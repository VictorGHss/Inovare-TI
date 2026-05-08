package br.dev.ctrls.inovareti.domain.appointment.usecase;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.domain.appointment.AppointmentCategory;
import br.dev.ctrls.inovareti.domain.appointment.AppointmentConfigRepository;
import br.dev.ctrls.inovareti.domain.appointment.AppointmentMotorProperties;
import br.dev.ctrls.inovareti.domain.appointment.AppointmentSession;
import br.dev.ctrls.inovareti.domain.appointment.AppointmentSessionRepository;
import br.dev.ctrls.inovareti.domain.appointment.AppointmentSessionStatus;
import br.dev.ctrls.inovareti.domain.appointment.ConfirmationStateMachineService;
import br.dev.ctrls.inovareti.domain.appointment.FeegowClient;
import br.dev.ctrls.inovareti.domain.appointment.service.BlipContextService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class MonitorAppointmentNudgesUseCase {

    private static final int FEEGOW_STATUS_DESMARCADO = 6;

    private final AppointmentSessionRepository appointmentSessionRepository;
    private final AppointmentConfigRepository appointmentConfigRepository;
    private final AppointmentMotorProperties appointmentMotorProperties;
    private final SendAppointmentTemplateUseCase sendAppointmentTemplateUseCase;
    private final ConfirmationStateMachineService confirmationStateMachineService;
    private final FeegowClient feegowClient;
    private final BlipContextService blipContextService;

    @Transactional
    public void execute() {
        int xHours = appointmentConfigRepository.findByCategory(AppointmentCategory.NUDGE_1)
                .map(config -> config.getTimingHours())
                .orElse(appointmentMotorProperties.getNudge1WaitHours());

        int yHours = appointmentConfigRepository.findByCategory(AppointmentCategory.NUDGE_FINAL)
                .map(config -> config.getTimingHours())
                .orElse(appointmentMotorProperties.getNudgeFinalWaitHours());

        LocalDateTime pendingThreshold = resolvePendingThreshold(xHours);
        List<AppointmentSession> pendingSessions = appointmentSessionRepository
                .findByStatusAndLastInteractionAtBefore(AppointmentSessionStatus.PENDING, pendingThreshold);

        for (AppointmentSession session : pendingSessions) {
                        boolean sent = sendAppointmentTemplateUseCase.execute(session, AppointmentCategory.NUDGE_1);
                        if (!sent) {
                                log.warn("NUDGE_1 não enviado. Mantendo sessão pendente. sessionId={}", session.getId());
                                continue;
                        }

            confirmationStateMachineService.markNudge1Sent(session);
            appointmentSessionRepository.save(session);
        }

        LocalDateTime nudge1Threshold = LocalDateTime.now().minusHours(yHours);
        List<AppointmentSession> nudge1Sessions = appointmentSessionRepository
                .findByStatusAndLastInteractionAtBefore(AppointmentSessionStatus.NUDGE_1_SENT, nudge1Threshold);

        for (AppointmentSession session : nudge1Sessions) {
                        boolean sent = sendAppointmentTemplateUseCase.execute(session, AppointmentCategory.NUDGE_FINAL);
                        if (!sent) {
                                log.warn("NUDGE_FINAL não enviado. Mantendo sessão no estado atual. sessionId={}", session.getId());
                                continue;
                        }

            confirmationStateMachineService.markNudgeFinalSent(session);
            appointmentSessionRepository.save(session);
        }

        LocalDateTime finalThreshold = LocalDateTime.now().minusHours(24);
        List<AppointmentSession> finalSessions = appointmentSessionRepository
                .findByStatusAndLastInteractionAtBefore(AppointmentSessionStatus.NUDGE_FINAL_SENT, finalThreshold);

        for (AppointmentSession session : finalSessions) {
            feegowClient.updateStatus(session.getFeegowAppointmentId(), FEEGOW_STATUS_DESMARCADO);
            confirmationStateMachineService.markCanceledByNoResponse(session);
            appointmentSessionRepository.save(session);
            blipContextService.setMasterState(session.getPhoneNumber(), appointmentMotorProperties.getBlipBuilderBotId(), "builder");
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
}
