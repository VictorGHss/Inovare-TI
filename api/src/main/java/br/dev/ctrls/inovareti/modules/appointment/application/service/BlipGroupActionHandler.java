package br.dev.ctrls.inovareti.modules.appointment.application.service;

import io.micrometer.observation.annotation.Observed;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSession;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.NotificationGroup;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentSessionRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.NotificationGroupRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Componente especialista na gestÃ£o de aÃ§Ãµes de grupos de consultas e fallback de visualizaÃ§Ã£o de agenda.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Observed
public class BlipGroupActionHandler {

    private final NotificationGroupRepositoryPort notificationGroupRepository;
    private final AppointmentSessionRepositoryPort appointmentSessionRepository;
    private final BlipAppointmentFormatter blipAppointmentFormatter;
    private final BlipContextService blipContextService;
    private final TransactionTemplate transactionTemplate;
    private final FeegowBulkIntegrationHandler feegowBulkIntegrationHandler;
    private final BlipIdentityReconciler blipIdentityReconciler;

    /**
     * Intercepta e processa as aÃ§Ãµes voltadas a agendamento de grupo.
     */
    public boolean handleGroupAction(String action, String fromPhone, String bsuid, Object metadata) {
        String lowerAction = action.toLowerCase();
        
        if (lowerAction.startsWith("ver_agenda_")) {
            handleVerAgenda(action, fromPhone, bsuid);
            return true;
        }
        if (lowerAction.startsWith("confirm_group_")) {
            handleConfirmGroup(action, fromPhone);
            return true;
        }
        if (lowerAction.startsWith("alter_group_")) {
            handleAlterGroup(action, fromPhone);
            return true;
        }
        if ("group_view_fallback".equalsIgnoreCase(action)) {
            handleGroupViewFallback(fromPhone, bsuid, metadata);
            return true;
        }
        if (lowerAction.startsWith("group_view_")) {
            handleGroupView(action, fromPhone);
            return true;
        }
        
        return false;
    }

    private void handleVerAgenda(String action, String fromPhone, String bsuid) {
        String groupIdStr = action.substring("ver_agenda_".length()).trim();
        log.info("[WEBHOOK] Clique em 'Ver Agendamentos' recebido para groupId={}", groupIdStr);
        try {
            UUID groupId = UUID.fromString(groupIdStr);
            if (fromPhone != null && !fromPhone.isBlank()) {
                String normalizedPhone = fromPhone.trim();
                String dbPhone = blipIdentityReconciler.resolveAndReconcileIdentity(fromPhone, bsuid);

                injectGroupSessionsContext(fromPhone, groupId);

                transactionTemplate.executeWithoutResult(status -> {
                    List<AppointmentSession> activeSessions =
                        appointmentSessionRepository.findActiveByPhoneNumber(dbPhone);
                    if (activeSessions == null || activeSessions.isEmpty()) {
                        log.warn("[WEBHOOK] Nenhuma sessao ativa encontrada para {} ao salvar groupId={}",
                            normalizedPhone, groupIdStr);
                        return;
                    }
                    for (AppointmentSession session : activeSessions) {
                        session.setCurrentGroupId(groupId);
                        appointmentSessionRepository.save(session);
                    }
                });
                log.info("[WEBHOOK] groupId salvo no banco para {}. groupId={}", normalizedPhone, groupIdStr);
            } else {
                log.warn("[WEBHOOK] fromPhone ausente ao salvar groupId={}", groupIdStr);
            }
        } catch (IllegalArgumentException e) {
            log.error("[WEBHOOK] groupId invÃ¡lido no payload ver_agenda_. action={}", action);
        }
    }

    private void handleConfirmGroup(String action, String fromPhone) {
        String groupIdStr = action.substring("confirm_group_".length()).trim();
        log.info("[WEBHOOK] Interceptando confirm_group_{} para processamento assÃ­ncrono em lote.", groupIdStr);
        try {
            UUID groupId = UUID.fromString(groupIdStr);
            feegowBulkIntegrationHandler.confirmGroupAsync(groupId, fromPhone);
        } catch (Exception e) {
            log.error("[WEBHOOK-BATCH] Erro ao agendar confirmaÃ§Ã£o assÃ­ncrona para grupo " + groupIdStr, e);
        }
    }

