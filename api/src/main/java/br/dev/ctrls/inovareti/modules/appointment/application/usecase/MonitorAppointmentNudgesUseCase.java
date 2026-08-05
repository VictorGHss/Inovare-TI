package br.dev.ctrls.inovareti.modules.appointment.application.usecase;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import br.dev.ctrls.inovareti.modules.appointment.application.service.BlipContextService;
import br.dev.ctrls.inovareti.modules.appointment.application.service.BlipNotificationService;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentCategory;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSession;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSessionStatus;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentConfigRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentSessionRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.AppointmentMotorProperties;
import lombok.RequiredArgsConstructor;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentExternalPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.FeegowAppointment;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class MonitorAppointmentNudgesUseCase {

    private static final ZoneId SAO_PAULO_ZONE = ZoneId.of("America/Sao_Paulo");

    private final AppointmentSessionRepositoryPort appointmentSessionRepository;
    private final AppointmentConfigRepositoryPort appointmentConfigRepository;
    private final AppointmentMotorProperties appointmentMotorProperties;
    private final SendAppointmentTemplateUseCase sendAppointmentTemplateUseCase;
    private final BlipContextService blipContextService;
    private final BlipNotificationService blipNotificationService;
    private final AppointmentExternalPort appointmentExternalPort;
    private final TransactionTemplate transactionTemplate;

    @Transactional
    public void execute() {
        // --- 1. RESOLVER TIMING DE REENVIO RECORRENTE (Padrão: 2 horas) ---
        int nudgeHours = appointmentConfigRepository.findByCategory(AppointmentCategory.NUDGE_1)
                .map(config -> config.getTimingHours())
                .orElse(appointmentMotorProperties.getNudge1WaitHours());

        LocalDateTime pendingThreshold = resolvePendingThreshold(nudgeHours);
        LocalDateTime todayStart = LocalDate.now(SAO_PAULO_ZONE).atStartOfDay();

        // --- 2. BUSCAR SESSÕES PENDENTES ELEGÍVEIS (appointmentAt >= TODAY e lastNotificationSentAt < 2h) ---
        List<AppointmentSession> pendingSessions = appointmentSessionRepository
                .findByStatusAndLastNotificationSentAtBefore(AppointmentSessionStatus.PENDING, pendingThreshold, todayStart);
        List<AppointmentSession> nudge1Sessions = appointmentSessionRepository
                .findByStatusAndLastNotificationSentAtBefore(AppointmentSessionStatus.NUDGE_1_SENT, pendingThreshold, todayStart);
        List<AppointmentSession> finalSessions = appointmentSessionRepository
                .findByStatusAndLastNotificationSentAtBefore(AppointmentSessionStatus.NUDGE_FINAL_SENT, pendingThreshold, todayStart);

        List<AppointmentSession> candidateSessions = new ArrayList<>();
        candidateSessions.addAll(pendingSessions);
        candidateSessions.addAll(nudge1Sessions);
        candidateSessions.addAll(finalSessions);

        int currentHour = LocalDateTime.now(SAO_PAULO_ZONE).getHour();
        log.info("[NUDGE-MONITOR] Janela das {}h: Encontrados {} pacientes PENDING sem resposta elegíveis para cobrança.", currentHour, candidateSessions.size());

        boolean hasSentBefore = false;
        Set<UUID> processedGroups = new HashSet<>();

        // --- 3. REENVIO RECORRENTE DE NUDGES A CADA 2h ---
        for (AppointmentSession session : candidateSessions) {
            if (session.getCurrentGroupId() != null) {
                // FLUXO DE GRUPO
                UUID groupId = session.getCurrentGroupId();
                if (processedGroups.contains(groupId)) {
                    continue;
                }

                if (session.getLastNotificationSentAt() != null && !session.getLastNotificationSentAt().isBefore(pendingThreshold)) {
                    continue;
                }

                processedGroups.add(groupId);

                if (hasSentBefore) {
                    aplicarPacingDelay();
                } else {
                    hasSentBefore = true;
                }
                processGroupNudge(groupId);
            } else {
                // FLUXO INDIVIDUAL
                if (session.getLastNotificationSentAt() != null && !session.getLastNotificationSentAt().isBefore(pendingThreshold)) {
                    continue;
                }

                if (hasSentBefore) {
                    aplicarPacingDelay();
                } else {
                    hasSentBefore = true;
                }
                processIndividualNudge(session);
            }
        }

        log.info("Monitor de nudges recorrentes executado com sucesso. Candidatos={}, Grupos Processados={}",
                candidateSessions.size(), processedGroups.size());
    }

    private void processIndividualNudge(AppointmentSession session) {
        boolean shouldSend = Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            AppointmentSession lockedSession = appointmentSessionRepository.findByIdLocked(session.getId()).orElse(null);
            if (lockedSession != null && isStatusEligibleForNudge(lockedSession.getStatus())) {
                if (blipContextService.hasActiveTicket(lockedSession.getPhoneNumber(), lockedSession.getLastNotificationSentAt())) {
                    log.info("[ATTENDANCE-GUARD] Abortando/pausando nudge recorrente para {} devido a ticket de live chat ativo no Blip.", lockedSession.getPhoneNumber());
                    lockedSession.setLastNotificationSentAt(LocalDateTime.now(SAO_PAULO_ZONE));
                    lockedSession.setLastInteractionAt(LocalDateTime.now(SAO_PAULO_ZONE));
                    appointmentSessionRepository.save(lockedSession);
                    return false;
                }

                // --- RE-VALIDAÇÃO PREVENTIVA NO FEEGOW ERP ANTES DO ENVIO ---
                if (lockedSession.getFeegowAppointmentId() != null && !lockedSession.getFeegowAppointmentId().isBlank()) {
                    try {
                        FeegowAppointment feegowAppt = appointmentExternalPort.findById(lockedSession.getFeegowAppointmentId());
                        if (feegowAppt != null) {
                            String statusId = feegowAppt.statusId();
                            // Se no Feegow o agendamento não estiver mais com status_id == 1 (Marcado)
                            if (statusId != null && !"1".equals(statusId.trim())) {
                                AppointmentSessionStatus newStatus = "7".equals(statusId.trim())
                                        ? AppointmentSessionStatus.CONFIRMED
                                        : AppointmentSessionStatus.CANCELED;
                                log.info("[NUDGE-GUARD] Agendamento Feegow ID {} possui status '{}' no ERP (diferente de 1 Marcado). Atualizando sessão local para {} e cancelando envio de lembrete.",
                                        lockedSession.getFeegowAppointmentId(), statusId, newStatus);
                                lockedSession.setStatus(newStatus);
                                lockedSession.setClosedAt(LocalDateTime.now(SAO_PAULO_ZONE));
                                appointmentSessionRepository.save(lockedSession);
                                return false;
                            }

                            // Se a data/hora da consulta tiver mudado no Feegow (reagendamento efetuado pela clínica)
                            if (lockedSession.getAppointmentAt() != null && feegowAppt.startAt() != null) {
                                if (!lockedSession.getAppointmentAt().isEqual(feegowAppt.startAt())) {
                                    log.info("[NUDGE-GUARD] Agendamento Feegow ID {} foi reagendado no ERP (antigo: {}, novo: {}). Atualizando sessão local para ALTERATION_REQUESTED e cancelando envio de lembrete.",
                                            lockedSession.getFeegowAppointmentId(), lockedSession.getAppointmentAt(), feegowAppt.startAt());
                                    lockedSession.setStatus(AppointmentSessionStatus.ALTERATION_REQUESTED);
                                    lockedSession.setClosedAt(LocalDateTime.now(SAO_PAULO_ZONE));
                                    appointmentSessionRepository.save(lockedSession);
                                    return false;
                                }
                            }
                        }
                    } catch (Exception ex) {
                        log.warn("[NUDGE-GUARD] Falha na re-validação do agendamento Feegow ID {}: {}. Prosseguindo com o envio.",
                                lockedSession.getFeegowAppointmentId(), ex.getMessage());
                    }
                }

                // Mantém o status como PENDING e apenas atualiza a data/hora do envio
                lockedSession.setStatus(AppointmentSessionStatus.PENDING);
                lockedSession.setLastNotificationSentAt(LocalDateTime.now(SAO_PAULO_ZONE));
                lockedSession.setLastInteractionAt(LocalDateTime.now(SAO_PAULO_ZONE));
                appointmentSessionRepository.save(lockedSession);
                return true;
            }
            return false;
        }));

        if (shouldSend) {
            AppointmentSession activeSession = transactionTemplate.execute(status ->
                appointmentSessionRepository.findById(session.getId()).orElse(null)
            );
            if (activeSession != null) {
                log.info("[NUDGE-SEND] Enviando nudge recorrente para paciente ID {} (Último envio: {}).",
                        activeSession.getId(), activeSession.getLastNotificationSentAt());
                boolean sent = sendAppointmentTemplateUseCase.execute(activeSession, AppointmentCategory.NUDGE_1);
                if (!sent) {
                    log.warn("Template de nudge recorrente não enviado. Sessão mantida em PENDING para próxima tentativa. sessionId={}", activeSession.getId());
                }
            }
        }
    }

    private void processGroupNudge(UUID groupId) {
        boolean shouldSend = Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            List<AppointmentSession> groupSessions = appointmentSessionRepository.findByCurrentGroupId(groupId);
            if (groupSessions == null || groupSessions.isEmpty()) {
                return false;
            }

            boolean allEligible = groupSessions.stream().allMatch(s -> isStatusEligibleForNudge(s.getStatus()));
            if (!allEligible) {
                log.info("[GRUPO-NUDGE] Grupo {} possui sessões em status não elegível para nudge recorrente. Abortando.", groupId);
                return false;
            }

            String phoneNumber = groupSessions.get(0).getPhoneNumber();
            LocalDateTime lastNotificationSentAt = groupSessions.get(0).getLastNotificationSentAt();
            if (blipContextService.hasActiveTicket(phoneNumber, lastNotificationSentAt)) {
                log.info("[ATTENDANCE-GUARD] Abortando/pausando nudge recorrente de grupo para {} devido a ticket de live chat ativo no Blip.", phoneNumber);
                for (AppointmentSession s : groupSessions) {
                    AppointmentSession locked = appointmentSessionRepository.findByIdLocked(s.getId()).orElse(s);
                    locked.setLastNotificationSentAt(LocalDateTime.now(SAO_PAULO_ZONE));
                    locked.setLastInteractionAt(LocalDateTime.now(SAO_PAULO_ZONE));
                    appointmentSessionRepository.save(locked);
                }
                return false;
            }

            for (AppointmentSession s : groupSessions) {
                if (s.getFeegowAppointmentId() != null && !s.getFeegowAppointmentId().isBlank()) {
                    try {
                        FeegowAppointment feegowAppt = appointmentExternalPort.findById(s.getFeegowAppointmentId());
                        if (feegowAppt != null) {
                            String statusId = feegowAppt.statusId();
                            if (statusId != null && !"1".equals(statusId.trim())) {
                                AppointmentSessionStatus newStatus = "7".equals(statusId.trim())
                                        ? AppointmentSessionStatus.CONFIRMED
                                        : AppointmentSessionStatus.CANCELED;
                                log.info("[GRUPO-NUDGE-GUARD] Agendamento Feegow ID {} do grupo {} possui status '{}'. Atualizando sessão para {} e abortando envio.",
                                        s.getFeegowAppointmentId(), groupId, statusId, newStatus);
                                AppointmentSession locked = appointmentSessionRepository.findByIdLocked(s.getId()).orElse(s);
                                locked.setStatus(newStatus);
                                locked.setClosedAt(LocalDateTime.now(SAO_PAULO_ZONE));
                                appointmentSessionRepository.save(locked);
                                return false;
                            }

                            if (s.getAppointmentAt() != null && feegowAppt.startAt() != null) {
                                if (!s.getAppointmentAt().isEqual(feegowAppt.startAt())) {
                                    log.info("[GRUPO-NUDGE-GUARD] Agendamento Feegow ID {} do grupo {} foi reagendado (antigo: {}, novo: {}). Atualizando sessão para ALTERATION_REQUESTED e abortando envio.",
                                            s.getFeegowAppointmentId(), groupId, s.getAppointmentAt(), feegowAppt.startAt());
                                    AppointmentSession locked = appointmentSessionRepository.findByIdLocked(s.getId()).orElse(s);
                                    locked.setStatus(AppointmentSessionStatus.ALTERATION_REQUESTED);
                                    locked.setClosedAt(LocalDateTime.now(SAO_PAULO_ZONE));
                                    appointmentSessionRepository.save(locked);
                                    return false;
                                }
                            }
                        }
                    } catch (Exception ex) {
                        log.warn("[GRUPO-NUDGE-GUARD] Falha na re-validação do agendamento Feegow ID {}: {}", s.getFeegowAppointmentId(), ex.getMessage());
                    }
                }
            }

            for (AppointmentSession s : groupSessions) {
                AppointmentSession locked = appointmentSessionRepository.findByIdLocked(s.getId()).orElse(s);
                locked.setStatus(AppointmentSessionStatus.PENDING);
                locked.setLastNotificationSentAt(LocalDateTime.now(SAO_PAULO_ZONE));
                locked.setLastInteractionAt(LocalDateTime.now(SAO_PAULO_ZONE));
                appointmentSessionRepository.save(locked);
            }
            return true;
        }));

        if (shouldSend) {
            List<AppointmentSession> activeSessions = transactionTemplate.execute(status ->
                appointmentSessionRepository.findByCurrentGroupId(groupId)
            );
            if (activeSessions != null && !activeSessions.isEmpty()) {
                String phoneNumber = activeSessions.get(0).getPhoneNumber();
                String templateId = transactionTemplate.execute(status ->
                    appointmentConfigRepository.findByCategory(AppointmentCategory.GROUP_NUDGE_1)
                        .map(config -> config.getTemplateId())
                        .orElse(appointmentMotorProperties.getBlipTemplateNudgePending())
                );

                try {
                    log.info("[NUDGE-SEND] Enviando nudge recorrente de grupo para paciente ID {} (Último envio: {}).",
                            activeSessions.get(0).getId(), activeSessions.get(0).getLastNotificationSentAt());
                    log.info("[GRUPO-NUDGE] Enviando template de nudge recorrente '{}' para {}. groupId={}", templateId, phoneNumber, groupId);
                    blipNotificationService.sendGroupTemplateMessage(phoneNumber, templateId, groupId, null);
                } catch (Exception e) {
                    log.error("[GRUPO-NUDGE] Erro ao enviar template de nudge para {}. groupId={}", phoneNumber, groupId, e);
                }
            }
        }
    }

    private boolean isStatusEligibleForNudge(AppointmentSessionStatus status) {
        return status == AppointmentSessionStatus.PENDING
                || status == AppointmentSessionStatus.NUDGE_1_SENT
                || status == AppointmentSessionStatus.NUDGE_FINAL_SENT;
    }

    private void aplicarPacingDelay() {
        try {
            Thread.sleep(150);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private LocalDateTime resolvePendingThreshold(int xHours) {
        if (!appointmentMotorProperties.isTestMode()) {
            return LocalDateTime.now(SAO_PAULO_ZONE).minusMinutes(105);
        }

        LocalDateTime immediateThreshold = LocalDateTime.now(SAO_PAULO_ZONE).plusMinutes(1);
        log.warn("[TEST MODE ACTIVE] NUDGE recorrente liberado imediatamente. pendingThreshold={}", immediateThreshold);
        return immediateThreshold;
    }
}
