package br.dev.ctrls.inovareti.domain.appointment.usecase;

import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.dev.ctrls.inovareti.core.exception.NotFoundException;
import br.dev.ctrls.inovareti.domain.appointment.AppointmentDoctorMapping;
import br.dev.ctrls.inovareti.domain.appointment.AppointmentDoctorMappingRepository;
import br.dev.ctrls.inovareti.domain.appointment.AppointmentMotorProperties;
import br.dev.ctrls.inovareti.domain.appointment.AppointmentSession;
import br.dev.ctrls.inovareti.domain.appointment.AppointmentSessionRepository;
import br.dev.ctrls.inovareti.domain.appointment.ConfirmationStateMachineService;
import br.dev.ctrls.inovareti.domain.appointment.FeegowClient;
import br.dev.ctrls.inovareti.domain.appointment.NoopWebhookIdempotencyService;
import br.dev.ctrls.inovareti.domain.appointment.WebhookIdempotencyService;
import br.dev.ctrls.inovareti.domain.appointment.service.BlipContextService;
import org.springframework.beans.factory.annotation.Value;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class HandleBlipWebhookUseCase {

    private final AppointmentSessionRepository appointmentSessionRepository;
    private final AppointmentMotorProperties appointmentMotorProperties;
    private final FeegowClient feegowClient;
    private final BlipContextService blipContextService;
    private final AppointmentDoctorMappingRepository appointmentDoctorMappingRepository;
    private final ConfirmationStateMachineService confirmationStateMachineService;
    private final Optional<WebhookIdempotencyService> webhookIdempotencyService;
    private final Optional<NoopWebhookIdempotencyService> noopWebhookIdempotencyService;
    private final ObjectMapper objectMapper;

    @Value("${APP_BLIP_STATE_LANDING_BLOCK_ID:2e3a6a6e-d18d-4d0d-b660-0d3dc7298262}")
    private String landingBlockId;

    @Value("${APP_BLIP_STATE_FLUXOV1_FLOW_ID:9271b2a2-9150-4391-8f55-e65b371007fb}")
    private String fluxov1FlowId;

    /**
     * @return nome da fila Blip resolvida após o processamento, ou {@code null} se o webhook foi ignorado
     *         (idempotência, ação não reconhecida, etc.)
     */
    @Transactional
    public WebhookResult execute(BlipWebhookPayload payload) {
        return execute(payload, false);
    }

    /**
     * @return nome da fila Blip resolvida após processar o médico (ex.: profissional {@code 70} no manual-trigger
     *         retorna {@code Endocrinologia} em fluxo {@code confirm}). No manual-trigger, os comandos LIME são
     *         disparados após o commit da transação, em thread separada, para não atrasar a resposta HTTP síncrona.
     */
    @Transactional
    public WebhookResult execute(BlipWebhookPayload payload, boolean skipTokenValidation) {
        if (!skipTokenValidation) {
            String expectedToken = appointmentMotorProperties.getSecurity().getWebhookToken();
            if (expectedToken != null && !expectedToken.isBlank() && !expectedToken.equals(payload.token())) {
                log.warn("Token de webhook inválido.");
                throw new SecurityException("Invalid token");
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
                java.util.List<AppointmentSession> activeSessions =
                    appointmentSessionRepository.findActiveByPhoneNumber(normalizedPhone);
                if (!activeSessions.isEmpty()) {
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
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                String cachedJson = webhookIdempotencyService.map(s -> s.getCachedResult(appointmentId)).orElse(null);
                if (cachedJson != null) {
                    try {
                        log.info("[IDEMPOTENCY] Resultado lido do cache para agendamento {}", appointmentId);
                        WebhookResult cachedResult = objectMapper.readValue(cachedJson, WebhookResult.class);
                        // Garante que o retorno utilize a ação do clique atual para não propagar estados obsoletos
                        WebhookResult overrideResult = new WebhookResult(
                            cachedResult.queue(),
                            cachedResult.patientName(),
                            cachedResult.patientCPF(),
                            cachedResult.patientBirthdate(),
                            actionType,
                            cachedResult.doctorName()
                        );
                        return overrideResult;
                    } catch (Exception e) {
                        log.error("Erro ao ler cache JSON para agendamento {}", appointmentId, e);
                    }
                }
            }
            log.warn("[IDEMPOTENCY] Tempo esgotado aguardando resultado para agendamento {}. Retornando null.", appointmentId);
            return null;
        }

        log.info("[WEBHOOK] Processando ação '{}' para agendamento ID={}", actionType, appointmentId);

        AppointmentSession session = appointmentSessionRepository.findByFeegowAppointmentId(appointmentId)
                .orElseThrow(() -> new NotFoundException("Sessão não encontrada para appointmentId=" + appointmentId));

        // Busca os dados reais diretamente no banco de dados (prioridade absoluta para evitar fallbacks)
        AppointmentDoctorMapping doctorMapping = appointmentDoctorMappingRepository
                .findByProfissionalId(session.getDoctorProfissionalId())
                .orElse(null);

        String doctorName = null;
        String queue = null;

        if (doctorMapping != null) {
            doctorName = doctorMapping.getProfissionalNome();
            queue = doctorMapping.getBlipQueueId();
        }

        // Se não achou o nome no banco, tenta buscar na Feegow
        if (doctorName == null || doctorName.isBlank()) {
            try {
                doctorName = feegowClient.getProfessionalName(session.getDoctorProfissionalId());
            } catch (Exception e) {
                log.warn("Não foi possível buscar o nome do médico na Feegow, usando fallback. erro={}", e.getMessage());
            }
        }

        // Fallback final para o nome do médico
        if (doctorName == null || doctorName.isBlank()) {
            doctorName = "Clínica Inovare";
        }
        doctorName = cleanDoctorName(doctorName);

        // Se a fila não foi encontrada no banco, usa o fallback da Recepção Central
        if (queue == null || queue.isBlank() || "null".equalsIgnoreCase(queue.trim()) || queue.contains("\u200E")) {
            queue = "Recepção Central / Suporte";
            log.warn("[QUEUE WARNING] Fila não encontrada no banco para o médico {}, usando fallback: {}", session.getDoctorProfissionalId(), queue);
        }

        queue = blipContextService.cleanQueueName(queue);
        if (queue.isBlank()) {
            queue = "Recepção Central / Suporte";
        }

        String dispatchIdentity = resolveDispatchIdentity(payload.from(), session);
        if (dispatchIdentity != null) {
            session.setPhoneNumber(dispatchIdentity);
        }

        // 1. Bloqueio de Duplicidade
        if ("CONFIRMED".equalsIgnoreCase(session.getStatus().name())) {
            log.info("[WEBHOOK] Agendamento {} já está confirmado no banco. Ignorando processamento duplicado para evitar múltiplas mensagens.", appointmentId);
            
            FeegowClient.FeegowPatient patient = feegowClient.patientInfo(session.getPatientId());
            String patientName = (patient.name() == null || patient.name().isBlank()) ? "Paciente" : patient.name();
            String formattedBirthdate = formatBirthdate(patient.birthdate());
            WebhookResult result = new WebhookResult(queue, patientName, patient.cpf(), formattedBirthdate, actionType, doctorName);
            log.info("[WEBHOOK] Agendamento {} já confirmado. Retornando WebhookResult populado: action={}, queue={}, patientName={}, doctorName={}", 
                     appointmentId, result.action(), result.queue(), result.patientName(), result.doctorName());
            try {
                String jsonResult = objectMapper.writeValueAsString(result);
                webhookIdempotencyService.ifPresent(service -> service.saveCachedResult(appointmentId, jsonResult));
                
                if (dispatchIdentity != null) {
                    java.util.Map<String, String> contextValues = java.util.Map.of(
                        "action", result.action() != null ? result.action() : "",
                        "doctorName", result.doctorName() != null ? result.doctorName() : "",
                        "queue", result.queue() != null ? result.queue() : "",
                        "patientName", result.patientName() != null ? result.patientName() : "",
                        "patientCPF", result.patientCPF() != null ? result.patientCPF() : "",
                        "patientBirthdate", result.patientBirthdate() != null ? result.patientBirthdate() : ""
                    );
                    blipContextService.atomicRedirect(dispatchIdentity, fluxov1FlowId, landingBlockId, contextValues);
                }
            } catch (Exception e) {
                log.error("Erro ao serializar resultado final para agendamento {}", appointmentId, e);
            }
            return result;
        }





        if ("confirm".equals(actionType)) {
            String confirmedStatusId = resolveConfirmedStatusId();
            log.info("[CONFIRM] Atualizando status na Feegow com código {}.", confirmedStatusId);
            try {
                feegowClient.updateAppointmentStatus(session.getFeegowAppointmentId(), confirmedStatusId);
            } catch (Exception ex) {
                log.error(
                    "[CONFIRM] Falha ao atualizar status na Feegow (continuando redirecionamento Blip). appointmentId={}, erro={}",
                    session.getFeegowAppointmentId(),
                    ex.getMessage(),
                    ex);
            }
            confirmationStateMachineService.markConfirmed(session);
            appointmentSessionRepository.save(session);
        } else {
            // alter_ — apenas salva sem mudar status na Feegow
            log.info("[ALTERAR] Paciente solicita alteração. Redirecionando para fila humana.");
        }

        // Removidos comandos LIME (teletransporte e mensagens) - o Builder cuidará do fluxo via resposta HTTP
        log.info("[WEBHOOK] Processamento concluído para {}. Fila: {}", actionType, queue);

        FeegowClient.FeegowPatient patient = feegowClient.patientInfo(session.getPatientId());
        String patientName = (patient.name() == null || patient.name().isBlank()) ? "Paciente" : patient.name();
        String formattedBirthdate = formatBirthdate(patient.birthdate());

        WebhookResult finalResult = new WebhookResult(queue, patientName, patient.cpf(), formattedBirthdate, actionType, doctorName);
        
        try {
            String jsonResult = objectMapper.writeValueAsString(finalResult);
            webhookIdempotencyService.ifPresent(service -> service.saveCachedResult(appointmentId, jsonResult));
            
            if (dispatchIdentity != null) {
                java.util.Map<String, String> contextValues = java.util.Map.of(
                    "action", finalResult.action() != null ? finalResult.action() : "",
                    "doctorName", finalResult.doctorName() != null ? finalResult.doctorName() : "",
                    "queue", finalResult.queue() != null ? finalResult.queue() : "",
                    "patientName", finalResult.patientName() != null ? finalResult.patientName() : "",
                    "patientCPF", finalResult.patientCPF() != null ? finalResult.patientCPF() : "",
                    "patientBirthdate", finalResult.patientBirthdate() != null ? finalResult.patientBirthdate() : ""
                );
                blipContextService.atomicRedirect(dispatchIdentity, fluxov1FlowId, landingBlockId, contextValues);
            }
        } catch (Exception e) {
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



    public record BlipWebhookPayload(String messageId, String appointmentId, String action, String from, String token, Object content) {
    }

    public record WebhookResult(String queue, String patientName, String patientCPF, String patientBirthdate, String action, String doctorName) {
    }
}