    private void handleAlterGroup(String action, String fromPhone) {
        String groupIdStr = action.substring("alter_group_".length()).trim();
        log.info("[WEBHOOK] Interceptando alter_group_{} para processamento assÃ­ncrono em lote.", groupIdStr);
        try {
            UUID groupId = UUID.fromString(groupIdStr);
            feegowBulkIntegrationHandler.alterGroupAsync(groupId, fromPhone);
        } catch (Exception e) {
            log.error("[WEBHOOK-BATCH] Erro ao agendar alteraÃ§Ã£o assÃ­ncrona para grupo " + groupIdStr, e);
        }
    }

    private void handleGroupViewFallback(String fromPhone, String bsuid, Object metadata) {
        log.info("[WEBHOOK] Interceptando group_view_fallback para resolucao de tunel.");
        String realPhone = null;
        if (metadata instanceof Map<?, ?> metadataMap) {
            Object origFrom = metadataMap.get("#tunnel.originalFrom");
            if (origFrom != null) {
                realPhone = origFrom.toString().trim();
                log.info("[WEBHOOK] Telefone real recuperado via #tunnel.originalFrom: {}", realPhone);
            }
        }
        
        if (realPhone == null || realPhone.isBlank()) {
            realPhone = fromPhone;
        }

        if (realPhone != null && !realPhone.isBlank()) {
            realPhone = blipIdentityReconciler.resolveAndReconcileIdentity(realPhone, bsuid);
        }
        
        log.info("[WEBHOOK] Telefone real purificado: {}", realPhone);

        if (realPhone != null && !realPhone.isBlank()) {
            final String targetPhone = realPhone;
            NotificationGroup latestGroup = transactionTemplate.execute(status ->
                notificationGroupRepository.findLatestByPhone(targetPhone).orElse(null)
            );

            if (latestGroup != null) {
                UUID groupId = latestGroup.getGroupId();
                log.info("[WEBHOOK] Ãšltimo grupo ativo encontrado: {} para telefone: {}", groupId, targetPhone);
                injectGroupSessionsContext(fromPhone, groupId);
            } else {
                log.warn("[WEBHOOK] Nenhum grupo ativo encontrado para o telefone real: {}", targetPhone);
            }
        } else {
            log.warn("[WEBHOOK] NÃ£o foi possÃ­vel resolver o telefone real do paciente.");
        }
    }

    private void handleGroupView(String action, String fromPhone) {
        String groupIdStr = action.substring("group_view_".length()).trim();
        log.info("[WEBHOOK] Clique em visualizaÃ§Ã£o de grupo recebido para groupId={}", groupIdStr);
        try {
            UUID groupId = UUID.fromString(groupIdStr);
            injectGroupSessionsContext(fromPhone, groupId);
        } catch (IllegalArgumentException e) {
            log.error("[WEBHOOK] groupId invÃ¡lido no payload group_view_. action={}", action);
        }
    }

    private void injectGroupSessionsContext(String fromPhone, UUID groupId) {
        if (fromPhone == null || fromPhone.isBlank()) {
            return;
        }
        List<NotificationGroup> groups = notificationGroupRepository.findByGroupId(groupId);
        List<AppointmentSession> groupedSessions = new ArrayList<>();
        for (NotificationGroup g : groups) {
            appointmentSessionRepository.findById(g.getSessionId()).ifPresent(groupedSessions::add);
        }

        groupedSessions.sort((s1, s2) -> {
            if (s1.getAppointmentAt() == null && s2.getAppointmentAt() == null) return 0;
            if (s1.getAppointmentAt() == null) return 1;
            if (s2.getAppointmentAt() == null) return -1;
            return s1.getAppointmentAt().compareTo(s2.getAppointmentAt());
        });

        String listaDetalhada = blipAppointmentFormatter.buildListaDetalhada(groupedSessions);
        blipContextService.setUserContextForUser(fromPhone.trim(), "lista_detalhada", listaDetalhada);
        blipContextService.setUserContextForUser(fromPhone.trim(), "groupId", groupId.toString());
        log.info("[WEBHOOK] Injetada lista_detalhada e groupId={} para {}.", groupId, fromPhone);
    }
}


