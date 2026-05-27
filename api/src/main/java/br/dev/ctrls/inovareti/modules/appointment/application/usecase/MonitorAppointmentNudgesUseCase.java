package br.dev.ctrls.inovareti.modules.appointment.application.usecase;

import java.time.LocalDateTime;
import java.util.List;

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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class MonitorAppointmentNudgesUseCase {

    private static final int FEEGOW_STATUS_DESMARCADO = 6;

    private final AppointmentSessionRepositoryPort appointmentSessionRepository;
    private final AppointmentConfigRepositoryPort appointmentConfigRepository;
    private final AppointmentMotorProperties appointmentMotorProperties;
    private final SendAppointmentTemplateUseCase sendAppointmentTemplateUseCase;
    private final ConfirmationStateMachineService confirmationStateMachineService;
    private final AppointmentExternalPort appointmentExternalPort;
    private final BlipContextService blipContextService;
    private final TransactionTemplate transactionTemplate;

    public void execute() {
        int xHours = appointmentConfigRepository.findByCategory(AppointmentCategory.NUDGE_1)
                .map(config -> config.getTimingHours())
                .orElse(appointmentMotorProperties.getNudge1WaitHours());

        int yHours = appointmentConfigRepository.findByCategory(AppointmentCategory.NUDGE_FINAL)
                .map(config -> config.getTimingHours())
                .orElse(appointmentMotorProperties.getNudgeFinalWaitHours());

        LocalDateTime pendingThreshold = resolvePendingThreshold(xHours);
        LocalDateTime nudge1Threshold = resolveNudge1Threshold(yHours);
        LocalDateTime finalThreshold = resolveFinalThreshold();

        List<AppointmentSession> pendingSessions = appointmentSessionRepository
                .findByStatusAndLastNotificationSentAtBefore(AppointmentSessionStatus.PENDING, pendingThreshold);
        List<AppointmentSession> nudge1Sessions = appointmentSessionRepository
                .findByStatusAndLastNotificationSentAtBefore(AppointmentSessionStatus.NUDGE_1_SENT, nudge1Threshold);
        List<AppointmentSession> finalSessions = appointmentSessionRepository
                .findByStatusAndLastNotificationSentAtBefore(AppointmentSessionStatus.NUDGE_FINAL_SENT, finalThreshold);

        for (AppointmentSession session : pendingSessions) {
            transactionTemplate.executeWithoutResult(status -> {
                AppointmentSession lockedSession = appointmentSessionRepository.findByIdLocked(session.getId()).orElse(null);
                if (lockedSession != null && lockedSession.getStatus() == AppointmentSessionStatus.PENDING) {
                    boolean sent = sendAppointmentTemplateUseCase.execute(lockedSession, AppointmentCategory.NUDGE_1);
                    if (!sent) {
                        log.warn("NUDGE_1 não enviado. Mantendo sessão pendente. sessionId={}", lockedSession.getId());
                        return;
                    }
                    confirmationStateMachineService.markNudge1Sent(lockedSession);
                    appointmentSessionRepository.save(lockedSession);
                }
            });
        }

        for (AppointmentSession session : nudge1Sessions) {
            transactionTemplate.executeWithoutResult(status -> {
                AppointmentSession lockedSession = appointmentSessionRepository.findByIdLocked(session.getId()).orElse(null);
                if (lockedSession != null && lockedSession.getStatus() == AppointmentSessionStatus.NUDGE_1_SENT) {
                    boolean sent = sendAppointmentTemplateUseCase.execute(lockedSession, AppointmentCategory.NUDGE_FINAL);
                    if (!sent) {
                        log.warn("NUDGE_FINAL não enviado. Mantendo sessão no estado atual. sessionId={}", lockedSession.getId());
                        return;
                    }
                    confirmationStateMachineService.markNudgeFinalSent(lockedSession);
                    appointmentSessionRepository.save(lockedSession);
                }
            });
        }

        for (AppointmentSession session : finalSessions) {
            transactionTemplate.executeWithoutResult(status -> {
                AppointmentSession lockedSession = appointmentSessionRepository.findByIdLocked(session.getId()).orElse(null);
                if (lockedSession != null && lockedSession.getStatus() == AppointmentSessionStatus.NUDGE_FINAL_SENT) {
                    try {
                        appointmentExternalPort.updateStatus(lockedSession.getFeegowAppointmentId(), FEEGOW_STATUS_DESMARCADO);
                    } catch (Exception e) {
                        log.error("Erro ao atualizar status do agendamento na Feegow para desmarcado. sessionId={}", lockedSession.getId(), e);
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

        log.info("Monitor de nudges executado. pendingParaNudge1={}, nudge1ParaFinal={}, finalParaCancelado={}",
                pendingSessions.size(), nudge1Sessions.size(), finalSessions.size());
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
