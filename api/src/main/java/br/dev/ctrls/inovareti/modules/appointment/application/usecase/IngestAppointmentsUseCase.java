package br.dev.ctrls.inovareti.modules.appointment.application.usecase;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentCategory;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentDoctorMappingRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.AppointmentMotorProperties;
import br.dev.ctrls.inovareti.modules.appointment.application.service.AppointmentSendIdempotencyService;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSession;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentSessionRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSessionStatus;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.PatientExternalPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.ProfessionalExternalPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentExternalPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.FeegowAppointment;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.FeegowPatient;
import br.dev.ctrls.inovareti.modules.appointment.application.service.NoopAppointmentSendIdempotencyService;
import br.dev.ctrls.inovareti.modules.appointment.application.dto.AppointmentDispatchContext;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.utils.StringSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class IngestAppointmentsUseCase {

    private static final int FEEGOW_STATUS_AGENDADO = 1;

    private final AppointmentMotorProperties appointmentMotorProperties;
    private final PatientExternalPort patientExternalPort;
    private final ProfessionalExternalPort professionalExternalPort;
    private final AppointmentExternalPort appointmentExternalPort;
    private final ObjectMapper objectMapper;
    private final AppointmentDoctorMappingRepositoryPort appointmentDoctorMappingRepository;
    private final AppointmentSessionRepositoryPort appointmentSessionRepository;
    private final SendAppointmentTemplateUseCase sendAppointmentTemplateUseCase;
    private final Optional<AppointmentSendIdempotencyService> appointmentSendIdempotencyService;
    private final Optional<NoopAppointmentSendIdempotencyService> noopAppointmentSendIdempotencyService;
    private final jakarta.persistence.EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;

    // Registro auxiliar para carregar dados do banco de dados antes de chamadas HTTP externas,
    // garantindo isolamento transacional e liberação rápida de conexões do pool HikariCP.
    private record DbLookupResult(
        boolean hasMapped,
        String mappingQueue,
        String mappingProfessionalName,
        boolean ignoreAutoSchedule,
        Optional<AppointmentSession> existingSessionOpt,
        boolean canSend
    ) {}

    public IngestionSummary execute() {
        LocalDate targetDate = LocalDate.now().plusDays(1);
        log.info("Iniciando ingestão de agendamentos para a data: {}", targetDate);

        List<FeegowAppointment> appointments;
        
        // Chamada de rede externa Feegow (fora de transação para não segurar conexões do HikariCP)
        if (appointmentMotorProperties.isTestMode()) {
            String testDoctorId = appointmentMotorProperties.getTestDoctorId();
            log.info("[TEST MODE] Buscando agendamentos apenas para os médicos de teste ID: {}", testDoctorId);
            
            appointments = new ArrayList<>();
            if (testDoctorId != null && !testDoctorId.isBlank()) {
                String[] doctorIds = testDoctorId.split(",");
                for (String docId : doctorIds) {
                    String trimmedDocId = docId.trim();
                    if (!trimmedDocId.isEmpty()) {
                        appointments.addAll(appointmentExternalPort.searchAppointments(
                            LocalDate.now(),
                            FEEGOW_STATUS_AGENDADO,
                            trimmedDocId));
                        
                        appointments.addAll(appointmentExternalPort.searchAppointments(
                            targetDate,
                            FEEGOW_STATUS_AGENDADO,
                            trimmedDocId));
                    }
                }
            }
        } else {
            log.info("Consultando Feegow para ingestão de agendamentos com status Marcado (ID={})", FEEGOW_STATUS_AGENDADO);
            appointments = appointmentExternalPort.searchAppointments(
                targetDate,
                FEEGOW_STATUS_AGENDADO,
                null);
        }

        int totalReceived = appointments.size();
        int filteredReceived = appointments.size();

        int created = 0;
        int messagesSent = 0;
        for (FeegowAppointment appointment : appointments) {
            log.info("Processando agendamento Feegow ID: {} - Status: {}", appointment.id(), appointment.statusId());

            if ("12".equals(appointment.statusId())) {
                log.info("Agendamento ignorado pois é um ENCAIXE (status_id=12). appointmentId={}", appointment.id());
                filteredReceived--;
                continue;
            }

            String feegowAppointmentId = normalizeFeegowAppointmentId(appointment.id());
            if (feegowAppointmentId.isBlank()) {
                log.warn("ID Feegow vazio. Conteúdo recebido: {} | rawId={}",
                        serializeAppointmentForLog(appointment),
                        appointment.id());
                continue;
            }

            // FASE 1: Leitura do Banco de Dados em Transação Microscópica
            // O uso de TransactionTemplate aqui isola a leitura dos mapeamentos e sessões.
            // Isso evita manter transações abertas e conexões HikariCP presas durante a posterior chamada de rede externa.
            DbLookupResult dbData;
            try {
                dbData = transactionTemplate.execute(status -> {
                    var mappingOpt = appointmentDoctorMappingRepository.findByProfissionalIdLocked(appointment.doctorId());
                    if (mappingOpt.isEmpty()) {
                        return new DbLookupResult(false, null, null, false, Optional.empty(), false);
                    }
                    var mapping = mappingOpt.get();
                    if ("inactive".equalsIgnoreCase(mapping.getBlipQueueId())) {
                        return new DbLookupResult(false, null, null, false, Optional.empty(), false);
                    }
                    String mappingQueue = mapping.getBlipQueueId();
                    String mappingProfessionalNameLocal = mapping.getProfissionalNome();
                    boolean ignoreAutoSchedule = mapping.isIgnoreAutoSchedule();

                    Optional<AppointmentSession> existingSessionOpt = appointmentSessionRepository.findByFeegowAppointmentId(feegowAppointmentId);

                    boolean canSend = appointmentSendIdempotencyService
                            .map(service -> service.registerIfFirstSend(feegowAppointmentId))
                            .orElseGet(() -> noopAppointmentSendIdempotencyService
                                    .map(service -> service.registerIfFirstSend(feegowAppointmentId))
                                    .orElse(true));

                    return new DbLookupResult(true, mappingQueue, mappingProfessionalNameLocal, ignoreAutoSchedule, existingSessionOpt, canSend);
                });
            } catch (RuntimeException ex) {
                log.error("Erro na fase de leitura transacional para o agendamento Feegow ID: {}. Detalhes: {}", appointment.id(), ex.getMessage(), ex);
                continue;
            }

            if (dbData == null || !dbData.hasMapped()) {
                log.info("Agendamento ignorado por ausência de mapeamento do médico. appointmentId={}, profissional_id={}",
                        appointment.id(),
                        appointment.doctorId());
                continue;
            }

            if (dbData.ignoreAutoSchedule()) {
                log.info("Agendamento ignorado por exceção de médico (ignore_auto_schedule=true). appointmentId={}, profissional_id={}",
                        appointment.id(),
                        appointment.doctorId());
                continue;
            }

            // Regra de Guarda: Evitar spam se a consulta já estiver confirmada localmente ou na Feegow
            boolean isConfirmedLocally = dbData.existingSessionOpt()
                    .map(s -> s.getStatus() == AppointmentSessionStatus.CONFIRMED)
                    .orElse(false);

            String confirmedStatusId = appointmentMotorProperties.getFeegowConfirmedStatusId();
            if (confirmedStatusId == null || confirmedStatusId.isBlank()) {
                confirmedStatusId = "7";
            }
            boolean isConfirmedOnFeegow = confirmedStatusId.trim().equalsIgnoreCase(appointment.statusId());

            if (isConfirmedLocally || isConfirmedOnFeegow) {
                log.info("[MENSAGERIA] Ignorando disparo de template: consulta já confirmada (Local: {}, Feegow: {}). appointmentId={}",
                        isConfirmedLocally, isConfirmedOnFeegow, feegowAppointmentId);
                continue;
            }

            if (dbData.existingSessionOpt().isPresent()) {
                AppointmentSessionStatus status = dbData.existingSessionOpt().get().getStatus();
                if (status == AppointmentSessionStatus.PENDING ||
                    status == AppointmentSessionStatus.NUDGE_1_SENT ||
                    status == AppointmentSessionStatus.NUDGE_FINAL_SENT ||
                    status == AppointmentSessionStatus.CONFIRMED) {
                    log.info("Agendamento ignorado: já existe uma AppointmentSession activa (status {}) para feegowAppointmentId={}", status, feegowAppointmentId);
                    continue;
                }
            }

            if (!dbData.canSend()) {
                log.info("Envio ignorado por idempotência Redis/Local. appointmentId={}", feegowAppointmentId);
                continue;
            }

            // FASE 2: Chamadas de Rede Externas (Fora de Transação)
            // As requisições HTTP para obter detalhes do paciente e do profissional ocorrem fora de qualquer
            // transação com o banco de dados. Isso impede o esgotamento do pool de conexões HikariCP.
            String patientId = appointment.patientId();
            FeegowPatient patientDetails;
            final String appointmentDoctorId = appointment.doctorId() != null ? appointment.doctorId().trim() : null;

            log.info("[MAPEAMENTO MÉDICO] profissional_id={} | fila_mapeada={}",
                appointmentDoctorId, dbData.mappingQueue());

            String resolvedProfessionalName;
            if (dbData.mappingProfessionalName() != null
                && !dbData.mappingProfessionalName().isBlank()
                && !"null".equalsIgnoreCase(dbData.mappingProfessionalName().trim())) {
                resolvedProfessionalName = dbData.mappingProfessionalName().trim();
            } else {
                resolvedProfessionalName = "Clínica Inovare";
            }

            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                var patientFuture = CompletableFuture.supplyAsync(() ->
                    patientExternalPort.patientInfo(patientId), executor);

                final boolean needFeegowName = "Clínica Inovare".equals(resolvedProfessionalName);
                final String currentResolvedName = resolvedProfessionalName;

                var professionalFuture = CompletableFuture.supplyAsync(() -> {
                    if (!needFeegowName) return currentResolvedName;
                    try {
                        String feegowName = professionalExternalPort.getProfessionalName(appointmentDoctorId);
                        if (feegowName != null && !feegowName.isBlank() && !"null".equalsIgnoreCase(feegowName.trim())) {
                            return feegowName.trim();
                        }
                    } catch (Exception e) {
                        log.warn("Falha ao recuperar nome do profissional via Feegow para id {}: {}", appointmentDoctorId, e.getMessage());
                    }
                    return currentResolvedName;
                }, executor);

                try {
                    patientDetails = patientFuture.join();
                } catch (Exception ex) {
                    RestClientResponseException rcre = null;
                    if (ex instanceof RestClientResponseException e) {
                        rcre = e;
                    } else if (ex instanceof java.util.concurrent.CompletionException && ex.getCause() instanceof RestClientResponseException e) {
                        rcre = e;
                    }

                    if (rcre != null) {
                        int statusCode = rcre.getStatusCode().value();
                        if (statusCode == 404 || statusCode == 500) {
                            log.error("Falha ao recuperar contato do paciente ID {}. Pulando agendamento.", patientId, rcre);
                        } else {
                            log.error("Erro ao recuperar contato do paciente ID {}. statusCode={}. Pulando agendamento.", patientId,
                                    statusCode,
                                    rcre);
                        }
                    } else {
                        log.error("Falha ao recuperar contato do paciente ID {}. Pulando agendamento.", patientId, ex);
                    }
                    continue;
                }

                try {
                    String fetchedName = professionalFuture.join();
                    if (fetchedName != null && !fetchedName.isBlank()) {
                        resolvedProfessionalName = fetchedName;
                    }
                } catch (Exception ignored) {
                }
            }

            String patientPhone = patientDetails != null ? patientDetails.phone() : null;

            String phoneNumber = normalizePhoneNumberForBlip(patientPhone);
            if (patientPhone == null || patientPhone.isBlank() || phoneNumber.isBlank()) {
                log.error("Falha ao recuperar contato do paciente ID {}. Pulando agendamento.", patientId);
                continue;
            }

            if (appointmentMotorProperties.isTestMode()) {
                // Filtra os IDs dos médicos homologados para o ambiente de teste
                String testDoctorId = appointmentMotorProperties.getTestDoctorId();
                java.util.List<String> allowedIds = java.util.Arrays.stream(testDoctorId.split(","))
                        .map(String::trim)
                        .filter(id -> !id.isEmpty())
                        .toList();
                
                String doctorId = appointment.doctorId() != null ? appointment.doctorId().trim() : "";
                if (!allowedIds.contains(doctorId)) {
                    log.info("[TRAVA DE TESTE] Agendamento pulado. Médico ID {} não está na lista de homologação ({}).", doctorId, testDoctorId);
                    continue;
                }

                // Resolução de múltiplos telefones de teste permitidos (separados por vírgula)
                String testPhone = appointmentMotorProperties.getTestPhone();
                if (testPhone != null && !testPhone.isBlank()) {
                    java.util.List<String> allowedPhones = java.util.Arrays.stream(testPhone.split(","))
                            .map(String::trim)
                            .filter(p -> !p.isEmpty())
                            .map(this::normalizePhoneNumberForBlip)
                            .filter(p -> !p.isEmpty())
                            .toList();

                    if (allowedPhones.isEmpty()) {
                        log.warn("[TEST MODE] Envio bloqueado para o paciente real ({}): nenhum telefone de teste válido em (APP_APPOINTMENT_MOTOR_TEST_PHONE) configurado no ambiente.", patientPhone);
                        continue;
                    }

                    // Se o telefone do paciente real (já normalizado) for um dos homologados, mantém o envio direto
                    if (allowedPhones.contains(phoneNumber)) {
                        log.info("[TEST MODE] Telefone do paciente real ({}) é um número de homologação permitido. Mantendo o número original.", patientPhone);
                    } else {
                        // Caso contrário, redireciona o envio para o primeiro número de teste cadastrado (número primário)
                        phoneNumber = allowedPhones.get(0);
                        log.info("[TEST MODE] Telefone do paciente real ({}) redirecionado para o número de teste homologado primário: {}", patientPhone, phoneNumber);
                    }
                } else {
                    log.warn("[TEST MODE] Envio bloqueado para o paciente real ({}): nenhum telefone de teste (APP_APPOINTMENT_MOTOR_TEST_PHONE) configurado no ambiente.", patientPhone);
                    continue;
                }
            }

            log.info("Dados do paciente recuperados: ID={}, Telefone={}, CampoUtilizado={}",
                patientId,
                patientPhone,
                "preferred_phone");

            log.info("Agendamento Ingerido: Paciente={}, Telefone Feegow={}, Telefone Normalizado para Blip={}",
                patientDetails != null ? patientDetails.name() : null,
                patientPhone,
                phoneNumber);

            // FASE 3: Persistência no Banco de Dados em Transação Microscópica
            // O escopo transacional é restrito estritamente à gravação da sessão e flush, garantindo liberação imediata da conexão.
            AppointmentSession saved;
            try {
                final String finalPhoneNumber = phoneNumber;
                saved = transactionTemplate.execute(status -> {
                    Optional<AppointmentSession> latestSessionOpt = appointmentSessionRepository.findByFeegowAppointmentId(feegowAppointmentId);
                    AppointmentSession session = latestSessionOpt.orElseGet(AppointmentSession::new);

                    session.setFeegowAppointmentId(feegowAppointmentId);
                    session.setPatientId(appointment.patientId());
                    session.setPhoneNumber(finalPhoneNumber);
                    session.setDoctorProfissionalId(appointment.doctorId());
                    session.setAppointmentAt(appointment.startAt());
                    session.setStatus(AppointmentSessionStatus.PENDING);
                    session.setLastInteractionAt(LocalDateTime.now());
                    session.setClosedAt(null);
                    session.setStatusDetails(null);

                    AppointmentSession s = appointmentSessionRepository.save(session);
                    entityManager.flush();
                    return s;
                });
            } catch (RuntimeException ex) {
                log.error("Falha grave na persistência da sessão de agendamento no banco de dados para feegowAppointmentId={}. Detalhes: {}", feegowAppointmentId, ex.getMessage(), ex);
                continue;
            }

            log.info("Agendamento salvo localmente: ID Feegow = {}", saved.getFeegowAppointmentId());

            String safeProfissionalNome = (resolvedProfessionalName != null
                && !resolvedProfessionalName.isBlank()
                && !"null".equalsIgnoreCase(resolvedProfessionalName.trim()))
                ? resolvedProfessionalName.trim()
                : "Clínica Inovare";

            String safeFilaDestino = (dbData.mappingQueue() != null
                && !dbData.mappingQueue().isBlank()
                && !"null".equalsIgnoreCase(dbData.mappingQueue().trim()))
                ? dbData.mappingQueue().trim()
                : StringSanitizer.UNICODE_LTR_MARK;

            log.info("[EXTRAS CONTATO] profissional_nome='{}' | fila_destino='{}'", safeProfissionalNome, safeFilaDestino);

            final String finalPatientName = (patientDetails != null && patientDetails.name() != null)
                ? patientDetails.name().trim() : "Paciente";
            final String finalPatientPhone = (patientDetails != null)
                ? patientDetails.phone() : null;
            final String finalDate = saved.getAppointmentAt() != null
                ? saved.getAppointmentAt().toLocalDate().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "";
            final String finalDateShort = saved.getAppointmentAt() != null
                ? saved.getAppointmentAt().toLocalDate().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM")) : "";
            final String finalTime = saved.getAppointmentAt() != null
                ? saved.getAppointmentAt().toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) : "";

            AppointmentDispatchContext ctx = new AppointmentDispatchContext(
                saved.getId(),
                feegowAppointmentId,
                finalPatientName,
                finalPatientPhone,
                saved.getPatientId(),
                appointmentDoctorId,
                resolvedProfessionalName,
                safeFilaDestino,
                finalDate,
                finalDateShort,
                finalTime,
                phoneNumber
            );

            // Chamada de rede externa Blip para envio do template (também fora de transação do IngestAppointmentsUseCase)
            boolean templateSent = sendAppointmentTemplateUseCase.execute(ctx, AppointmentCategory.CONFIRMATION);
            created++;
            if (templateSent) {
                messagesSent++;
            }
        }

        String mode = appointmentMotorProperties.isTestMode() ? "TEST" : "PROD";
        log.info("Ingestão de consultas executada. totalRecebido={}, totalAposFiltro={}, sessoesCriadas={}, mensagensEnviadas={}, modo={}",
                totalReceived,
                filteredReceived,
                created,
                messagesSent,
                mode);

        return new IngestionSummary(totalReceived, filteredReceived, created, messagesSent, mode);
    }

    public record IngestionSummary(int totalReceived, int filteredReceived, int sessionsCreated, int messagesSent, String mode) {
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

    private String normalizePhoneNumberForBlip(String originalPhone) {
        if (originalPhone == null || originalPhone.isBlank()) {
            return "";
        }

        String trimmed = originalPhone.trim();

        // Alguns cadastros retornam múltiplos contatos no mesmo campo. Mantém apenas o primeiro.
        if (trimmed.contains(",") || trimmed.contains("/") || trimmed.contains(" ")) {
            String[] parts = trimmed.split("[,/\\s]+");
            if (parts.length == 0 || parts[0] == null || parts[0].isBlank()) {
                return "";
            }

            trimmed = parts[0].trim();
        }

        String digitsOnly = trimmed.replaceAll("\\D", "");
        if (digitsOnly.isBlank()) {
            return "";
        }

        if (digitsOnly.startsWith("55")) {
            return "+" + digitsOnly;
        }

        return "+55" + digitsOnly;
    }

    private String serializeAppointmentForLog(FeegowAppointment appointment) {
        if (appointment == null) {
            return "{}";
        }

        try {
            return objectMapper.writeValueAsString(appointment);
        } catch (JsonProcessingException ex) {
            return String.valueOf(appointment);
        }
    }
}
