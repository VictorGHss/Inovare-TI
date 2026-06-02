package br.dev.ctrls.inovareti.modules.appointment.application.service;

import io.micrometer.observation.annotation.Observed;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import br.dev.ctrls.inovareti.modules.appointment.application.usecase.HandleBlipWebhookUseCase;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSession;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.NotificationGroup;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentExternalPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentSessionRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.NotificationGroupRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Componente especialista na gestão de ações de grupos de consultas e fallback de visualização de agenda.
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
    private final AppointmentExternalPort appointmentExternalPort;

    /**
     * Intercepta e processa as ações voltadas a agendamento de grupo.
     */
    public HandleBlipWebhookUseCase.WebhookResult handleGroupAction(String action, String fromPhone, String bsuid, Object metadata) {
        String lowerAction = action.toLowerCase();
        
        UUID groupId = null;
        String actionType = null;
        
        if (lowerAction.startsWith("ver_agenda_")) {
            actionType = "ver_agenda";
            groupId = parseUuid(action.substring("ver_agenda_".length()).trim());
        } else if (lowerAction.startsWith("confirm_group_")) {
            actionType = "confirm_group";
            groupId = parseUuid(action.substring("confirm_group_".length()).trim());
        } else if (lowerAction.startsWith("alter_group_")) {
            actionType = "alter_group";
            groupId = parseUuid(action.substring("alter_group_".length()).trim());
        } else if (lowerAction.startsWith("group_view_")) {
            actionType = "group_view";
            groupId = parseUuid(action.substring("group_view_".length()).trim());
        } else if ("group_view_fallback".equalsIgnoreCase(action)) {
            actionType = "group_view_fallback";
            groupId = resolveFallbackGroupId(fromPhone, bsuid, metadata);
        } else {
            groupId = parseUuid(action.trim());
            if (groupId != null) {
                actionType = "group_view";
            }
        }
        
        if (groupId == null) {
            return null;
        }

        // Buscar o grupo na tabela para validar existência
        List<NotificationGroup> groups = notificationGroupRepository.findByGroupId(groupId);
        if (groups == null || groups.isEmpty()) {
            log.warn("[GROUP-HANDLER] Grupo {} não encontrado no banco de dados para a ação {}.", groupId, action);
            return null;
        }
        
        if ("ver_agenda".equals(actionType)) {
            handleVerAgenda(groupId, fromPhone, bsuid);
        } else if ("confirm_group".equals(actionType)) {
            handleConfirmGroup(groupId, fromPhone);
        } else if ("alter_group".equals(actionType)) {
            handleAlterGroup(groupId, fromPhone);
        } else if ("group_view".equals(actionType)) {
            handleGroupView(groupId, fromPhone);
        } else if ("group_view_fallback".equals(actionType)) {
            handleGroupViewFallback(groupId, fromPhone);
        }
        
        return new HandleBlipWebhookUseCase.WebhookResult("", "", "", "", "group_action_processed", "");
    }

    private void handleVerAgenda(UUID groupId, String fromPhone, String bsuid) {
        log.info("[WEBHOOK] Clique em 'Ver Agendamentos' recebido para groupId={}", groupId);
        if (fromPhone != null && !fromPhone.isBlank()) {
            String normalizedPhone = fromPhone.trim();
            String dbPhone = blipIdentityReconciler.resolveAndReconcileIdentity(fromPhone, bsuid);

            injectGroupSessionsContext(fromPhone, groupId);

            transactionTemplate.executeWithoutResult(status -> {
                List<AppointmentSession> activeSessions =
                    appointmentSessionRepository.findActiveByPhoneNumber(dbPhone);
                if (activeSessions == null || activeSessions.isEmpty()) {
                    log.warn("[WEBHOOK] Nenhuma sessao ativa encontrada para {} ao salvar groupId={}",
                        normalizedPhone, groupId);
                    return;
                }
                for (AppointmentSession session : activeSessions) {
                    session.setCurrentGroupId(groupId);
                    appointmentSessionRepository.save(session);
                }
            });
            log.info("[WEBHOOK] groupId salvo no banco para {}. groupId={}", normalizedPhone, groupId);
        } else {
            log.warn("[WEBHOOK] fromPhone ausente ao salvar groupId={}", groupId);
        }
    }

    private void handleConfirmGroup(UUID groupId, String fromPhone) {
        log.info("[WEBHOOK] Interceptando confirm_group_{} para processamento assíncrono em lote.", groupId);
        try {
            feegowBulkIntegrationHandler.confirmGroupAsync(groupId, fromPhone);
        } catch (Exception e) {
            log.error("[WEBHOOK-BATCH] Erro ao agendar confirmação assíncrona para grupo " + groupId, e);
        }
    }

    private void handleAlterGroup(UUID groupId, String fromPhone) {
        log.info("[WEBHOOK] Interceptando alter_group_{} para processamento assíncrono em lote.", groupId);
        try {
            feegowBulkIntegrationHandler.alterGroupAsync(groupId, fromPhone);
        } catch (Exception e) {
            log.error("[WEBHOOK-BATCH] Erro ao agendar alteração assíncrona para grupo " + groupId, e);
        }
    }

    private UUID resolveFallbackGroupId(String fromPhone, String bsuid, Object metadata) {
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
                log.info("[WEBHOOK] Último grupo ativo encontrado: {} para telefone: {}", groupId, targetPhone);
                return groupId;
            } else {
                log.warn("[WEBHOOK] Nenhum grupo ativo encontrado para o telefone real: {}", targetPhone);
            }
        } else {
            log.warn("[WEBHOOK] Não foi possível resolver o telefone real do paciente.");
        }
        return null;
    }

    private void handleGroupViewFallback(UUID groupId, String fromPhone) {
        log.info("[WEBHOOK] Visualização de grupo com fallback executada para groupId={}", groupId);
        injectGroupSessionsContext(fromPhone, groupId);
    }

    private void handleGroupView(UUID groupId, String fromPhone) {
        log.info("[WEBHOOK] Clique em visualização de grupo recebido para groupId={}", groupId);
        injectGroupSessionsContext(fromPhone, groupId);
    }

    private void injectGroupSessionsContext(String fromPhone, UUID groupId) {
        if (fromPhone == null || fromPhone.isBlank()) {
            return;
        }
        List<NotificationGroup> groups = notificationGroupRepository.findByGroupId(groupId);
        List<AppointmentSession> groupedSessions = new ArrayList<>();
        for (NotificationGroup g : groups) {
            appointmentSessionRepository.findById(g.getSessionId()).ifPresent(session -> {
                // buscar os dados reais no Feegow (via 'FeegowAppointmentAdapter')
                try {
                    var realAppOpt = appointmentExternalPort.searchAppointments(
                        session.getAppointmentAt().toLocalDate(),
                        1, // pending status
                        session.getDoctorProfissionalId()
                    ).stream()
                     .filter(a -> a.id().equals(session.getFeegowAppointmentId()))
                     .findFirst();
                     
                    if (realAppOpt.isPresent()) {
                        var realApp = realAppOpt.get();
                        if (realApp.startAt() != null) {
                            session.setAppointmentAt(realApp.startAt());
                            appointmentSessionRepository.save(session);
                        }
                    }
                } catch (Exception ex) {
                    log.warn("Erro ao buscar dados reais do agendamento {} no Feegow para o grupo: {}", session.getFeegowAppointmentId(), ex.getMessage());
                }
                groupedSessions.add(session);
            });
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

    private UUID parseUuid(String str) {
        try {
            return UUID.fromString(str);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
