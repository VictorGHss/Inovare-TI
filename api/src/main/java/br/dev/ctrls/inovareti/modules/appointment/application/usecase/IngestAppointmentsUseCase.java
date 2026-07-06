package br.dev.ctrls.inovareti.modules.appointment.application.usecase;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import br.dev.ctrls.inovareti.modules.appointment.application.dto.AppointmentDispatchContext;
import br.dev.ctrls.inovareti.modules.appointment.application.service.AppointmentSendIdempotencyService;
import br.dev.ctrls.inovareti.modules.appointment.application.service.BlipAppointmentFormatter;
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
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.BlipUserIdentityReconciliationRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.FeegowAppointment;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.FeegowPatient;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.NotificationGroupRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.ProfessionalExternalPort;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.AppointmentMotorProperties;
import br.dev.ctrls.inovareti.modules.appointment.application.service.BlipContextService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Caso de Uso responsável pela ingestão diária de agendamentos vindos do Feegow,
 * gerando sessões locais e disparando notificações individuais ou em lote.
 *
 * Performance:
 * - Grupos são processados em paralelo via Virtual Threads.
 * - Um Semaphore limita as chamadas simultâneas ao Blip para evitar HTTP 429.
 * - Toda a persistência de cada grupo (sessões + NotificationGroup) ocorre
 *   em uma única transação de banco, reduzindo o overhead do HikariCP.
 * - O contexto do Blip é configurado em fire-and-forget para não bloquear
 *   o envio imediato do template ao paciente.
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
    private final TransactionTemplate transactionTemplate;
    private final NotificationGroupRepositoryPort notificationGroupRepository;
    private final BlipNotificationService blipNotificationService;
    private final BlipAppointmentFormatter blipAppointmentFormatter;
    private final AppointmentConfigRepositoryPort appointmentConfigRepository;
    private final FeegowAppointmentSearcher feegowAppointmentSearcher;
    private final FeegowPatientDetailsFetcher feegowPatientDetailsFetcher;
    private final BlipContextService blipContextService;
    private final br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.BlipProperties blipProperties;
    private final BlipUserIdentityReconciliationRepositoryPort blipUserIdentityReconciliationRepository;
    private final org.springframework.core.task.AsyncTaskExecutor applicationTaskExecutor;

    /**
     * Semaphore que limita a quantidade de grupos processando chamadas ao Blip simultaneamente.
     * Evita rajadas de HTTP 429 (Too Many Requests) na API do Blip durante o lote diário.
     * Configurável via APP_APPOINTMENT_BLIP_INGEST_CONCURRENCY (padrão: 20).
     */
    @Value("${APP_APPOINTMENT_BLIP_INGEST_CONCURRENCY:20}")
    private int blipIngestConcurrency;

    private Semaphore blipSemaphore;

    @jakarta.annotation.PostConstruct
    public void init() {
        this.blipSemaphore = new Semaphore(blipIngestConcurrency, true);
    }

    /** Encapsula o resultado da persistência de um grupo dentro da transação única. */
    private record GroupPersistenceResult(List<AppointmentSession> savedSessions, String preCompiledText) {}

    public IngestionSummary execute() {
        java.time.DayOfWeek dayOfWeek = LocalDate.now().getDayOfWeek();
        LocalDate targetDate;
        if (dayOfWeek == java.time.DayOfWeek.FRIDAY) {
            targetDate = LocalDate.now().plusDays(3);
        } else {
            targetDate = LocalDate.now().plusDays(1);
        }
        log.info("Iniciando ingestão de agendamentos para a data alvo: {} (Dia da semana atual: {})", targetDate, dayOfWeek);

        List<FeegowAppointment> appointments = feegowAppointmentSearcher.searchAppointments(targetDate);
        int total = appointments.size();

        // Filtro de Encaixe: ignora agendamentos que possuem a flag encaixe ativa
        appointments = appointments.stream()
                .filter(a -> {
                    if (a.encaixe() != null && a.encaixe()) {
                        log.info("[FILTRO-ENCAIXE] Agendamento ID={} ignorado porque é um encaixe.", a.id());
                        return false;
                    }
                    return true;
                })
                .collect(Collectors.toList());
        int aposEncaixe = appointments.size();
        log.info("Agendamentos filtrados por encaixe. Total antes: {}, Total depois: {}", total, aposEncaixe);

        // Filtro de Procedimento: apenas IDs permitidos na propriedade ELIGIBLE_PROCEDURE_IDS
        String eligibleIdsProp = appointmentMotorProperties.getEligibleProcedureIds();
        final java.util.List<String> eligibleProcedureIdsList;
        if (eligibleIdsProp == null || eligibleIdsProp.isBlank()) {
            eligibleProcedureIdsList = java.util.Collections.emptyList();
            log.warn("[FILTRO-PROCEDIMENTO] A lista de IDs de procedimentos elegíveis (ELIGIBLE_PROCEDURE_IDS) está vazia ou nula.");
        } else {
            eligibleProcedureIdsList = java.util.Arrays.stream(eligibleIdsProp.split(","))
                    .map(String::trim)
                    .filter(id -> !id.isEmpty())
                    .toList();
            log.info("[FILTRO-PROCEDIMENTO] IDs de procedimentos elegíveis configurados: {}", eligibleProcedureIdsList);
        }

        appointments = appointments.stream()
                .filter(a -> {
                    String procId = a.procedureId();
                    if (procId == null || procId.isBlank()) {
                        log.info("[FILTRO-PROCEDIMENTO] Agendamento ID={} ignorado porque o ID do procedimento está nulo ou vazio.", a.id());
                        return false;
                    }
                    boolean val = eligibleProcedureIdsList.contains(procId.trim());
                    if (!val) {
                        log.info("[FILTRO-PROCEDIMENTO] Agendamento ID={} ignorado porque o procedimento_id '{}' (nome: '{}') não está na lista de procedimentos elegíveis.", a.id(), procId, a.procedureName());
                    }
                    return val;
                })
                .collect(Collectors.toList());
        int aposProcedimentos = appointments.size();
        log.info("Agendamentos filtrados por procedimento. Total antes: {}, Total depois: {}", aposEncaixe, aposProcedimentos);

        appointments = appointments.stream()
                .filter(a -> a.startAt() != null && !a.startAt().toLocalDate().isBefore(LocalDate.now()))
                .collect(Collectors.toList());
        int filtrados = appointments.size();
        log.info("Filtrando agendamentos antigos. Total antes: {}, Total depois: {}", aposProcedimentos, filtrados);

        int totalReceived = filtrados;

        java.util.Set<String> feegowIds = appointments.stream()
                .map(a -> normalizeFeegowAppointmentId(a.id()))
                .filter(id -> !id.isBlank())
                .collect(Collectors.toSet());

        Map<String, AppointmentSession> sessionCache = appointmentSessionRepository.findByFeegowAppointmentIdIn(feegowIds).stream()
                .collect(Collectors.toMap(AppointmentSession::getFeegowAppointmentId, s -> s, (s1, s2) -> s1));

        Map<String, br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentDoctorMapping> doctorMappingCache = appointmentDoctorMappingRepository.findAll().stream()
                .collect(Collectors.toMap(br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentDoctorMapping::getProfissionalId, m -> m, (m1, m2) -> m1));

        // FILTRO ESTRUTURAL DE AUDITORIA: Remove agendamentos de médicos não-assinantes ou inativos antes de buscar detalhes dos pacientes
        int totalBeforeDoctorFilter = appointments.size();
        boolean billingEnabled = appointmentMotorProperties.isBillingEnabled();
        appointments = appointments.stream()
                .filter(appointment -> {
                    var mapping = doctorMappingCache.get(appointment.doctorId());
                    if (mapping == null) {
                        return false;
                    }
                    if ("inactive".equalsIgnoreCase(mapping.getBlipQueueId()) || mapping.isIgnoreAutoSchedule()) {
                        return false;
                    }
                    if (billingEnabled) {
                        boolean isLicensed = mapping.isActive() || (mapping.getSubscriptionEndDate() != null && mapping.getSubscriptionEndDate().isAfter(LocalDateTime.now()));
                        if (!isLicensed) {
                            log.info("[MONETIZATION] Acesso bloqueado para o ID={}", appointment.doctorId());
                            return false;
                        }
                    }
                    return true;
                })
                .collect(Collectors.toList());
        int removedByDoctorFilter = totalBeforeDoctorFilter - appointments.size();
        if (removedByDoctorFilter > 0) {
            log.info("[INGESTAO-AUDITORIA] Ignorados {} agendamentos pertencentes a médicos inativos ou não-assinantes.", removedByDoctorFilter);
        }

        List<FeegowAppointment> activeAppointments = appointments.stream()
                .filter(appointment -> {
                    String statusId = appointment.statusId();
                    if ("1".equals(statusId)) {
                        return true;
                    }
                    
                    String statusDescription = switch (statusId != null ? statusId.trim() : "") {
                        case "2" -> "Confirmado";
                        case "3" -> "Triagem";
                        case "4" -> "Em Atendimento";
                        case "5" -> "Atendido";
                        case "6" -> "Cancelado";
                        case "7" -> "Confirmado (Feegow)";
                        case "11" -> "Falta";
                        case "15" -> "Pré-Agendamento";
                        case "16" -> "Remarcado";
                        case "101", "103", "105" -> "Status de Telemedicina ou Integração";
                        default -> "Outro Status Desconhecido";
                    };
                    
                    log.info("[ELEGIBILIDADE-STATUS] Agendamento ID={} descartado sumariamente da esteira. Status ID={} ({}) não elegível. Apenas o status '1' (Marcado - não confirmado) é permitido para disparo.",
                            appointment.id(), statusId, statusDescription);
                    return false;
                })
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

        // Cache do nome do template buscado UMA vez antes do loop para evitar N queries idênticas ao banco.
        // O template não muda durante a execução da ingestão.
        String cachedGroupTemplateName = transactionTemplate.execute(status ->
            appointmentConfigRepository.findByCategory(AppointmentCategory.GROUP_NOTIFICATION)
                .map(AppointmentConfig::getTemplateId)
                .orElse(appointmentMotorProperties.getBlipTemplateGroup())
        );
        log.info("[INGESTAO] Template de grupo resolvido antes do loop: '{}'", cachedGroupTemplateName);

        AtomicInteger created = new AtomicInteger(0);
        AtomicInteger messagesSent = new AtomicInteger(0);
        AtomicInteger filteredReceived = new AtomicInteger(0);

        // Processa todos os grupos em paralelo usando Virtual Threads.
        // Cada grupo (paciente com múltiplos agendamentos) roda em sua própria thread virtual,
        // o que reduz o tempo total de ingestão de O(N) sequencial para O(1) paralelo
        // limitado pela latência do serviço mais lento (Blip ou banco de dados).
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            boolean hasSentBefore = false;

            for (Map.Entry<String, List<FeegowAppointment>> entry : grouped.entrySet()) {
                String key = entry.getKey();
                List<FeegowAppointment> groupAppointments = entry.getValue();

                // Log de auditoria dos telefones antes de submeter ao executor
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
                if (eligibleAppointments.isEmpty()) continue;

                filteredReceived.addAndGet(eligibleAppointments.size());

                String[] keyParts = key.split("#", 2);
                String normalizedPhone = keyParts[0];
                if (normalizedPhone.isBlank()) continue;

                // Implementação do Delay Seguro (Pacing/Throttling) em Português do Brasil (PT-BR)
                // Se já houver um disparo anterior neste lote de envio, aplica-se um delay controlado de 150 a 300 ms.
                // Usa parkNanos para evitar a chamada direta a Thread.sleep dentro do loop.
                if (hasSentBefore) {
                    long delayMillis = java.util.concurrent.ThreadLocalRandom.current().nextLong(150, 301);
                    log.info("[PACING-LOG] Aplicando espaçamento temporal controlado de {} milissegundos antes do próximo disparo de notificação na API do Blip.", delayMillis);
                    java.util.concurrent.locks.LockSupport.parkNanos(java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(delayMillis));
                    if (Thread.currentThread().isInterrupted()) {
                        Thread.currentThread().interrupt();
                        log.warn("[PACING-LOG] O delay de espaçamento de notificações foi interrompido.");
                    }
                } else {
                    hasSentBefore = true;
                }

                if (eligibleAppointments.size() == 1) {
                    // Agendamento individual: processa em paralelo também
                    final FeegowAppointment appt = eligibleAppointments.get(0);
                    final FeegowPatient patientDetails = patientDetailsCache.get(appt.patientId());
                    futures.add(CompletableFuture.runAsync(() -> {
                        if (processSingleFlow(appt, doctorMappingCache, patientDetails)) {
                            created.incrementAndGet();
                            messagesSent.incrementAndGet();
                        }
                    }, executor));
                } else {
                    // Grupo: processa em paralelo com controle de concorrência no Blip via Semaphore
                    final FeegowAppointment firstAppt = eligibleAppointments.get(0);
                    final FeegowPatient patientDetails = patientDetailsCache.get(firstAppt.patientId());
                    final String blipPhone = "55" + normalizedPhone;
                    final String templateName = cachedGroupTemplateName;
                    futures.add(CompletableFuture.runAsync(() -> {
                        int sent = processGroupFlow(eligibleAppointments, patientDetails, blipPhone, templateName, patientDetailsCache);
                        created.addAndGet(sent);
                        if (sent > 0) messagesSent.incrementAndGet();
                    }, executor));
                }
            }

            // Aguarda todos os grupos terminarem antes de retornar o resumo
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        }

        String mode = appointmentMotorProperties.isTestMode() ? "TEST" : "PROD";
        log.info("Ingestão executada. totalRecebido={}, totalAposFiltro={}, sessoesCriadas={}, mensagensEnviadas={}, modo={}",
                totalReceived, filteredReceived.get(), created.get(), messagesSent.get(), mode);

        return new IngestionSummary(totalReceived, filteredReceived.get(), created.get(), messagesSent.get(), mode);
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

            if (appointmentMotorProperties.isBillingEnabled()) {
                boolean isLicensed = mapping.isActive() || (mapping.getSubscriptionEndDate() != null && mapping.getSubscriptionEndDate().isAfter(LocalDateTime.now()));
                if (!isLicensed) {
                    log.info("[MONETIZATION] Acesso bloqueado para o ID={}", appointment.doctorId());
                    continue;
                }
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
                log.info("[FILTRO-CONFIRMACAO] Abortando disparo para o agendamento ID={} pois o paciente já confirmou. (Confirmado no Feegow: {}, Confirmado localmente: {})",
                        feegowAppointmentId, isConfirmedOnFeegow, isConfirmedLocally);
                continue;
            }

            if (existingSessionOpt.isPresent()) {
                AppointmentSessionStatus status = existingSessionOpt.get().getStatus();
                if (status == AppointmentSessionStatus.PENDING || status == AppointmentSessionStatus.NUDGE_1_SENT ||
                    status == AppointmentSessionStatus.NUDGE_FINAL_SENT || status == AppointmentSessionStatus.CONFIRMED) {
                    log.info("[IDEMPOTENCIA] Disparo abortado para o agendamento ID={}. Mensagem já enviada anteriormente.", feegowAppointmentId);
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

            // Sem entityManager.flush() — o commit do transactionTemplate já faz flush automaticamente
            return appointmentSessionRepository.save(session);
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

    /**
     * Processa o fluxo de notificação para um grupo de agendamentos do mesmo paciente/telefone.
     *
     * Otimizações aplicadas:
     * 1. Toda a persistência (sessões + NotificationGroup) ocorre em UMA única transação de banco.
     *    currentGroupId e lastNotificationSentAt já são setados na entidade antes do primeiro save,
     *    eliminando os loops separados de populateCurrentGroupIdOnSessions e updateSessionsNotificationTimestamp.
     * 2. O contexto do Blip é configurado em fire-and-forget (sem bloquear o template).
     * 3. Um Semaphore global garante no máximo N chamadas simultâneas ao Blip.
     *
     * @param eligibleAppointments agendamentos elegíveis do grupo
     * @param patientDetails dados do paciente (nome)
     * @param normalizedPhone telefone no formato 55XXXXXXXXXXX
     * @param groupTemplateName nome do template Blip (pre-cacheado antes do loop)
     * @param patientDetailsCache cache de detalhes dos pacientes
     * @return quantidade de sessões salvas (0 se o grupo foi descartado)
     */
    private int processGroupFlow(List<FeegowAppointment> eligibleAppointments, FeegowPatient patientDetails,
            String normalizedPhone, String groupTemplateName, Map<String, FeegowPatient> patientDetailsCache) {
        if (normalizedPhone == null || normalizedPhone.isBlank()) {
            return 0;
        }
        if (appointmentMotorProperties.isTestMode() && !isDoctorAllowedInTestMode(eligibleAppointments.get(0).doctorId())) {
            return 0;
        }

        // Gera o groupId antes da transação para já associar nas sessões durante o primeiro save
        UUID groupId = UUID.randomUUID();
        String phoneNumber = normalizedPhone;

        // OTIMIZAÇÃO: toda a persistência do grupo (sessões + NotificationGroup) em UMA transação única.
        // currentGroupId e lastNotificationSentAt são setados ANTES do save, eliminando os loops
        // separados de populateCurrentGroupIdOnSessions e updateSessionsNotificationTimestamp.
        // Isso reduz o número de transações de banco de 9 (para grupo de 3 agendamentos) para 1.
        GroupPersistenceResult result = transactionTemplate.execute(status -> {
            List<AppointmentSession> savedSessions = new ArrayList<>();

            for (FeegowAppointment appointment : eligibleAppointments) {
                String feegowAppointmentId = normalizeFeegowAppointmentId(appointment.id());
                if (feegowAppointmentId.isBlank()) continue;
                try {
                    Optional<AppointmentSession> latestOpt = appointmentSessionRepository.findByFeegowAppointmentId(feegowAppointmentId);
                    AppointmentSession session = latestOpt.orElseGet(AppointmentSession::new);

                    // Sessões existentes em estado terminal são reutilizadas sem alterar status
                    if (latestOpt.isPresent()) {
                        AppointmentSessionStatus st = session.getStatus();
                        if (st == AppointmentSessionStatus.PENDING || st == AppointmentSessionStatus.NUDGE_1_SENT ||
                                st == AppointmentSessionStatus.NUDGE_FINAL_SENT || st == AppointmentSessionStatus.CONFIRMED) {
                            // Atualiza apenas o groupId e o timestamp sem mudar status
                            session.setCurrentGroupId(groupId);
                            session.setLastNotificationSentAt(LocalDateTime.now());
                            savedSessions.add(appointmentSessionRepository.save(session));
                            continue;
                        }
                    }

                    // Configura todos os campos da sessão em memória — apenas um save ao banco
                    session.setFeegowAppointmentId(feegowAppointmentId);
                    session.setPatientId(appointment.patientId());
                    session.setPhoneNumber(phoneNumber);
                    session.setDoctorProfissionalId(appointment.doctorId());
                    session.setAppointmentAt(appointment.startAt());
                    session.setStatus(AppointmentSessionStatus.PENDING);
                    session.setLastInteractionAt(LocalDateTime.now());
                    session.setClosedAt(null);
                    session.setStatusDetails(null);
                    // Já inclui groupId e timestamp para evitar updates separados posteriormente
                    session.setCurrentGroupId(groupId);
                    session.setLastNotificationSentAt(LocalDateTime.now());

                    savedSessions.add(appointmentSessionRepository.save(session));
                } catch (RuntimeException ex) {
                    log.error("[GRUPO] Falha ao persistir sessão para feegowId={}.", feegowAppointmentId, ex);
                }
            }

            if (savedSessions.size() < 2) {
                log.warn("[GRUPO] Menos de 2 sessões válidas para groupId={}. Abortando grupo.", groupId);
                status.setRollbackOnly();
                return new GroupPersistenceResult(List.of(), null);
            }

            // Pré-compila o texto da lista de agendamentos para o contexto do Blip
            String preCompiledText;
            try {
                preCompiledText = blipAppointmentFormatter.buildListaDetalhada(savedSessions, patientDetailsCache);
            } catch (Exception ex) {
                log.error("[GRUPO] Erro ao compilar lista detalhada para groupId={}.", groupId, ex);
                status.setRollbackOnly();
                return new GroupPersistenceResult(List.of(), null);
            }

            // Persiste os NotificationGroup dentro da mesma transação
            List<NotificationGroup> groupEntities = savedSessions.stream()
                .map(s -> NotificationGroup.builder()
                    .groupId(groupId)
                    .sessionId(s.getId())
                    .phoneNumber(phoneNumber)
                    .createdAt(LocalDateTime.now())
                    .preCompiledScheduleText(preCompiledText)
                    .build())
                .toList();
            try {
                notificationGroupRepository.saveAll(groupEntities);
                log.info("[GRUPO] Sessões + NotificationGroup persistidos em transação única. groupId={}, qtd={}",
                    groupId, savedSessions.size());
            } catch (RuntimeException ex) {
                log.error("[GRUPO] Falha ao salvar NotificationGroup para groupId={}.", groupId, ex);
                status.setRollbackOnly();
                return new GroupPersistenceResult(List.of(), null);
            }

            return new GroupPersistenceResult(savedSessions, preCompiledText);
        });

        if (result == null || result.savedSessions().isEmpty()) {
            return 0;
        }

        String finalPatientName = (patientDetails != null && patientDetails.name() != null)
            ? patientDetails.name().trim() : "Paciente";

        // OTIMIZAÇÃO: contexto do Blip configurado em background (fire-and-forget).
        // O paciente leva minutos para abrir o WhatsApp — o contexto sempre estará disponível.
        // O Semaphore garante no máximo 'blipIngestConcurrency' chamadas simultâneas ao Blip.
        final String preText = result.preCompiledText();
        CompletableFuture.runAsync(() -> {
            try {
                blipSemaphore.acquire();
                try {
                    setGroupContextDuringIngestion(phoneNumber, groupId, preText);
                } finally {
                    blipSemaphore.release();
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("[GRUPO] Interrompido aguardando semáforo para contexto Blip. groupId={}", groupId);
            }
        }, applicationTaskExecutor);

        // Envia o template imediatamente, sem esperar o contexto subir
        try {
            blipSemaphore.acquire();
            try {
                log.info("[GRUPO] Enviando template '{}' para {}. groupId={}", groupTemplateName, phoneNumber, groupId);
                blipNotificationService.sendGroupTemplateMessage(phoneNumber, groupTemplateName, groupId, finalPatientName);
            } finally {
                blipSemaphore.release();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("[GRUPO] Interrompido aguardando semáforo para envio de template. groupId={}", groupId);
        } catch (Exception e) {
            log.error("[ERRO-CRITICO-GRUPO] Falha ao enviar template para {}. groupId={}", phoneNumber, groupId, e);
        }

        return result.savedSessions().size();
    }


    /**
     * Configura as variáveis de contexto no Blip para o fluxo de grupo.
     * É chamado em fire-and-forget a partir de processGroupFlow.
     *
     * Define: lista_detalhada, groupId e isConfirmingAgenda.
     * O master-state NÃO é configurado — o roteamento é nativo do Blip Builder.
     */
    private void setGroupContextDuringIngestion(String phoneNumber, UUID groupId, String preCompiledText) {
        if (phoneNumber == null || phoneNumber.isBlank()) return;
        try {
            Map<String, String> fields = Map.of(
                "lista_detalhada", preCompiledText,
                "listaDetalhada", preCompiledText,
                "groupId", groupId.toString(),
                "isConfirmingAgenda", "true"
            );
            log.info("[INGESTAO-GRUPO-CONTEXTO] Configurando contexto Blip para {}. groupId={}", phoneNumber.trim(), groupId);

            String cleanPhone = phoneNumber.trim();
            String phoneDigits = cleanPhone;
            if (phoneDigits.contains("@")) {
                phoneDigits = phoneDigits.substring(0, phoneDigits.indexOf('@'));
            }

            // Busca se há reconciliação de identidade
            List<br.dev.ctrls.inovareti.modules.appointment.domain.model.BlipUserIdentityReconciliation> reconciliations =
                blipUserIdentityReconciliationRepository.findByPhoneNumber(phoneDigits);

            List<String> targetsForContext = new ArrayList<>();
            targetsForContext.add(cleanPhone);

            List<String> tunnelIdentities = new ArrayList<>();

            String subbotId = blipProperties.getSubbotId();
            String subbotLocalPart = null;
            if (subbotId != null && !subbotId.isBlank()) {
                subbotLocalPart = subbotId.trim();
                if (subbotLocalPart.contains("@")) {
                    subbotLocalPart = subbotLocalPart.substring(0, subbotLocalPart.indexOf('@'));
                }
            }

            // 1. Túnel determinístico (fallback)
            if (subbotLocalPart != null) {
                String deterministicTunnel = phoneDigits + "." + subbotLocalPart + "@tunnel.msging.net";
                tunnelIdentities.add(deterministicTunnel);
            }

            // 2. Túnel baseado na reconciliação real
            if (reconciliations != null && !reconciliations.isEmpty()) {
                for (var rec : reconciliations) {
                    if (rec.getBlipGuid() != null && !rec.getBlipGuid().isBlank()) {
                        String realTunnel = rec.getBlipGuid().trim() + "@tunnel.msging.net";
                        if (!tunnelIdentities.contains(realTunnel)) {
                            tunnelIdentities.add(realTunnel);
                            log.info("[INGESTAO-GRUPO-CONTEXTO] Identidade de túnel real reconciliada encontrada: {} para o telefone: {}", realTunnel, phoneDigits);
                        }
                    }
                }
            }

            // Adiciona todas as identidades de túnel para injeção de contexto também
            for (String tunnel : tunnelIdentities) {
                if (!targetsForContext.contains(tunnel)) {
                    targetsForContext.add(tunnel);
                }
            }

            // Injeta o contexto sequencialmente para todos os alvos (telefone e túneis) para evitar estouro de threads
            for (String target : targetsForContext) {
                try {
                    blipContextService.setUserContextFieldsInParallel(target, fields);
                } catch (Exception e) {
                    log.error("[INGESTAO-GRUPO-CONTEXTO] Erro ao configurar variáveis de contexto para target {}", target, e);
                }
            }

            String prepararAtendimentoBlockId = blipProperties.getBlocks().getPrepararAtendimento();

            if (prepararAtendimentoBlockId != null && !prepararAtendimentoBlockId.isBlank()) {
                // Roteador Master-State
                try {
                    blipContextService.setMasterState(cleanPhone, subbotId, prepararAtendimentoBlockId);
                    log.info("[INGESTAO-GRUPO-CONTEXTO] Master-State do Roteador atualizado na ingestão para {} apontando para o subbot e bloco {}", cleanPhone, prepararAtendimentoBlockId);
                } catch (Exception e) {
                    log.error("[INGESTAO-GRUPO-CONTEXTO] Erro ao atualizar Master-State no Roteador na ingestão para {}", cleanPhone, e);
                }

                // Subbot Builder Master-State (para cada túnel identificado) sequencialmente
                for (String tunnel : tunnelIdentities) {
                    try {
                        blipContextService.setBuilderMasterState(tunnel, prepararAtendimentoBlockId);
                        log.info("[INGESTAO-GRUPO-CONTEXTO] Builder Master-State atualizado na ingestão para o túnel {} e bloco {}", tunnel, prepararAtendimentoBlockId);
                    } catch (Exception e) {
                        log.error("[INGESTAO-GRUPO-CONTEXTO] Erro ao atualizar Builder Master-State no Subbot na ingestão para {}", tunnel, e);
                    }
                }
            }
        } catch (Exception e) {
            log.error("[INGESTAO-GRUPO-CONTEXTO] Falha ao configurar contexto/master-states na ingestão. telefone={}, groupId={}", phoneNumber, groupId, e);
        }
    }

    private boolean isDoctorAllowedInTestMode(String doctorId) {
        if (!appointmentMotorProperties.isTestMode()) {
            return true;
        }
        String testDoctorId = appointmentMotorProperties.getTestModeDoctorIds();
        if (testDoctorId == null || testDoctorId.isBlank()) {
            testDoctorId = appointmentMotorProperties.getTestDoctorId();
        }
        if (testDoctorId == null || testDoctorId.isBlank()) {
            return false;
        }
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
