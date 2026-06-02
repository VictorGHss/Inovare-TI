package br.dev.ctrls.inovareti.modules.appointment.application.service;

import io.micrometer.observation.annotation.Observed;

import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientException;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSession;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentExternalPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentSessionRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.BlipProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Componente responsÃ¡vel por gerenciar a resposta do usuÃ¡rio a mensagens de Nudge do Blip.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Observed
public class BlipNudgeResponseHandler {

    private final AppointmentSessionRepositoryPort appointmentSessionRepository;
    private final TransactionTemplate transactionTemplate;
    private final ConfirmationStateMachineService confirmationStateMachineService;
    private final AppointmentExternalPort appointmentExternalPort;
    private final BlipProperties blipProperties;
    private final BlipContextService blipContextService;
    private final BlipIdentityReconciler blipIdentityReconciler;

    /**
     * Intercepta a resposta do nudge do WhatsApp para manter ou cancelar a consulta,
     * executando os status na Feegow e redirecionando para suporte humano (Desk).
     */
    public boolean handleNudgeResponse(String normalizedAction, String action, String fromPhone, String bsuid) {
        boolean isManterAgendamento = normalizedAction.equals("manter agendamento")
                || normalizedAction.contains("manter_agendamento");
        boolean isCancelarConsulta = normalizedAction.equals("cancelar consulta")
                || normalizedAction.contains("cancelar_consulta");

        if (!isManterAgendamento && !isCancelarConsulta) {
            return false;
        }

        if (fromPhone != null && !fromPhone.isBlank()) {
            String dbPhone = blipIdentityReconciler.resolveAndReconcileIdentity(fromPhone, bsuid);
            log.info("[WEBHOOK-NUDGE] Interceptando resposta do nudge: '{}' para o telefone: {} (DB Phone: {})",
                action, fromPhone, dbPhone);
            
            List<AppointmentSession> activeSessions = transactionTemplate.execute(status -> 
                appointmentSessionRepository.findActiveByPhoneNumber(dbPhone)
            );
            
            if (activeSessions != null && !activeSessions.isEmpty()) {
                log.info("[WEBHOOK-NUDGE] Encontradas {} sessÃµes ativas para processar.", activeSessions.size());
                for (AppointmentSession session : activeSessions) {
                    processSessionUpdate(session, isManterAgendamento);
                }
                transferToDesk(fromPhone);
            } else {
                log.warn("[WEBHOOK-NUDGE] Resposta de Nudge '{}' recebida de {}, mas nenhuma sessÃ£o ativa encontrada.",
                    action, fromPhone);
            }
        } else {
            log.warn("[WEBHOOK-NUDGE] Resposta de Nudge '{}' recebida sem 'fromPhone' identificado.", action);
        }
        return true;
    }

    private void transferToDesk(String fromPhone) {
        String deskBlockId = blipProperties.getBlocks().getDeskStateId();
        blipContextService.setMasterState(fromPhone, "desk@msging.net", deskBlockId);
        log.info("[WEBHOOK-NUDGE] Transbordo concluÃ­do para {} direcionando ao Bloco: 'desk:{}'", fromPhone, deskBlockId);
    }

    private void processSessionUpdate(AppointmentSession session, boolean isManterAgendamento) {
        try {
            if (isManterAgendamento) {
                log.info("[WEBHOOK-NUDGE] Confirmando sessÃ£o local e Feegow para sessionId={}, feegowAppointmentId={}",
                    session.getId(), session.getFeegowAppointmentId());
                transactionTemplate.executeWithoutResult(status -> {
                    AppointmentSession lockedSession = appointmentSessionRepository.findByIdLocked(session.getId()).orElse(null);
                    if (lockedSession != null) {
                        confirmationStateMachineService.markConfirmed(lockedSession);
                        appointmentSessionRepository.save(lockedSession);
                    }
                });
                appointmentExternalPort.updateAppointmentStatus(session.getFeegowAppointmentId(), "7");
            } else {
                log.info("[WEBHOOK-NUDGE] Cancelando sessÃ£o local e Feegow para sessionId={}, feegowAppointmentId={}",
                    session.getId(), session.getFeegowAppointmentId());
                transactionTemplate.executeWithoutResult(status -> {
                    AppointmentSession lockedSession = appointmentSessionRepository.findByIdLocked(session.getId()).orElse(null);
                    if (lockedSession != null) {
                        confirmationStateMachineService.markCanceled(lockedSession);
                        appointmentSessionRepository.save(lockedSession);
                    }
                });
                appointmentExternalPort.updateStatus(session.getFeegowAppointmentId(), 11);
            }
        } catch (TransactionException | RestClientException | DataAccessException ex) {
            log.error("[WEBHOOK-NUDGE] Falha ao atualizar sessÃ£o no lote. sessionId={}, erro={}",
                session.getId(), ex.getMessage(), ex);
        }
    }
}


