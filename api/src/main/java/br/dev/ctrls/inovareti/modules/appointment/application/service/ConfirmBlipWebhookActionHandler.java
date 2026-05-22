package br.dev.ctrls.inovareti.modules.appointment.application.service;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSession;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentExternalPort;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.AppointmentMotorProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Estratégia de processamento específica para a ação de confirmação de consulta ("confirm").
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConfirmBlipWebhookActionHandler implements BlipWebhookActionHandler {

    private final AppointmentExternalPort appointmentExternalPort;
    private final AppointmentMotorProperties appointmentMotorProperties;
    private final ConfirmationStateMachineService confirmationStateMachineService;

    @Override
    public boolean supports(String actionType) {
        return "confirm".equalsIgnoreCase(actionType);
    }

    @Override
    public void prePersistence(AppointmentSession session) {
        String confirmedStatusId = resolveConfirmedStatusId();
        log.info("[CONFIRM] Atualizando status na Feegow com código {}.", confirmedStatusId);
        try {
            appointmentExternalPort.updateAppointmentStatus(session.getFeegowAppointmentId(), confirmedStatusId);
        } catch (RestClientException | IllegalStateException ex) {
            log.error(
                "[CONFIRM] Falha ao atualizar status na Feegow (continuando redirecionamento Blip). appointmentId={}, erro={}",
                session.getFeegowAppointmentId(),
                ex.getMessage(),
                ex);
        }
    }

    @Override
    public void applySessionState(AppointmentSession session) {
        confirmationStateMachineService.markConfirmed(session);
        log.info("[MENSAGERIA] Ação de {} processada com sucesso no banco e na Feegow. Navegação entregue ao Builder nativo.", "confirmação");
    }

    private String resolveConfirmedStatusId() {
        String configuredStatusId = appointmentMotorProperties.getFeegowConfirmedStatusId();
        if (configuredStatusId == null || configuredStatusId.isBlank()) {
            return "7";
        }
        String trimmed = configuredStatusId.trim();
        if ("2".equals(trimmed)) {
            return "7";
        }
        return trimmed;
    }
}
