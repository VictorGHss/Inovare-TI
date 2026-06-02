package br.dev.ctrls.inovareti.modules.appointment.application.service;

import io.micrometer.observation.annotation.Observed;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSession;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentExternalPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * EstratÃ©gia de processamento especÃ­fica para a aÃ§Ã£o de cancelamento de consulta ("cancel").
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Observed
public class CancelBlipWebhookActionHandler implements BlipWebhookActionHandler {

    private final AppointmentExternalPort appointmentExternalPort;
    private final ConfirmationStateMachineService confirmationStateMachineService;

    @Override
    public boolean supports(String actionType) {
        return "cancel".equalsIgnoreCase(actionType);
    }

    @Override
    public void prePersistence(AppointmentSession session, String action) {
        log.info("[CANCEL] Paciente solicita cancelamento. Atualizando status na Feegow com cÃ³digo 100.");
        try {
            appointmentExternalPort.updateAppointmentStatus(session.getFeegowAppointmentId(), "100");
        } catch (RestClientException | IllegalStateException ex) {
            log.error(
                "[CANCEL] Falha ao atualizar status na Feegow para cancelado. appointmentId={}, erro={}",
                session.getFeegowAppointmentId(),
                ex.getMessage(),
                ex);
        }
    }

    @Override
    public void applySessionState(AppointmentSession session, String action) {
        confirmationStateMachineService.markCanceled(session);
    }
}


