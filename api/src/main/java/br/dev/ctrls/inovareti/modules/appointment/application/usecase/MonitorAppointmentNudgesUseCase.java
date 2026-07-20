package br.dev.ctrls.inovareti.modules.appointment.application.usecase;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class MonitorAppointmentNudgesUseCase {

    private final AppointmentSessionRepositoryPort appointmentSessionRepository;
    private final AppointmentConfigRepositoryPort appointmentConfigRepository;
    private final AppointmentMotorProperties appointmentMotorProperties;
    private final SendAppointmentTemplateUseCase sendAppointmentTemplateUseCase;
    private final BlipContextService blipContextService;
    private final BlipNotificationService blipNotificationService;
    private final TransactionTemplate transactionTemplate;

    @Transactional
    public void execute() {
        // --- 1. RESOLVER TIMING DE REENVIO RECORRENTE (Padrão: 2 horas) ---
        int nudgeHours = appointmentConfigRepository.findByCategory(AppointmentCategory.NUDGE_1)
                .map(config -> config.getTimingHours())
                .orElse(appointmentMotorProperties.getNudge1WaitHours());

        LocalDateTime pendingThreshold = resolvePendingThreshold(nudgeHours);
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();

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
                    lockedSession.setLastNotificationSentAt(LocalDateTime.now());
                    lockedSession.setLastInteractionAt(LocalDateTime.now());
                    appointmentSessionRepository.save(lockedSession);
                    return false;
                }

                // Mantém o status como PENDING e apenas atualiza a data/hora do envio
                lockedSession.setStatus(AppointmentSessionStatus.PENDING);
                lockedSession.setLastNotificationSentAt(LocalDateTime.now());
                lockedSession.setLastInteractionAt(LocalDateTime.now());
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
                    locked.setLastNotificationSentAt(LocalDateTime.now());
                    locked.setLastInteractionAt(LocalDateTime.now());
                    appointmentSessionRepository.save(locked);
                }
                return false;
            }

            for (AppointmentSession s : groupSessions) {
                AppointmentSession locked = appointmentSessionRepository.findByIdLocked(s.getId()).orElse(s);
                locked.setStatus(AppointmentSessionStatus.PENDING);
                locked.setLastNotificationSentAt(LocalDateTime.now());
                locked.setLastInteractionAt(LocalDateTime.now());
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
        // No-op: Blip rate limits are not an issue for notifications/nudges
    }

    private LocalDateTime resolvePendingThreshold(int xHours) {
        if (!appointmentMotorProperties.isTestMode()) {
            return LocalDateTime.now().minusHours(xHours);
        }

        LocalDateTime immediateThreshold = LocalDateTime.now().plusMinutes(1);
        log.warn("[TEST MODE ACTIVE] NUDGE recorrente liberado imediatamente. pendingThreshold={}", immediateThreshold);
        return immediateThreshold;
    }
}
