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
        
        UUID groupId;
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
        
        if (actionType == null) {
            return null;
        }

        switch (actionType) {
            case "ver_agenda" -> handleVerAgenda(groupId, fromPhone, bsuid);
            case "confirm_group" -> handleConfirmGroup(groupId);
            case "alter_group" -> handleAlterGroup(groupId, fromPhone);
            case "group_view" -> handleGroupView(groupId, fromPhone);
            case "group_view_fallback" -> handleGroupViewFallback(groupId, fromPhone);
            default -> {}
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

    private void handleConfirmGroup(UUID groupId) {
        log.info("[WEBHOOK] Interceptando confirm_group_{} para processamento síncrono e baixa real no Feegow.", groupId);
        List<NotificationGroup> groups = notificationGroupRepository.findByGroupId(groupId);
        if (groups != null) {
            for (NotificationGroup g : groups) {
                if (g != null && g.getSessionId() != null) {
                    appointmentSessionRepository.findById(g.getSessionId()).ifPresent(appt -> {
                        if (appt != null && appt.getFeegowAppointmentId() != null) {
                            log.info("[FEEGOW-BAIXA-GRUPO] Atualizando consulta ID={} para confirmado no Feegow devido ao groupId={}", appt.getId(), groupId);
                            try {
                                appointmentExternalPort.updateStatus(appt.getFeegowAppointmentId(), 7);
                                
                                transactionTemplate.executeWithoutResult(status -> {
                                    AppointmentSession lockedSession = appointmentSessionRepository.findByIdLocked(appt.getId()).orElse(null);
                                    if (lockedSession != null) {
                                        lockedSession.setStatus(br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSessionStatus.CONFIRMED);
                                        lockedSession.setClosedAt(java.time.LocalDateTime.now());
                                        lockedSession.setLastInteractionAt(java.time.LocalDateTime.now());
                                        appointmentSessionRepository.save(lockedSession);
                                    }
                                });
                            } catch (RuntimeException e) {
                                log.error("[FEEGOW-BAIXA-GRUPO] Erro ao atualizar status no Feegow para ID: {}. Erro: {}", appt.getFeegowAppointmentId(), e.getMessage(), e);
                            }
                        }
                    });
                }
            }
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
        if (groups == null || groups.isEmpty()) {
            log.warn("[WEBHOOK] Nenhum NotificationGroup encontrado para o groupId={}", groupId);
            return;
        }

        String listaDetalhada = "";
        for (NotificationGroup g : groups) {
            if (g.getPreCompiledScheduleText() != null && !g.getPreCompiledScheduleText().isBlank()) {
                listaDetalhada = g.getPreCompiledScheduleText();
                break;
            }
        }

        if (listaDetalhada.isBlank()) {
            log.warn("[WEBHOOK] preCompiledScheduleText vazio para o groupId={}", groupId);
        }

        blipContextService.setUserContextForUser(fromPhone.trim(), "lista_detalhada", listaDetalhada);
        blipContextService.setUserContextForUser(fromPhone.trim(), "groupId", groupId.toString());
        log.info("[WEBHOOK] Injetada lista_detalhada pré-compilada e groupId={} para {}.", groupId, fromPhone);
    }

    private UUID parseUuid(String str) {
        try {
            return UUID.fromString(str);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
