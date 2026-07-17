package br.dev.ctrls.inovareti.modules.appointment.application.usecase;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentCategory;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentConfigRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.AppointmentMotorProperties;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSession;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentSessionRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSessionStatus;
import br.dev.ctrls.inovareti.modules.appointment.application.service.ConfirmationStateMachineService;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentExternalPort;
import br.dev.ctrls.inovareti.modules.appointment.application.service.BlipContextService;
import br.dev.ctrls.inovareti.modules.appointment.application.service.BlipNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class MonitorAppointmentNudgesUseCase {


    private final AppointmentSessionRepositoryPort appointmentSessionRepository;
    private final AppointmentConfigRepositoryPort appointmentConfigRepository;
    private final AppointmentMotorProperties appointmentMotorProperties;
    private final SendAppointmentTemplateUseCase sendAppointmentTemplateUseCase;
    private final ConfirmationStateMachineService confirmationStateMachineService;
    private final AppointmentExternalPort appointmentExternalPort;
    private final BlipContextService blipContextService;
    private final BlipNotificationService blipNotificationService;
    private final TransactionTemplate transactionTemplate;

    @Transactional
    public void execute() {
        // --- 1. RESOLVER CONFIGURAÇÕES E TIMINGS ---
        // Individual Timings
        int nudge1Hours = appointmentConfigRepository.findByCategory(AppointmentCategory.NUDGE_1)
                .map(config -> config.getTimingHours())
                .orElse(appointmentMotorProperties.getNudge1WaitHours());

        int nudgeFinalHours = appointmentConfigRepository.findByCategory(AppointmentCategory.NUDGE_FINAL)
                .map(config -> config.getTimingHours())
                .orElse(appointmentMotorProperties.getNudgeFinalWaitHours());

        // Group Timings
        int groupNudge1Hours = appointmentConfigRepository.findByCategory(AppointmentCategory.GROUP_NUDGE_1)
                .map(config -> config.getTimingHours())
                .orElse(4);

        int groupNudgeFinalHours = appointmentConfigRepository.findByCategory(AppointmentCategory.GROUP_NUDGE_FINAL)
                .map(config -> config.getTimingHours())
                .orElse(24);

        // Thresholds individuais
        LocalDateTime pendingThreshold = resolvePendingThreshold(nudge1Hours);
        LocalDateTime nudge1Threshold = resolveNudge1Threshold(nudgeFinalHours);
        LocalDateTime finalThreshold = resolveFinalThreshold();

        // Thresholds de grupo
        LocalDateTime groupPendingThreshold = resolvePendingThreshold(groupNudge1Hours);
        LocalDateTime groupNudge1Threshold = resolveNudge1Threshold(groupNudgeFinalHours);

        // Threshold de busca no BD (o mais recente/curto dos dois para carregar todos os candidatos)
        LocalDateTime queryPendingThreshold = pendingThreshold.isAfter(groupPendingThreshold) ? pendingThreshold : groupPendingThreshold;
        LocalDateTime queryNudge1Threshold = nudge1Threshold.isAfter(groupNudge1Threshold) ? nudge1Threshold : groupNudge1Threshold;

        // --- 2. BUSCAR SESSÕES CANDIDATAS DO BANCO ---
        List<AppointmentSession> pendingSessions = appointmentSessionRepository
                .findByStatusAndLastNotificationSentAtBefore(AppointmentSessionStatus.PENDING, queryPendingThreshold);
        List<AppointmentSession> nudge1Sessions = appointmentSessionRepository
                .findByStatusAndLastNotificationSentAtBefore(AppointmentSessionStatus.NUDGE_1_SENT, queryNudge1Threshold);
        List<AppointmentSession> finalSessions = appointmentSessionRepository
                .findByStatusAndLastNotificationSentAtBefore(AppointmentSessionStatus.NUDGE_FINAL_SENT, finalThreshold);

        boolean hasSentBefore = false;

        // --- 3. FLUXO 1: PENDING -> NUDGE_1_SENT ---
        Set<UUID> processedGroup1 = new HashSet<>();
        for (AppointmentSession session : pendingSessions) {
            if (session.getCurrentGroupId() != null) {
                // FLUXO DE GRUPO
                UUID groupId = session.getCurrentGroupId();
                if (processedGroup1.contains(groupId)) {
                    continue;
                }
                
                // Verifica data limite de grupo
                if (session.getLastNotificationSentAt() != null && !session.getLastNotificationSentAt().isBefore(groupPendingThreshold)) {
                    continue;
                }

                processedGroup1.add(groupId);
                
                if (hasSentBefore) {
                    aplicarPacingDelay();
                } else {
                    hasSentBefore = true;
                }
                processGroupNudge(groupId, AppointmentCategory.GROUP_NUDGE_1, AppointmentSessionStatus.PENDING);
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
                processIndividualNudge(session, AppointmentCategory.NUDGE_1, AppointmentSessionStatus.PENDING);
            }
        }

        // --- 4. FLUXO 2: NUDGE_1_SENT -> NUDGE_FINAL_SENT ---
        Set<UUID> processedGroupFinal = new HashSet<>();
        for (AppointmentSession session : nudge1Sessions) {
            if (session.getCurrentGroupId() != null) {
                // FLUXO DE GRUPO
                UUID groupId = session.getCurrentGroupId();
                if (processedGroupFinal.contains(groupId)) {
                    continue;
                }

                // Verifica data limite de grupo
                if (session.getLastNotificationSentAt() != null && !session.getLastNotificationSentAt().isBefore(groupNudge1Threshold)) {
                    continue;
                }

                processedGroupFinal.add(groupId);
                
                if (hasSentBefore) {
                    aplicarPacingDelay();
                } else {
                    hasSentBefore = true;
                }
                processGroupNudge(groupId, AppointmentCategory.GROUP_NUDGE_FINAL, AppointmentSessionStatus.NUDGE_1_SENT);
            } else {
                // FLUXO INDIVIDUAL
                if (session.getLastNotificationSentAt() != null && !session.getLastNotificationSentAt().isBefore(nudge1Threshold)) {
                    continue;
                }
                
                if (hasSentBefore) {
                    aplicarPacingDelay();
                } else {
                    hasSentBefore = true;
                }
                processIndividualNudge(session, AppointmentCategory.NUDGE_FINAL, AppointmentSessionStatus.NUDGE_1_SENT);
            }
        }

        // --- 5. FLUXO 3: NUDGE_FINAL_SENT -> CANCELED_NO_RESPONSE ---
        Set<UUID> processedGroupCancel = new HashSet<>();
        for (AppointmentSession session : finalSessions) {
            if (session.getCurrentGroupId() != null) {
                // FLUXO DE GRUPO
                UUID groupId = session.getCurrentGroupId();
                if (processedGroupCancel.contains(groupId)) {
                    continue;
                }

                processedGroupCancel.add(groupId);
                
                if (hasSentBefore) {
                    aplicarPacingDelay();
                } else {
                    hasSentBefore = true;
                }
                processGroupCancel(groupId);
            } else {
                // FLUXO INDIVIDUAL
                if (hasSentBefore) {
                    aplicarPacingDelay();
                } else {
                    hasSentBefore = true;
                }
                processIndividualCancel(session);
            }
        }

        log.info("Monitor de nudges executado. pendingParaNudge1={}, nudge1ParaFinal={}, finalParaCancelado={}",
                pendingSessions.size(), nudge1Sessions.size(), finalSessions.size());
    }

    private void processIndividualNudge(AppointmentSession session, AppointmentCategory category, AppointmentSessionStatus expectedStatus) {
        boolean shouldSend = transactionTemplate.execute(status -> {
            AppointmentSession lockedSession = appointmentSessionRepository.findByIdLocked(session.getId()).orElse(null);
            if (lockedSession != null && lockedSession.getStatus() == expectedStatus) {
                if (blipContextService.hasActiveTicket(lockedSession.getPhoneNumber())) {
                    log.info("[ATTENDANCE-GUARD] Abortando/pausando nudge para {} devido a ticket de live chat ativo no Blip.", lockedSession.getPhoneNumber());
                    lockedSession.setLastNotificationSentAt(LocalDateTime.now());
                    lockedSession.setLastInteractionAt(LocalDateTime.now());
                    appointmentSessionRepository.save(lockedSession);
                    return false;
                }
                if (category == AppointmentCategory.NUDGE_1) {
                    confirmationStateMachineService.markNudge1Sent(lockedSession);
                } else {
                    confirmationStateMachineService.markNudgeFinalSent(lockedSession);
                }
                appointmentSessionRepository.save(lockedSession);
                return true;
            }
            return false;
        });

        if (shouldSend) {
            AppointmentSession activeSession = transactionTemplate.execute(status ->
                appointmentSessionRepository.findById(session.getId()).orElse(null)
            );
            if (activeSession != null) {
                boolean sent = sendAppointmentTemplateUseCase.execute(activeSession, category);
                if (!sent) {
                    log.warn("Nudge não enviado. Revertendo status da sessão para o estado anterior. sessionId={}, category={}", activeSession.getId(), category);
                    transactionTemplate.executeWithoutResult(status -> {
                        AppointmentSession locked = appointmentSessionRepository.findByIdLocked(session.getId()).orElse(null);
                        if (locked != null) {
                            locked.setStatus(expectedStatus);
                            appointmentSessionRepository.save(locked);
                        }
                    });
                }
            }
        }
    }

    private void processGroupNudge(UUID groupId, AppointmentCategory category, AppointmentSessionStatus expectedStatus) {
        boolean shouldSend = transactionTemplate.execute(status -> {
            List<AppointmentSession> groupSessions = appointmentSessionRepository.findByCurrentGroupId(groupId);
            if (groupSessions == null || groupSessions.isEmpty()) {
                return false;
            }

            boolean allInExpectedState = groupSessions.stream().allMatch(s -> s.getStatus() == expectedStatus);
            if (!allInExpectedState) {
                log.info("[GRUPO-NUDGE] Grupo {} possui sessões em status divergente. Abortando nudge.", groupId);
                return false;
            }

            String phoneNumber = groupSessions.get(0).getPhoneNumber();
            if (blipContextService.hasActiveTicket(phoneNumber)) {
                log.info("[ATTENDANCE-GUARD] Abortando/pausando nudge de grupo para {} devido a ticket de live chat ativo no Blip.", phoneNumber);
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
                if (category == AppointmentCategory.GROUP_NUDGE_1) {
                    confirmationStateMachineService.markNudge1Sent(locked);
                } else {
                    confirmationStateMachineService.markNudgeFinalSent(locked);
                }
                appointmentSessionRepository.save(locked);
            }
            return true;
        });

        if (shouldSend) {
            List<AppointmentSession> activeSessions = transactionTemplate.execute(status ->
                appointmentSessionRepository.findByCurrentGroupId(groupId)
            );
            if (activeSessions != null && !activeSessions.isEmpty()) {
                String phoneNumber = activeSessions.get(0).getPhoneNumber();
                String templateId = transactionTemplate.execute(status ->
                    appointmentConfigRepository.findByCategory(category)
                        .map(config -> config.getTemplateId())
                        .orElse(appointmentMotorProperties.getBlipTemplateNudgePending())
                );

                try {
                    log.info("[GRUPO-NUDGE] Enviando template de nudge '{}' para {}. groupId={}, category={}", templateId, phoneNumber, groupId, category);
                    blipNotificationService.sendGroupTemplateMessage(phoneNumber, templateId, groupId, null);
                } catch (Exception e) {
                    log.error("[GRUPO-NUDGE] Erro ao enviar template de nudge para {}. Revertendo status do grupo. groupId={}", phoneNumber, groupId, e);
                    transactionTemplate.executeWithoutResult(status -> {
                        List<AppointmentSession> groupSessions = appointmentSessionRepository.findByCurrentGroupId(groupId);
                        if (groupSessions != null) {
                            for (AppointmentSession s : groupSessions) {
                                AppointmentSession locked = appointmentSessionRepository.findByIdLocked(s.getId()).orElse(s);
                                locked.setStatus(expectedStatus);
                                appointmentSessionRepository.save(locked);
                            }
                        }
                    });
                }
            }
        }
    }

    private void processIndividualCancel(AppointmentSession session) {
        transactionTemplate.executeWithoutResult(status -> {
            AppointmentSession lockedSession = appointmentSessionRepository.findByIdLocked(session.getId()).orElse(null);
            if (lockedSession != null && lockedSession.getStatus() == AppointmentSessionStatus.NUDGE_FINAL_SENT) {
                if (blipContextService.hasActiveTicket(lockedSession.getPhoneNumber())) {
                    log.info("[ATTENDANCE-GUARD] Abortando/pausando CANCELAMENTO automático para {} devido a ticket de live chat ativo no Blip.", lockedSession.getPhoneNumber());
                    lockedSession.setLastNotificationSentAt(LocalDateTime.now());
                    lockedSession.setLastInteractionAt(LocalDateTime.now());
                    appointmentSessionRepository.save(lockedSession);
                    return;
                }
                try {
                    appointmentExternalPort.cancelAppointment(lockedSession.getFeegowAppointmentId(), "Cancelado automaticamente por falta de resposta às mensagens de confirmação.");
                } catch (Exception e) {
                    log.error("Erro ao cancelar agendamento na Feegow por falta de resposta. sessionId={}", lockedSession.getId(), e);
                }

                boolean sent = sendAppointmentTemplateUseCase.executeSimpleTemplate(lockedSession, "aviso_final_cancelamento");
                if (!sent) {
                    log.warn("Template de cancelamento automático não enviado. sessionId={}", lockedSession.getId());
                }

                confirmationStateMachineService.markCanceledByNoResponse(lockedSession);
                appointmentSessionRepository.save(lockedSession);
                blipContextService.setMasterState(lockedSession.getPhoneNumber(), appointmentMotorProperties.getBlipBuilderBotId(), "builder");
            }
        });
    }

    private void processGroupCancel(UUID groupId) {
        transactionTemplate.executeWithoutResult(status -> {
            List<AppointmentSession> groupSessions = appointmentSessionRepository.findByCurrentGroupId(groupId);
            if (groupSessions == null || groupSessions.isEmpty()) {
                return;
            }

            boolean allInNudgeFinal = groupSessions.stream().allMatch(s -> s.getStatus() == AppointmentSessionStatus.NUDGE_FINAL_SENT);
            if (!allInNudgeFinal) {
                log.info("[GRUPO-CANCEL] Grupo {} possui sessões em status divergente do esperado. Abortando cancelamento.", groupId);
                return;
            }

            String phoneNumber = groupSessions.get(0).getPhoneNumber();
            if (blipContextService.hasActiveTicket(phoneNumber)) {
                log.info("[ATTENDANCE-GUARD] Abortando/pausando CANCELAMENTO automático de grupo para {} devido a ticket de live chat ativo no Blip.", phoneNumber);
                for (AppointmentSession s : groupSessions) {
                    AppointmentSession locked = appointmentSessionRepository.findByIdLocked(s.getId()).orElse(s);
                    locked.setLastNotificationSentAt(LocalDateTime.now());
                    locked.setLastInteractionAt(LocalDateTime.now());
                    appointmentSessionRepository.save(locked);
                }
                return;
            }

            // Desmarca todos os agendamentos do grupo na Feegow
            for (AppointmentSession s : groupSessions) {
                try {
                    log.info("[GRUPO-CANCEL] Desmarcando consulta na Feegow: appointmentId={}", s.getFeegowAppointmentId());
                    appointmentExternalPort.cancelAppointment(s.getFeegowAppointmentId(), "Cancelado automaticamente por falta de resposta às mensagens de confirmação.");
                } catch (Exception e) {
                    log.error("[GRUPO-CANCEL] Erro ao cancelar consulta {} no Feegow.", s.getFeegowAppointmentId(), e);
                }
            }

            // Envia um único template de aviso final de cancelamento simples
            AppointmentSession representativeSession = groupSessions.get(0);
            boolean sent = sendAppointmentTemplateUseCase.executeSimpleTemplate(representativeSession, "aviso_final_cancelamento");
            if (!sent) {
                log.warn("[GRUPO-CANCEL] Template de cancelamento automático não enviado para o grupo={}", groupId);
            }

            // Promove localmente todas as sessões para canceladas e restaura o estado do Master State
            for (AppointmentSession s : groupSessions) {
                AppointmentSession locked = appointmentSessionRepository.findByIdLocked(s.getId()).orElse(s);
                confirmationStateMachineService.markCanceledByNoResponse(locked);
                appointmentSessionRepository.save(locked);
            }

            try {
                blipContextService.setMasterState(phoneNumber, appointmentMotorProperties.getBlipBuilderBotId(), "builder");
                log.info("[GRUPO-CANCEL] Master-State do paciente {} restaurado para o Builder principal.", phoneNumber);
            } catch (Exception e) {
                log.error("[GRUPO-CANCEL] Erro ao restaurar Master-State para {}", phoneNumber, e);
            }
        });
    }

    private void aplicarPacingDelay() {
        long delayMillis = java.util.concurrent.ThreadLocalRandom.current().nextLong(150, 301);
        log.info("[PACING-LOG] Aplicando espaçamento temporal controlado de {} milissegundos nas notificações de nudges/cancelamento para respeitar o Rate Limit do Blip.", delayMillis);
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[PACING-LOG] O delay de espaçamento de nudges foi interrompido: {}", e.getMessage());
        }
    }

    private LocalDateTime resolvePendingThreshold(int xHours) {
        if (!appointmentMotorProperties.isTestMode()) {
            return LocalDateTime.now().minusHours(xHours);
        }

        // Em modo de teste, libera NUDGE_1 imediatamente para facilitar validação com consulta de hoje/amanhã.
        LocalDateTime immediateThreshold = LocalDateTime.now().plusMinutes(1);
        log.warn("[TEST MODE ACTIVE] NUDGE_1 liberado imediatamente. pendingThreshold={}", immediateThreshold);
        return immediateThreshold;
    }

    private LocalDateTime resolveNudge1Threshold(int yHours) {
        if (!appointmentMotorProperties.isTestMode()) {
            return LocalDateTime.now().minusHours(yHours);
        }

        LocalDateTime immediateThreshold = LocalDateTime.now().plusMinutes(1);
        log.warn("[TEST MODE ACTIVE] NUDGE_2 liberado imediatamente. nudge1Threshold={}", immediateThreshold);
        return immediateThreshold;
    }

    private LocalDateTime resolveFinalThreshold() {
        if (!appointmentMotorProperties.isTestMode()) {
            return LocalDateTime.now().minusHours(24);
        }

        LocalDateTime immediateThreshold = LocalDateTime.now().plusMinutes(1);
        log.warn("[TEST MODE ACTIVE] CANCELAMENTO liberado imediatamente. finalThreshold={}", immediateThreshold);
        return immediateThreshold;
    }
}
