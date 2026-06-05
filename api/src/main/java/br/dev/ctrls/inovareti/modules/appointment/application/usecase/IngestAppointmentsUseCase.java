package br.dev.ctrls.inovareti.modules.appointment.application.usecase;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import br.dev.ctrls.inovareti.modules.appointment.application.dto.AppointmentDispatchContext;
import br.dev.ctrls.inovareti.modules.appointment.application.service.AppointmentSendIdempotencyService;
import br.dev.ctrls.inovareti.modules.appointment.application.service.BlipNotificationService;
import br.dev.ctrls.inovareti.modules.appointment.application.service.NoopAppointmentSendIdempotencyService;
import br.dev.ctrls.inovareti.modules.appointment.application.service.FeegowAppointmentSearcher;
import br.dev.ctrls.inovareti.modules.appointment.application.service.FeegowPatientDetailsFetcher;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentCategory;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSession;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSessionStatus;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.NotificationGroup;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentConfig;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentConfigRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentDoctorMappingRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentSessionRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.FeegowAppointment;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.FeegowPatient;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.NotificationGroupRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.ProfessionalExternalPort;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.AppointmentMotorProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Caso de Uso responsável pela ingestão diária de agendamentos vindos do Feegow,
 * gerando sessões locais e disparando notificações individuais ou em lote.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IngestAppointmentsUseCase {

    private final AppointmentMotorProperties appointmentMotorProperties;
    private final ProfessionalExternalPort professionalExternalPort;
    private final AppointmentDoctorMappingRepositoryPort appointmentDoctorMappingRepository;
    private final AppointmentSessionRepositoryPort appointmentSessionRepository;
    private final SendAppointmentTemplateUseCase sendAppointmentTemplateUseCase;
    private final Optional<AppointmentSendIdempotencyService> appointmentSendIdempotencyService;
    private final Optional<NoopAppointmentSendIdempotencyService> noopAppointmentSendIdempotencyService;
    private final jakarta.persistence.EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;
    private final NotificationGroupRepositoryPort notificationGroupRepository;
    private final BlipNotificationService blipNotificationService;
    private final AppointmentConfigRepositoryPort appointmentConfigRepository;
    private final FeegowAppointmentSearcher feegowAppointmentSearcher;
    private final FeegowPatientDetailsFetcher feegowPatientDetailsFetcher;

    public IngestionSummary execute() {
        LocalDate targetDate = LocalDate.now().plusDays(1);
        log.info("Iniciando ingestão de agendamentos para a data: {}", targetDate);

        List<FeegowAppointment> appointments = feegowAppointmentSearcher.searchAppointments(targetDate);
        int total = appointments.size();
        appointments = appointments.stream()
                .filter(a -> a.startAt() != null && !a.startAt().toLocalDate().isBefore(LocalDate.now()))
                .collect(Collectors.toList());
        int filtrados = appointments.size();
        log.info("Filtrando agendamentos antigos. Total antes: {}, Total depois: {}", total, filtrados);

        int totalReceived = filtrados;

        java.util.Set<String> feegowIds = appointments.stream()
                .map(a -> normalizeFeegowAppointmentId(a.id()))
                .filter(id -> !id.isBlank())
                .collect(Collectors.toSet());

        Map<String, AppointmentSession> sessionCache = appointmentSessionRepository.findByFeegowAppointmentIdIn(feegowIds).stream()
                .collect(Collectors.toMap(AppointmentSession::getFeegowAppointmentId, s -> s, (s1, s2) -> s1));

        Map<String, br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentDoctorMapping> doctorMappingCache = appointmentDoctorMappingRepository.findAll().stream()
                .collect(Collectors.toMap(br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentDoctorMapping::getProfissionalId, m -> m, (m1, m2) -> m1));

        List<FeegowAppointment> activeAppointments = appointments.stream()
                .filter(appointment -> !"12".equals(appointment.statusId()))
                .collect(Collectors.toList());

        java.util.Set<String> patientIds = activeAppointments.stream()
                .map(FeegowAppointment::patientId)
                .collect(Collectors.toSet());

        Map<String, FeegowPatient> patientDetailsCache = feegowPatientDetailsFetcher.fetchPatientDetailsInParallel(patientIds);

        Map<String, List<FeegowAppointment>> grouped = activeAppointments.stream()
                .collect(Collectors.groupingBy(appointment -> {
                    FeegowPatient patient = patientDetailsCache.get(appointment.patientId());
                    String phone = patient != null ? patient.phone() : null;
                    String normalized = purificarTelefoneParaGrupo(phone);
                    LocalDate date = appointment.startAt().toLocalDate();
                    return normalized + "#" + date;
                }));

        return processIngestionGroups(grouped, sessionCache, doctorMappingCache, patientDetailsCache, totalReceived);
    }

    private IngestionSummary processIngestionGroups(Map<String, List<FeegowAppointment>> grouped, Map<String, AppointmentSession> sessionCache,
            Map<String, br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentDoctorMapping> doctorMappingCache,
            Map<String, FeegowPatient> patientDetailsCache, int totalReceived) {
        
        int created = 0;
        int messagesSent = 0;
        int filteredReceived = 0;

        for (Map.Entry<String, List<FeegowAppointment>> entry : grouped.entrySet()) {
            String key = entry.getKey();
            List<FeegowAppointment> groupAppointments = entry.getValue();

            // Log espiao para auditar os numeros digitados
            for (FeegowAppointment appt : groupAppointments) {
                FeegowPatient patient = patientDetailsCache.get(appt.patientId());
                String patientName = patient != null ? patient.name() : "Paciente";
                String rawPhone = patient != null ? patient.phone() : "null";
                String purifiedPhone = purificarTelefoneParaGrupo(rawPhone);
                String appointmentTime = appt.startAt() != null ? appt.startAt().toString() : "null";
                log.info("[AUDITORIA-TELEFONE] paciente={}, horario={}, telefoneBruto={}, telefonePurificado={}",
                    patientName, appointmentTime, rawPhone, purifiedPhone);
            }

            List<FeegowAppointment> eligibleAppointments = filterEligibleAppointments(groupAppointments, sessionCache, doctorMappingCache);

            if (eligibleAppointments.isEmpty()) {
                continue;
            }

            filteredReceived += eligibleAppointments.size();

            String[] keyParts = key.split("#", 2);
            String normalizedPhone = keyParts[0];

            if (normalizedPhone.isBlank()) {
                continue;
            }

            if (eligibleAppointments.size() == 1) {
                FeegowAppointment appt = eligibleAppointments.get(0);
                FeegowPatient patientDetails = patientDetailsCache.get(appt.patientId());
                if (processSingleFlow(appt, doctorMappingCache, patientDetails)) {
                    created++;
                    messagesSent++;
                }
            } else {
                FeegowAppointment firstAppt = eligibleAppointments.get(0);
                FeegowPatient patientDetails = patientDetailsCache.get(firstAppt.patientId());
                String blipPhone = "55" + normalizedPhone;
                int sent = processGroupFlow(eligibleAppointments, patientDetails, blipPhone);
                created += sent;
                if (sent > 0) {
                    messagesSent++;
                }
            }
        }

        String mode = appointmentMotorProperties.isTestMode() ? "TEST" : "PROD";
        log.info("Ingestão executada. totalRecebido={}, totalAposFiltro={}, sessoesCriadas={}, mensagensEnviadas={}, modo={}",
                totalReceived, filteredReceived, created, messagesSent, mode);

        return new IngestionSummary(totalReceived, filteredReceived, created, messagesSent, mode);
    }

    private List<FeegowAppointment> filterEligibleAppointments(List<FeegowAppointment> group, Map<String, AppointmentSession> sessionCache,
            Map<String, br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentDoctorMapping> doctorMappingCache) {
        
        List<FeegowAppointment> eligible = new ArrayList<>();
        for (FeegowAppointment appointment : group) {
            String feegowAppointmentId = normalizeFeegowAppointmentId(appointment.id());
            if (feegowAppointmentId.isBlank()) continue;

            var mapping = doctorMappingCache.get(appointment.doctorId());
            if (mapping == null || "inactive".equalsIgnoreCase(mapping.getBlipQueueId()) || mapping.isIgnoreAutoSchedule()) {
                continue;
            }

            AppointmentSession existing = sessionCache.get(feegowAppointmentId);
            Optional<AppointmentSession> existingSessionOpt = Optional.ofNullable(existing);

            String confirmedStatusId = appointmentMotorProperties.getFeegowConfirmedStatusId();
            if (confirmedStatusId == null || confirmedStatusId.isBlank()) {
                confirmedStatusId = "7";
            }
            boolean isConfirmedOnFeegow = confirmedStatusId.trim().equalsIgnoreCase(appointment.statusId());
            boolean isConfirmedLocally = existingSessionOpt.map(s -> s.getStatus() == AppointmentSessionStatus.CONFIRMED).orElse(false);

            if (isConfirmedLocally || isConfirmedOnFeegow) {
                continue;
            }

            if (existingSessionOpt.isPresent()) {
                AppointmentSessionStatus status = existingSessionOpt.get().getStatus();
                if (status == AppointmentSessionStatus.PENDING || status == AppointmentSessionStatus.NUDGE_1_SENT ||
                    status == AppointmentSessionStatus.NUDGE_FINAL_SENT || status == AppointmentSessionStatus.CONFIRMED) {
                    continue;
                }
            }

            boolean canSend = appointmentSendIdempotencyService.map(s -> s.registerIfFirstSend(feegowAppointmentId))
                    .orElseGet(() -> noopAppointmentSendIdempotencyService.map(s -> s.registerIfFirstSend(feegowAppointmentId)).orElse(true));

            if (canSend) {
                eligible.add(appointment);
            }
        }
        return eligible;
    }

    private boolean processSingleFlow(FeegowAppointment appointment,
            Map<String, br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentDoctorMapping> doctorMappingCache,
            FeegowPatient patientDetails) {
        
        String feegowAppointmentId = normalizeFeegowAppointmentId(appointment.id());
        var mapping = doctorMappingCache.get(appointment.doctorId());
        String mappingQueue = mapping != null ? mapping.getBlipQueueId() : null;
        String mappingProfessionalName = mapping != null ? mapping.getProfissionalNome() : null;

        String patientPhone = patientDetails != null ? patientDetails.phone() : null;
        String phoneNumber = normalizePhoneNumberForBlip(patientPhone);
        if (patientPhone == null || patientPhone.isBlank() || phoneNumber.isBlank()) {
            return false;
        }

        if (appointmentMotorProperties.isTestMode() && !isDoctorAllowedInTestMode(appointment.doctorId())) {
            return false;
        }

        AppointmentSession saved = saveSingleSession(feegowAppointmentId, appointment, phoneNumber);
        if (saved == null) return false;

        return dispatchSingleTemplate(saved, appointment, mappingQueue, mappingProfessionalName, phoneNumber, patientDetails);
    }

    private AppointmentSession saveSingleSession(String feegowAppointmentId, FeegowAppointment appointment, String phoneNumber) {
        return transactionTemplate.execute(status -> {
            Optional<AppointmentSession> latestSessionOpt = appointmentSessionRepository.findByFeegowAppointmentId(feegowAppointmentId);
            if (latestSessionOpt.isPresent()) {
                AppointmentSessionStatus currentStatus = latestSessionOpt.get().getStatus();
                if (currentStatus == AppointmentSessionStatus.PENDING || currentStatus == AppointmentSessionStatus.NUDGE_1_SENT ||
                    currentStatus == AppointmentSessionStatus.NUDGE_FINAL_SENT || currentStatus == AppointmentSessionStatus.CONFIRMED) {
                    return null;
                }
            }
            AppointmentSession session = latestSessionOpt.orElseGet(AppointmentSession::new);
            session.setFeegowAppointmentId(feegowAppointmentId);
            session.setPatientId(appointment.patientId());
            session.setPhoneNumber(phoneNumber);
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
    }

    private boolean dispatchSingleTemplate(AppointmentSession saved, FeegowAppointment appointment, String mappingQueue,
            String mappingProfessionalName, String phoneNumber, FeegowPatient patientDetails) {
        
        String resolvedProfessionalName = resolveDoctorName(appointment.doctorId(), mappingProfessionalName, appointment.doctorName());
        String safeFilaDestino = (mappingQueue != null && !mappingQueue.isBlank() && !"null".equalsIgnoreCase(mappingQueue.trim())) ? mappingQueue.trim() : "\u200E";

        String finalPatientName = (patientDetails != null && patientDetails.name() != null) ? patientDetails.name().trim() : "Paciente";
        String finalPatientPhone = (patientDetails != null) ? patientDetails.phone() : null;

        java.time.format.DateTimeFormatter dtfDate = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
        java.time.format.DateTimeFormatter dtfShort = java.time.format.DateTimeFormatter.ofPattern("dd/MM");
        java.time.format.DateTimeFormatter dtfTime = java.time.format.DateTimeFormatter.ofPattern("HH:mm");

        String finalDate = saved.getAppointmentAt() != null ? saved.getAppointmentAt().toLocalDate().format(dtfDate) : "";
        String finalDateShort = saved.getAppointmentAt() != null ? saved.getAppointmentAt().toLocalDate().format(dtfShort) : "";
        String finalTime = saved.getAppointmentAt() != null ? saved.getAppointmentAt().toLocalTime().format(dtfTime) : "";

        AppointmentDispatchContext ctx = new AppointmentDispatchContext(
            saved.getId(), saved.getFeegowAppointmentId(), finalPatientName, finalPatientPhone,
            saved.getPatientId(), appointment.doctorId(), resolvedProfessionalName, safeFilaDestino,
            finalDate, finalDateShort, finalTime, phoneNumber
        );

        return sendAppointmentTemplateUseCase.execute(ctx, AppointmentCategory.CONFIRMATION);
    }

    private String resolveDoctorName(String doctorId, String mappingName, String feegowDoctorName) {
        if (mappingName != null && !mappingName.isBlank() && !"null".equalsIgnoreCase(mappingName.trim())) {
            return mappingName.trim();
        }
        String resolved = feegowDoctorName;
        if (resolved == null || resolved.isBlank() || "null".equalsIgnoreCase(resolved.trim())) {
            try {
                resolved = professionalExternalPort.getProfessionalName(doctorId);
            } catch (Exception e) {
                log.warn("Falha ao recuperar nome do profissional via Feegow: {}", e.getMessage());
            }
        }
        return (resolved != null && !resolved.isBlank() && !"null".equalsIgnoreCase(resolved.trim())) ? resolved.trim() : "Clínica Inovare";
    }

    private int processGroupFlow(List<FeegowAppointment> eligibleAppointments, FeegowPatient patientDetails, String normalizedPhone) {
        if (normalizedPhone == null || normalizedPhone.isBlank()) {
            return 0;
        }

        if (appointmentMotorProperties.isTestMode() && !isDoctorAllowedInTestMode(eligibleAppointments.get(0).doctorId())) {
            return 0;
        }

        String phoneNumber = normalizedPhone;

        List<AppointmentSession> savedSessions = saveGroupSessions(eligibleAppointments, phoneNumber);
        if (savedSessions.size() < 2) {
            return 0;
        }

        UUID groupId = UUID.randomUUID();
        if (!saveNotificationGroup(groupId, savedSessions, phoneNumber)) {
            return 0;
        }

        updateSessionsNotificationTimestamp(savedSessions);

        String finalPatientName = (patientDetails != null && patientDetails.name() != null) ? patientDetails.name().trim() : "Paciente";
        sendGroupNotification(phoneNumber, groupId, finalPatientName);

        return savedSessions.size();
    }

    private List<AppointmentSession> saveGroupSessions(List<FeegowAppointment> eligibleAppointments, String phoneNumber) {
        List<AppointmentSession> savedSessions = new ArrayList<>();
        for (FeegowAppointment appointment : eligibleAppointments) {
            String feegowAppointmentId = normalizeFeegowAppointmentId(appointment.id());
            try {
                AppointmentSession session = saveGroupSessionSingle(feegowAppointmentId, appointment, phoneNumber);
                if (session != null) {
                    savedSessions.add(session);
                }
            } catch (RuntimeException ex) {
                log.error("Falha na persistência da sessão no banco de dados para feegowAppointmentId={}.", feegowAppointmentId, ex);
            }
        }
        return savedSessions;
    }

    private AppointmentSession saveGroupSessionSingle(String feegowAppointmentId, FeegowAppointment appointment, String phoneNumber) {
        return transactionTemplate.execute(status -> {
            Optional<AppointmentSession> latestSessionOpt = appointmentSessionRepository.findByFeegowAppointmentId(feegowAppointmentId);
            if (latestSessionOpt.isPresent()) {
                AppointmentSessionStatus currentStatus = latestSessionOpt.get().getStatus();
                if (currentStatus == AppointmentSessionStatus.PENDING || currentStatus == AppointmentSessionStatus.NUDGE_1_SENT ||
                    currentStatus == AppointmentSessionStatus.NUDGE_FINAL_SENT || currentStatus == AppointmentSessionStatus.CONFIRMED) {
                    return latestSessionOpt.get();
                }
            }
            AppointmentSession s = latestSessionOpt.orElseGet(AppointmentSession::new);
            s.setFeegowAppointmentId(feegowAppointmentId);
            s.setPatientId(appointment.patientId());
            s.setPhoneNumber(phoneNumber);
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
    }

    private boolean saveNotificationGroup(UUID groupId, List<AppointmentSession> savedSessions, String phoneNumber) {
        List<NotificationGroup> groupEntities = new ArrayList<>();
        for (AppointmentSession session : savedSessions) {
            groupEntities.add(NotificationGroup.builder()
                .groupId(groupId)
                .sessionId(session.getId())
                .phoneNumber(phoneNumber)
                .createdAt(LocalDateTime.now())
                .build());
        }
        try {
            notificationGroupRepository.saveAll(groupEntities);
            log.info("[GRUPO] NotificationGroup salvo no banco com groupId={} contendo {} sessões.", groupId, savedSessions.size());
            return true;
        } catch (RuntimeException ex) {
            log.error("Falha grave ao salvar NotificationGroup para groupId={}.", groupId, ex);
            return false;
        }
    }

    private void updateSessionsNotificationTimestamp(List<AppointmentSession> savedSessions) {
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
    }

    private void sendGroupNotification(String phoneNumber, UUID groupId, String patientName) {
        String groupTemplateName = transactionTemplate.execute(status ->
            appointmentConfigRepository.findByCategory(AppointmentCategory.GROUP_NOTIFICATION)
                .map(AppointmentConfig::getTemplateId)
                .orElse("aviso_agendamento_grupo")
        );
        log.info("[GRUPO] Template de grupo resolvido: '{}'. groupId={}", groupTemplateName, groupId);
        try {
            blipNotificationService.sendGroupTemplateMessage(phoneNumber, groupTemplateName, groupId, patientName);
        } catch (Exception e) {
            log.error("[ERRO-CRITICO-GRUPO] Falha ao transmitir grupo para a Blip", e);
        }
    }

    private boolean isDoctorAllowedInTestMode(String doctorId) {
        if (!appointmentMotorProperties.isTestMode()) {
            return true;
        }
        String testDoctorId = appointmentMotorProperties.getTestDoctorId();
        java.util.List<String> allowedIds = java.util.Arrays.stream(testDoctorId.split(","))
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .toList();
        
        String docId = doctorId != null ? doctorId.trim() : "";
        return allowedIds.contains(docId);
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
        String purified = purificarTelefoneParaGrupo(originalPhone);
        if (purified.isEmpty()) {
            return "";
        }
        return "55" + purified;
    }

    private String purificarTelefoneParaGrupo(String originalPhone) {
        if (originalPhone == null || originalPhone.isBlank()) {
            return "";
        }
        String digitsOnly = originalPhone.replaceAll("\\D", "");
        if (digitsOnly.startsWith("55")) {
            digitsOnly = digitsOnly.substring(2);
        }
        if (digitsOnly.startsWith("0")) {
            digitsOnly = digitsOnly.substring(1);
        }
        return digitsOnly;
    }

    public record IngestionSummary(int totalReceived, int filteredReceived, int sessionsCreated, int messagesSent, String mode) {
    }
}
