package br.dev.ctrls.inovareti.modules.appointment.application.usecase;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.LockSupport;

import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.dev.ctrls.inovareti.core.exception.NotFoundException;
import br.dev.ctrls.inovareti.core.shared.domain.port.output.AuditPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentDoctorMapping;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentDoctorMappingRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.AppointmentMotorProperties;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSession;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentSessionRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.application.service.ConfirmationStateMachineService;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.PatientExternalPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.ProfessionalExternalPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentExternalPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.FeegowPatient;
import br.dev.ctrls.inovareti.modules.appointment.application.service.NoopWebhookIdempotencyService;
import br.dev.ctrls.inovareti.modules.appointment.application.service.WebhookIdempotencyService;
import br.dev.ctrls.inovareti.modules.appointment.application.service.BlipContextService;
import br.dev.ctrls.inovareti.modules.appointment.application.dto.AppointmentPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class HandleBlipWebhookUseCase {

    private final AppointmentSessionRepositoryPort appointmentSessionRepository;
    private final AppointmentMotorProperties appointmentMotorProperties;
    private final PatientExternalPort patientExternalPort;
    private final ProfessionalExternalPort professionalExternalPort;
    private final AppointmentExternalPort appointmentExternalPort;
    private final BlipContextService blipContextService;
    private final AppointmentDoctorMappingRepositoryPort appointmentDoctorMappingRepository;
    private final ConfirmationStateMachineService confirmationStateMachineService;
    private final AuditPort auditPort;
    private final Optional<WebhookIdempotencyService> webhookIdempotencyService;
    private final Optional<NoopWebhookIdempotencyService> noopWebhookIdempotencyService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    // Registro auxiliar para carregar dados da sessão e mapeamento do médico de forma rápida,
    // garantindo liberação imediata da conexão com o banco antes do I/O de rede com Feegow/Blip.
    private record SessionDbData(
        AppointmentSession session,
        AppointmentDoctorMapping doctorMapping
    ) {}


    /**
     * @return nome da fila Blip resolvida após o processamento, ou {@code null} se o webhook foi ignorado
     *         (idempotência, ação não reconhecida, etc.)
     */
    public WebhookResult execute(BlipWebhookPayload payload) {
        return execute(payload, false);
    }

    /**
     * @return nome da fila Blip resolvida após processar o médico (ex.: profissional {@code 70} no manual-trigger
     *         retorna {@code Endocrinologia} em fluxo {@code confirm}). No manual-trigger, os comandos LIME são
     *         disparados após o commit da transação, em thread separada, para não atrasar a resposta HTTP síncrona.
     */
    public WebhookResult execute(BlipWebhookPayload payload, boolean skipTokenValidation) {
        if (!skipTokenValidation) {
            String expectedToken = appointmentMotorProperties.getSecurity().getWebhookToken();
            if (expectedToken != null && !expectedToken.isBlank() && !expectedToken.equals(payload.token())) {
                log.warn("Token de webhook inválido.");
                throw new SecurityException("Invalid token");
            }
            if (expectedToken != null && !expectedToken.isBlank()) {
                auditPort.record(
                        "APPOINTMENT_MOTOR",
                        "ASSINATURA_VALIDADA",
                        "Assinatura do webhook validada. messageId=" + payload.messageId(),
                        resolveTraceId());
            }
        }

        boolean fresh = webhookIdempotencyService
                .map(service -> service.registerIfFirstTime(payload.messageId()))
                .orElseGet(() -> noopWebhookIdempotencyService.map(service -> service.registerIfFirstTime(payload.messageId())).orElse(true));

        if (!fresh) {
            log.debug("Ação ignorada no webhook (idempotência). messageId={}", payload.messageId());
            return null;
        }

        String action = payload.action() != null ? payload.action().trim() : "";
        if (action.isBlank()) {
            String fallbackAction = resolveActionFromContent(payload.content());
            if (fallbackAction != null && !fallbackAction.isBlank()) {
                action = fallbackAction.trim();
            }
        }

        // Intercepção de texto livre: "Solicitar Alteração" sem payload de botão
        // Resolve o ID da sessão ativa mais recente para o telefone do usuário
        if (action.equalsIgnoreCase("Solicitar Alteração")
                || action.equalsIgnoreCase("Solicitar Alteracao")
                || action.equalsIgnoreCase("alterar")
                || action.toLowerCase().contains("solicitar alter")) {
            String fromPhone = payload.from();
            if (fromPhone != null && !fromPhone.isBlank()) {
                String normalizedPhone = fromPhone.trim();
                // FASE 1A: Leitura rápida no banco (micro-transação)
                java.util.List<AppointmentSession> activeSessions = transactionTemplate.execute(status -> 
                    appointmentSessionRepository.findActiveByPhoneNumber(normalizedPhone)
                );
                
                if (activeSessions != null && !activeSessions.isEmpty()) {
                    String resolvedId = activeSessions.get(0).getFeegowAppointmentId();
                    log.info("[WEBHOOK] Texto livre '{}' interceptado. Mapeando para alter_{} (sessão mais recente de {})",
                        action, resolvedId, normalizedPhone);
                    action = "alter_" + resolvedId;
                } else {
                    log.warn("[WEBHOOK] Texto livre '{}' recebido mas nenhuma sessão ativa encontrada para {}", action, normalizedPhone);
                    return null;
                }
            } else {
                log.warn("[WEBHOOK] Texto livre '{}' recebido sem 'from' identificável.", action);
                return null;
            }
        }

        // Captura confirm_{ID} ou alter_{ID}
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(?i)(confirm|alter)_(\\d+(?:\\.\\d+)?)");
        java.util.regex.Matcher matcher = pattern.matcher(action);

        if (!matcher.find()) {
            log.debug("[WEBHOOK] Ação ignorada (não é confirm_ nem alter_). action='{}'", action);
            return null;
        }

        String actionType   = matcher.group(1).toLowerCase(); // "confirm" ou "alter"
        String rawId        = matcher.group(2);
        String appointmentId = normalizeFeegowAppointmentId(rawId);

        if (appointmentId == null || appointmentId.isBlank()) {
            log.warn("[WEBHOOK] Payload {}_  recebido sem ID válido. action={}", actionType, action);
            return null;
        }

        boolean lockAcquired = webhookIdempotencyService
                .map(service -> service.tryAcquireLock(appointmentId))
                .orElseGet(() -> noopWebhookIdempotencyService.map(service -> service.tryAcquireLock(appointmentId)).orElse(true));

        if (!lockAcquired) {
            log.info("[IDEMPOTENCY] Aguardando processamento da thread principal para o agendamento {}", appointmentId);
            // Spin-Wait
            int maxAttempts = 10;
            for (int i = 0; i < maxAttempts; i++) {
                awaitIdempotencyDelayNonBlocking(500L); // Usando delay não bloqueante para Virtual Threads

                String cachedJson = webhookIdempotencyService.map(s -> s.getCachedResult(appointmentId)).orElse(null);
                if (cachedJson != null) {
                    try {
                        log.info("[IDEMPOTENCY] Resultado lido do cache para agendamento {}", appointmentId);
                        WebhookResult cachedResult = objectMapper.readValue(cachedJson, WebhookResult.class);
                        WebhookResult overrideResult = new WebhookResult(
                            cachedResult.queue(),
                            cachedResult.patientName(),
                            cachedResult.patientCPF(),
                            cachedResult.patientBirthdate(),
                            actionType,
                            cachedResult.doctorName()
                        );
                        return overrideResult;
                    } catch (JsonProcessingException | IllegalArgumentException e) {
                        log.error("Erro ao ler cache JSON para agendamento {}", appointmentId, e);
                    }
                }
            }
            log.warn("[IDEMPOTENCY] Tempo esgotado aguardando resultado para agendamento {}. Retornando null.", appointmentId);
            return null;
        }

        log.info("[WEBHOOK] Processando ação '{}' para agendamento ID={}", actionType, appointmentId);

        // FASE 1B: Leitura transacional em micro-transação.
        // Busca a sessão e o mapeamento do médico no banco e libera a conexão HikariCP imediatamente.
        SessionDbData dbData;
        try {
            dbData = transactionTemplate.execute(status -> {
                AppointmentSession session = appointmentSessionRepository.findByFeegowAppointmentId(appointmentId)
                        .orElseThrow(() -> new NotFoundException("Sessão não encontrada para appointmentId=" + appointmentId));
                AppointmentDoctorMapping doctorMapping = appointmentDoctorMappingRepository
                        .findByProfissionalId(session.getDoctorProfissionalId())
                        .orElse(null);
                return new SessionDbData(session, doctorMapping);
            });
        } catch (NotFoundException | DataAccessException | IllegalStateException ex) {
            log.error("Erro na leitura transacional inicial do webhook para appointmentId={}. Detalhes: {}", appointmentId, ex.getMessage(), ex);
            return null;
        }

        if (dbData == null) {
            return null;
        }

        String doctorName = null;
        String queue = null;

        if (dbData.doctorMapping() != null) {
            doctorName = dbData.doctorMapping().getProfissionalNome();
            queue = dbData.doctorMapping().getBlipQueueId();
        }

        // FASE 2: Chamada HTTP Externa à Feegow (Fora de Transação)
        if (doctorName == null || doctorName.isBlank()) {
            try {
                doctorName = professionalExternalPort.getProfessionalName(dbData.session().getDoctorProfissionalId());
            } catch (RestClientException | IllegalStateException e) {
                log.warn("Não foi possível buscar o nome do médico na Feegow, usando fallback. erro={}", e.getMessage());
                auditPort.record(
                        "APPOINTMENT_MOTOR",
                        "CIRCUIT_BREAKER_FALLBACK",
                        "Fallback ao buscar nome do profissional na Feegow. appointmentId=" + appointmentId
                                + ", erro=" + safeMessage(e),
                        resolveTraceId());
            }
        }

        if (doctorName == null || doctorName.isBlank()) {
            doctorName = "Clínica Inovare";
        }
        doctorName = cleanDoctorName(doctorName);

        if (queue == null || queue.isBlank() || "null".equalsIgnoreCase(queue.trim()) || queue.contains("\u200E")) {
            queue = "Recepção Central / Suporte";
            log.warn("[QUEUE WARNING] Fila não encontrada no banco para o médico {}, usando fallback: {}", dbData.session().getDoctorProfissionalId(), queue);
        }

        queue = blipContextService.cleanQueueName(queue);
        if (queue.isBlank()) {
            queue = "Recepção Central / Suporte";
        }

        String dispatchIdentity = resolveDispatchIdentity(payload.from(), dbData.session());

        // 1. Bloqueio de Duplicidade
        if ("CONFIRMED".equalsIgnoreCase(dbData.session().getStatus().name())) {
            log.info("[WEBHOOK] Agendamento {} já está confirmado no banco. Ignorando processamento duplicado para evitar múltiplas mensagens.", appointmentId);
            
            // FASE 2B: Chamada HTTP Externa (Fora de Transação)
            FeegowPatient patient = patientExternalPort.patientInfo(dbData.session().getPatientId());
            String patientName = (patient.name() == null || patient.name().isBlank()) ? "Paciente" : patient.name();
            String formattedBirthdate = formatBirthdate(patient.birthdate());
            WebhookResult result = new WebhookResult(queue, patientName, patient.cpf(), formattedBirthdate, actionType, doctorName);
            log.info("[WEBHOOK] Agendamento {} já confirmado. Retornando WebhookResult populado: action={}, queue={}, patientName={}, doctorName={}", 
                     appointmentId, result.action(), result.queue(), result.patientName(), result.doctorName());
            try {
                String jsonResult = objectMapper.writeValueAsString(result);
                webhookIdempotencyService.ifPresent(service -> service.saveCachedResult(appointmentId, jsonResult));
                
                if (dispatchIdentity != null) {
                    // Atualiza telefone no banco caso tenha vindo diferente (Transação microscópica de gravação)
                    try {
                        transactionTemplate.executeWithoutResult(status -> {
                            AppointmentSession currentSession = appointmentSessionRepository.findByFeegowAppointmentId(appointmentId).orElse(null);
                            if (currentSession != null) {
                                currentSession.setPhoneNumber(dispatchIdentity);
                                appointmentSessionRepository.save(currentSession);
                            }
                        });
                    } catch (DataAccessException | IllegalStateException ex) {
                        log.error("Falha ao atualizar telefone na sessão já confirmada. appointmentId={}", appointmentId, ex);
                    }

                    AppointmentPayload appointmentPayload = AppointmentPayload.builder()
                        .action(result.action() != null ? result.action() : "")
                        .doctorName(result.doctorName() != null ? result.doctorName() : "")
                        .queue(result.queue() != null ? result.queue() : "")
                        .patientName(result.patientName() != null ? result.patientName() : "")
                        .patientCPF(result.patientCPF() != null ? result.patientCPF() : "")
                        .patientBirthdate(result.patientBirthdate() != null ? result.patientBirthdate() : "")
                        .build();
                    blipContextService.processAppointmentPush(dispatchIdentity, result.action(), appointmentPayload);
                }
            } catch (JsonProcessingException | IllegalArgumentException e) {
                log.error("Erro ao serializar resultado final para agendamento {}", appointmentId, e);
            }
            return result;
        }

        // FASE 2C: Chamada de rede externa Feegow se for CONFIRM (Fora de Transação)
        if ("confirm".equals(actionType)) {
            String confirmedStatusId = resolveConfirmedStatusId();
            log.info("[CONFIRM] Atualizando status na Feegow com código {}.", confirmedStatusId);
            try {
                appointmentExternalPort.updateAppointmentStatus(dbData.session().getFeegowAppointmentId(), confirmedStatusId);
            } catch (RestClientException | IllegalStateException ex) {
                log.error(
                    "[CONFIRM] Falha ao atualizar status na Feegow (continuando redirecionamento Blip). appointmentId={}, erro={}",
                    dbData.session().getFeegowAppointmentId(),
                    ex.getMessage(),
                    ex);
            }
        } else {
            log.info("[ALTERAR] Paciente solicita alteração. Redirecionando para fila humana.");
        }

        // FASE 3: Persistência no Banco de Dados em Transação Microscópica
        // O escopo transacional é restrito e isolado da chamada HTTP externa.
        try {
            transactionTemplate.executeWithoutResult(status -> {
                AppointmentSession currentSession = appointmentSessionRepository.findByFeegowAppointmentId(appointmentId)
                        .orElseThrow(() -> new NotFoundException("Sessão não encontrada para appointmentId=" + appointmentId));
                
                if (dispatchIdentity != null) {
                    currentSession.setPhoneNumber(dispatchIdentity);
                }
                
                if ("confirm".equals(actionType)) {
                    confirmationStateMachineService.markConfirmed(currentSession);
                }
                
                appointmentSessionRepository.save(currentSession);
            });
        } catch (DataAccessException | IllegalStateException ex) {
            log.error("Falha grave na gravação dos dados do webhook no banco de dados para appointmentId={}. Detalhes: {}", appointmentId, ex.getMessage(), ex);
            throw ex;
        }

        log.info("[WEBHOOK] Processamento concluído para {}. Fila: {}", actionType, queue);

        // FASE 2D: Chamada HTTP Externa à Feegow para retornar dados do paciente (Fora de Transação)
        FeegowPatient patient = patientExternalPort.patientInfo(dbData.session().getPatientId());
        String patientName = (patient.name() == null || patient.name().isBlank()) ? "Paciente" : patient.name();
        String formattedBirthdate = formatBirthdate(patient.birthdate());

        WebhookResult finalResult = new WebhookResult(queue, patientName, patient.cpf(), formattedBirthdate, actionType, doctorName);
        
        try {
            String jsonResult = objectMapper.writeValueAsString(finalResult);
            webhookIdempotencyService.ifPresent(service -> service.saveCachedResult(appointmentId, jsonResult));
            
            if (dispatchIdentity != null) {
                AppointmentPayload appointmentPayload = AppointmentPayload.builder()
                    .action(finalResult.action() != null ? finalResult.action() : "")
                    .doctorName(finalResult.doctorName() != null ? finalResult.doctorName() : "")
                    .queue(finalResult.queue() != null ? finalResult.queue() : "")
                    .patientName(finalResult.patientName() != null ? finalResult.patientName() : "")
                    .patientCPF(finalResult.patientCPF() != null ? finalResult.patientCPF() : "")
                    .patientBirthdate(finalResult.patientBirthdate() != null ? finalResult.patientBirthdate() : "")
                    .build();
                // Chamada externa Blip (fora de transação)
                blipContextService.processAppointmentPush(dispatchIdentity, finalResult.action(), appointmentPayload);
            }
        } catch (JsonProcessingException | IllegalArgumentException e) {
            log.error("Erro ao serializar resultado final para agendamento {}", appointmentId, e);
        }

        return finalResult;
    }



    private String resolveConfirmedStatusId() {
        String configuredStatusId = appointmentMotorProperties.getFeegowConfirmedStatusId();
        if (configuredStatusId == null || configuredStatusId.isBlank()) {
            return "7";
        }
        return configuredStatusId.trim();
    }



    private String normalizeFeegowAppointmentId(String feegowAppointmentId) {
        if (feegowAppointmentId == null) {
            return "";
        }
        String normalized = feegowAppointmentId.trim();
        if (normalized.matches("^\\d+\\.0+$")) {
            return normalized.substring(0, normalized.indexOf('.'));
        }
        return normalized;
    }

    private String resolveDispatchIdentity(String from, AppointmentSession session) {
        String direct = normalizeDispatchIdentity(from);
        if (direct != null) {
            return direct;
        }
        String sessionPhone = session != null ? session.getPhoneNumber() : null;
        String fallback = normalizeDispatchIdentity(sessionPhone);
        if (fallback == null) {
            log.warn("Identidade de disparo inválida. from={}, sessionPhone={}", from, sessionPhone);
        }
        return fallback;
    }

    private String normalizeDispatchIdentity(String identity) {
        if (identity == null || identity.isBlank()) {
            return null;
        }
        return identity.trim();
    }

    private String resolveActionFromContent(Object content) {
        if (content == null) {
            return null;
        }
        if (content instanceof String text) {
            return text;
        }
        Map<String, Object> contentMap = toMap(content);
        if (contentMap == null) {
            return null;
        }
        String replyValue = null;
        Object replied = contentMap.get("replied");
        if (replied instanceof Map<?, ?> repliedMap) {
            replyValue = asText(repliedMap.get("value"));
        }
        return firstNonBlank(
            replyValue,
            asText(contentMap.get("text")),
            asText(contentMap.get("value")),
            asText(contentMap.get("payload")),
            asText(contentMap.get("id"))
        );
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank() && !"null".equalsIgnoreCase(value)) {
                return value;
            }
        }
        return null;
    }

    private String asText(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            return text;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        return null;
    }

    private Map<String, Object> toMap(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> cast = (Map<String, Object>) map;
            return cast;
        }
        try {
            return objectMapper.convertValue(value, new TypeReference<Map<String, Object>>() { });
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }



    private String formatBirthdate(String birthdate) {
        if (birthdate == null || birthdate.isBlank()) {
            return "";
        }
        String clean = birthdate.trim();
        // Se já estiver no formato DD/MM/AAAA, retorna
        if (clean.matches("\\d{2}/\\d{2}/\\d{4}")) {
            return clean;
        }
        // Tenta converter de AAAA-MM-DD ou DD-MM-AAAA para DD/MM/AAAA
        try {
            java.time.LocalDate date;
            if (clean.contains("-")) {
                if (clean.indexOf('-') == 4) { // AAAA-MM-DD
                    date = java.time.LocalDate.parse(clean);
                } else { // DD-MM-AAAA
                    date = java.time.LocalDate.parse(clean, java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"));
                }
                return date.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            }
        } catch (Exception e) {
            log.warn("Falha ao formatar data de nascimento: {}", birthdate);
        }
        return clean;
    }

    private String cleanDoctorName(String doctorName) {
        if (doctorName == null || doctorName.isBlank()) {
            return "Clínica Inovare";
        }
        String clean = doctorName.trim();
        // Remove "Dr. ", "Dra. ", "Dr ", "Dra " case-insensitively from the start of the string
        clean = clean.replaceAll("(?i)^(Dr\\.|Dra\\.|Dr|Dra)\\s+", "");
        return clean.trim();
    }

    // Substitui Thread.sleep por um mecanismo não bloqueante para Virtual Threads.
    private void awaitIdempotencyDelayNonBlocking(long delayMs) {
        LockSupport.parkNanos(java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(delayMs));
    }

    private String resolveTraceId() {
        String traceId = MDC.get("traceId");
        if (traceId == null || traceId.isBlank()) {
            traceId = MDC.get("trace_id");
        }
        return traceId;
    }

    private String safeMessage(Exception ex) {
        if (ex == null || ex.getMessage() == null) {
            return "-";
        }
        return ex.getMessage();
    }



    public record BlipWebhookPayload(String messageId, String appointmentId, String action, String from, String token, Object content) {
    }

    public record WebhookResult(String queue, String patientName, String patientCPF, String patientBirthdate, String action, String doctorName) {
    }
}
