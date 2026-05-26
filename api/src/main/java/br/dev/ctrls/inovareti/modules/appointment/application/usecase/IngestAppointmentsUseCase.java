package br.dev.ctrls.inovareti.modules.appointment.application.usecase;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.UUID;
import java.util.Map;
import java.util.stream.Collectors;

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
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.NotificationGroupRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.application.service.BlipNotificationService;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.NotificationGroup;
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
    private final NotificationGroupRepositoryPort notificationGroupRepository;
    private final BlipNotificationService blipNotificationService;

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
        
        // Agrupar agendamentos elegíveis por ID do Paciente
        Map<String, List<FeegowAppointment>> grouped = appointments.stream()
                .filter(appointment -> !"12".equals(appointment.statusId()))
                .collect(Collectors.groupingBy(FeegowAppointment::patientId));

        int created = 0;
        int messagesSent = 0;
        int filteredReceived = 0;

        for (Map.Entry<String, List<FeegowAppointment>> entry : grouped.entrySet()) {
            String patientId = entry.getKey();
            List<FeegowAppointment> group = entry.getValue();

            // Filtrar agendamentos elegíveis dentro do grupo
            List<FeegowAppointment> eligibleAppointments = new ArrayList<>();
            List<DbLookupResult> eligibleDbResults = new ArrayList<>();

            for (FeegowAppointment appointment : group) {
                String feegowAppointmentId = normalizeFeegowAppointmentId(appointment.id());
                if (feegowAppointmentId.isBlank()) {
                    log.warn("ID Feegow vazio. RawId={}", appointment.id());
                    continue;
                }

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
                            appointment.id(), appointment.doctorId());
                    continue;
                }

                if (dbData.ignoreAutoSchedule()) {
                    log.info("Agendamento ignorado por exceção de médico (ignore_auto_schedule=true). appointmentId={}, profissional_id={}",
                            appointment.id(), appointment.doctorId());
                    continue;
                }

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

                eligibleAppointments.add(appointment);
                eligibleDbResults.add(dbData);
            }

            if (eligibleAppointments.isEmpty()) {
                continue;
            }

            filteredReceived += eligibleAppointments.size();

            if (eligibleAppointments.size() == 1) {
                // Fluxo tradicional
                FeegowAppointment appointment = eligibleAppointments.get(0);
                DbLookupResult dbData = eligibleDbResults.get(0);
                String feegowAppointmentId = normalizeFeegowAppointmentId(appointment.id());

                final String appointmentDoctorId = appointment.doctorId() != null ? appointment.doctorId().trim() : null;
                String resolvedProfessionalName;
                if (dbData.mappingProfessionalName() != null
                    && !dbData.mappingProfessionalName().isBlank()
                    && !"null".equalsIgnoreCase(dbData.mappingProfessionalName().trim())) {
                    resolvedProfessionalName = dbData.mappingProfessionalName().trim();
                } else {
                    resolvedProfessionalName = "Clínica Inovare";
                }

                FeegowPatient patientDetails;
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
                        log.error("Falha ao recuperar contato do paciente ID {}. Pulando agendamento.", patientId, ex);
                        continue;
                    }

                    try {
                        String fetchedName = professionalFuture.join();
                        if (fetchedName != null && !fetchedName.isBlank()) {
                            resolvedProfessionalName = fetchedName;
                        }
                    } catch (Exception ignored) {}
                }

                String patientPhone = patientDetails != null ? patientDetails.phone() : null;
                String phoneNumber = normalizePhoneNumberForBlip(patientPhone);
                if (patientPhone == null || patientPhone.isBlank() || phoneNumber.isBlank()) {
                    log.error("Falha ao recuperar contato do paciente ID {}. Pulando agendamento.", patientId);
                    continue;
                }

                if (appointmentMotorProperties.isTestMode()) {
                    phoneNumber = redirectTestPhoneIfNeeded(phoneNumber, appointment.doctorId(), patientPhone);
                    if (phoneNumber == null) continue;
                }

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

                String safeProfissionalNome = resolvedProfessionalName;
                String safeFilaDestino = (dbData.mappingQueue() != null
                    && !dbData.mappingQueue().isBlank()
                    && !"null".equalsIgnoreCase(dbData.mappingQueue().trim()))
                    ? dbData.mappingQueue().trim()
                    : StringSanitizer.UNICODE_LTR_MARK;

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

                boolean templateSent = sendAppointmentTemplateUseCase.execute(ctx, AppointmentCategory.CONFIRMATION);
                created++;
                if (templateSent) {
                    messagesSent++;
                }

            } else {
                // Fluxo de grupo (> 1 agendamento elegível)
                log.info("[GRUPO] Paciente ID {} possui {} agendamentos elegíveis no dia.", patientId, eligibleAppointments.size());

                FeegowPatient patientDetails;
                try {
                    patientDetails = patientExternalPort.patientInfo(patientId);
                } catch (Exception ex) {
                    log.error("Falha ao recuperar contato do paciente ID {}. Pulando agendamento agrupado.", patientId, ex);
                    continue;
                }

                String patientPhone = patientDetails != null ? patientDetails.phone() : null;
                String phoneNumber = normalizePhoneNumberForBlip(patientPhone);
                if (patientPhone == null || patientPhone.isBlank() || phoneNumber.isBlank()) {
                    log.error("Falha ao recuperar contato do paciente ID {} para agendamento agrupado.", patientId);
                    continue;
                }

                if (appointmentMotorProperties.isTestMode()) {
                    phoneNumber = redirectTestPhoneIfNeeded(phoneNumber, eligibleAppointments.get(0).doctorId(), patientPhone);
                    if (phoneNumber == null) continue;
                }

                List<AppointmentSession> savedSessions = new ArrayList<>();
                for (FeegowAppointment appointment : eligibleAppointments) {
                    String feegowAppointmentId = normalizeFeegowAppointmentId(appointment.id());
                    try {
                        final String finalPhoneNumber = phoneNumber;
                        AppointmentSession session = transactionTemplate.execute(status -> {
                            Optional<AppointmentSession> latestSessionOpt = appointmentSessionRepository.findByFeegowAppointmentId(feegowAppointmentId);
                            AppointmentSession s = latestSessionOpt.orElseGet(AppointmentSession::new);

                            s.setFeegowAppointmentId(feegowAppointmentId);
                            s.setPatientId(appointment.patientId());
                            s.setPhoneNumber(finalPhoneNumber);
                            s.setDoctorProfissionalId(appointment.doctorId());
                            s.setAppointmentAt(appointment.startAt());
                            s.setStatus(AppointmentSessionStatus.PENDING);
                            s.setLastInteractionAt(LocalDateTime.now());
                            s.setClosedAt(null);
                            s.setStatusDetails(null);

                            AppointmentSession savedS = appointmentSessionRepository.save(s);
                            entityManager.flush();
                            return savedS;
                        });
                        savedSessions.add(session);
                    } catch (RuntimeException ex) {
                        log.error("Falha grave na persistência da sessão no banco de dados para feegowAppointmentId={}. Detalhes: {}", feegowAppointmentId, ex.getMessage(), ex);
                    }
                }

                if (savedSessions.size() < 2) {
                    log.warn("Menos de 2 sessões salvas para o grupo do paciente ID {}. Abortando envio de grupo.", patientId);
                    continue;
                }

                UUID groupId = UUID.randomUUID();
                List<NotificationGroup> groupEntities = new ArrayList<>();
                for (AppointmentSession session : savedSessions) {
                    NotificationGroup ng = NotificationGroup.builder()
                        .groupId(groupId)
                        .sessionId(session.getId())
                        .createdAt(LocalDateTime.now())
                        .build();
                    groupEntities.add(ng);
                }

                try {
                    notificationGroupRepository.saveAll(groupEntities);
                    log.info("[GRUPO] NotificationGroup salvo no banco com groupId={} contendo {} sessões.", groupId, savedSessions.size());
                } catch (RuntimeException ex) {
                    log.error("Falha grave ao salvar NotificationGroup para groupId={}.", groupId, ex);
                    continue;
                }

                // Atualiza lastNotificationSentAt = LocalDateTime.now() nas sessões salvas
                for (AppointmentSession session : savedSessions) {
                    session.setLastNotificationSentAt(LocalDateTime.now());
                    try {
                        transactionTemplate.executeWithoutResult(status -> {
                            appointmentSessionRepository.save(session);
                        });
                    } catch (RuntimeException ex) {
                        log.error("Erro ao atualizar lastNotificationSentAt para sessionId={}.", session.getId(), ex);
                    }
                }

                final String finalPatientName = (patientDetails != null && patientDetails.name() != null)
                    ? patientDetails.name().trim() : "Paciente";

                // Envia template de grupo
                blipNotificationService.sendGroupTemplateMessage(phoneNumber, "aviso_agendamento_grupo", groupId, finalPatientName);
                created += savedSessions.size();
                messagesSent++;
            }
        }

        String mode = appointmentMotorProperties.isTestMode() ? "TEST" : "PROD";
        log.info("Ingestão de consultas executada. totalRecebido={}, totalAposFiltro={}, sessoesCriadas={}, mensagensEnviadas={}, modo={}",
                totalReceived, filteredReceived, created, messagesSent, mode);

        return new IngestionSummary(totalReceived, filteredReceived, created, messagesSent, mode);
    }

    private String redirectTestPhoneIfNeeded(String phoneNumber, String doctorId, String patientPhone) {
        // Filtra os IDs dos médicos homologados para o ambiente de teste
        String testDoctorId = appointmentMotorProperties.getTestDoctorId();
        java.util.List<String> allowedIds = java.util.Arrays.stream(testDoctorId.split(","))
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .toList();
        
        String docId = doctorId != null ? doctorId.trim() : "";
        if (!allowedIds.contains(docId)) {
            log.info("[TRAVA DE TESTE] Agendamento pulado. Médico ID {} não está na lista de homologação ({}).", docId, testDoctorId);
            return null;
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
                return null;
            }

            // Se o telefone do paciente real (já normalizado) for um dos homologados, mantém o envio direto
            if (allowedPhones.contains(phoneNumber)) {
                log.info("[TEST MODE] Telefone do paciente real ({}) é um número de homologação permitido. Mantendo o número original.", patientPhone);
                return phoneNumber;
            } else {
                // Caso contrário, redireciona o envio para o primeiro número de teste cadastrado (número primário)
                String redirectPhone = allowedPhones.get(0);
                log.info("[TEST MODE] Telefone do paciente real ({}) redirecionado para o número de teste homologado primário: {}", patientPhone, redirectPhone);
                return redirectPhone;
            }
        } else {
            log.warn("[TEST MODE] Envio bloqueado para o paciente real ({}): nenhum telefone de teste (APP_APPOINTMENT_MOTOR_TEST_PHONE) configurado no ambiente.", patientPhone);
            return null;
        }
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
