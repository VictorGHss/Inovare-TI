package br.dev.ctrls.inovareti.modules.appointment.application.service;

import io.micrometer.observation.annotation.Observed;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import br.dev.ctrls.inovareti.modules.appointment.application.usecase.HandleBlipWebhookUseCase;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSession;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.NotificationGroup;
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

    /**
     * Intercepta e processa as ações voltadas a agendamento de grupo.
     */
    public HandleBlipWebhookUseCase.WebhookResult handleGroupAction(String action, String fromPhone, String bsuid, Object metadata) {
        if (action == null || action.isBlank()) {
            return null;
        }
        
        String lowerAction = action.toLowerCase();

        // Filtro de relevância: ignora de imediato mensagens de fluxo técnico ou agradecimentos
        if (lowerAction.contains("agradecimento") || 
            lowerAction.contains("atendimento humano") || 
            lowerAction.contains("pesquisa") || 
            lowerAction.contains("obrigada") ||
            lowerAction.contains("obrigado")) {
            return null;
        }

        // Verifica se a ação corresponde a um evento legítimo do motor de grupo
        boolean isGroupAction = lowerAction.startsWith("confirm_group_") ||
                                lowerAction.startsWith("alter_group_") ||
                                lowerAction.startsWith("ver_agenda_") ||
                                lowerAction.startsWith("group_view_") ||
                                "group_view_fallback".equalsIgnoreCase(action);

        if (!isGroupAction && parseUuid(action.trim()) == null) {
            return null;
        }

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
        
        List<NotificationGroup> groups = null;
        if (groupId != null) {
            groups = notificationGroupRepository.findByGroupId(groupId);
        }

        if (groups == null || groups.isEmpty()) {
            log.warn("[WEBHOOK] Grupo {} não encontrado no banco. Tentando recuperação por busca de sessão...", groupId);
            if (fromPhone != null && !fromPhone.isBlank()) {
                String dbPhone = blipIdentityReconciler.resolveAndReconcileIdentity(fromPhone, bsuid);
                
                // 1. Tentar buscar pelo agendamento mais recente vinculado ao phone_number que esteja com status PENDING
                Optional<NotificationGroup> latestGroupOpt = notificationGroupRepository.findLatestByPhone(dbPhone);
                if (latestGroupOpt.isPresent()) {
                    NotificationGroup latestGroup = latestGroupOpt.get();
                    Optional<AppointmentSession> sessionOpt = appointmentSessionRepository.findById(latestGroup.getSessionId());
                    if (sessionOpt.isPresent() && sessionOpt.get().getStatus() == br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSessionStatus.PENDING) {
                        groupId = latestGroup.getGroupId();
                        groups = notificationGroupRepository.findByGroupId(groupId);
                        log.info("[WEBHOOK] Recuperado grupo com sucesso via agendamento PENDING recente para o telefone={}. Novo groupId={}", dbPhone, groupId);
                    }
                }

                // 2. Fallback secundário: buscar por qualquer sessão ativa se o mais recente não estiver PENDING
                if (groups == null || groups.isEmpty()) {
                    List<AppointmentSession> activeSessions = appointmentSessionRepository.findActiveByPhoneNumber(dbPhone);
                    if (activeSessions != null && !activeSessions.isEmpty()) {
                        for (AppointmentSession session : activeSessions) {
                            List<NotificationGroup> recoveredGroups = notificationGroupRepository.findBySessionId(session.getId());
                            if (recoveredGroups != null && !recoveredGroups.isEmpty()) {
                                groups = recoveredGroups;
                                groupId = recoveredGroups.get(0).getGroupId();
                                log.info("[WEBHOOK] Recuperado grupo com sucesso via sessao ativa para o telefone={}. Novo groupId={}", dbPhone, groupId);
                                break;
                            }
                        }
                    }
                }
            }
        }
        
        if (groups == null || groups.isEmpty()) {
            log.warn("[WEBHOOK] Falha definitiva: Grupo não encontrado e nenhuma sessão ativa pôde recuperar o groupId={}.", groupId);
            return null;
        }
        
        if (actionType == null) {
            return null;
        }

        String rawFrom = null;
        if (metadata instanceof Map<?, ?> metadataMap) {
            Object rawFromObj = metadataMap.get("rawFrom");
            if (rawFromObj != null) {
                rawFrom = rawFromObj.toString();
            }
        }

        switch (actionType) {
            case "ver_agenda" -> handleVerAgenda(groupId, fromPhone, bsuid, rawFrom);
            case "confirm_group" -> handleConfirmGroup(groupId, fromPhone);
            case "alter_group" -> handleAlterGroup(groupId, fromPhone);
            case "group_view" -> handleGroupView(groupId, fromPhone, rawFrom);
            case "group_view_fallback" -> handleGroupViewFallback(groupId, fromPhone, rawFrom);
            default -> {}
        }
        
        return new HandleBlipWebhookUseCase.WebhookResult("", "", "", "", "group_action_processed", "");
    }

    private void handleVerAgenda(UUID groupId, String fromPhone, String bsuid, String rawFrom) {
        log.info("[WEBHOOK] Clique em 'Ver Agendamentos' recebido para groupId={}", groupId);
        if (fromPhone != null && !fromPhone.isBlank()) {
            String normalizedPhone = fromPhone.trim();
            String dbPhone = blipIdentityReconciler.resolveAndReconcileIdentity(fromPhone, bsuid);

            injectGroupSessionsContext(fromPhone, rawFrom, groupId);

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
        log.info("[WEBHOOK] Interceptando confirm_group_{} para processamento assíncrono e baixa real no Feegow.", groupId);
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

    private void handleGroupViewFallback(UUID groupId, String fromPhone, String rawFrom) {
        log.info("[WEBHOOK] Visualização de grupo com fallback executada para groupId={}", groupId);
        injectGroupSessionsContext(fromPhone, rawFrom, groupId);
    }

    private void handleGroupView(UUID groupId, String fromPhone, String rawFrom) {
        log.info("[WEBHOOK] Clique em visualização de grupo recebido para groupId={}", groupId);
        injectGroupSessionsContext(fromPhone, rawFrom, groupId);
    }

    private void injectGroupSessionsContext(String fromPhone, String rawFrom, UUID groupId) {
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

        Map<String, String> fields = Map.of(
            "lista_detalhada", listaDetalhada,
            "groupId", groupId.toString(),
            "isConfirmingAgenda", "true"
        );

        long start = System.currentTimeMillis();
        java.util.List<java.util.concurrent.CompletableFuture<Void>> futures = new java.util.ArrayList<>();
        
        futures.add(java.util.concurrent.CompletableFuture.runAsync(() -> 
            blipContextService.setUserContextFieldsInParallel(fromPhone.trim(), fields)
        ));

        if (rawFrom != null && !rawFrom.isBlank() && !rawFrom.trim().equalsIgnoreCase(fromPhone.trim())) {
            String cleanRawFrom = rawFrom.trim();
            futures.add(java.util.concurrent.CompletableFuture.runAsync(() -> 
                blipContextService.setUserContextFieldsInParallel(cleanRawFrom, fields)
            ));
        }

        try {
            java.util.concurrent.CompletableFuture.allOf(futures.toArray(java.util.concurrent.CompletableFuture[]::new)).join();
            log.info("[WEBHOOK] Injetado contexto em paralelo com sucesso para groupId={} em {} ms.", groupId, System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("[WEBHOOK] Erro ao injetar contexto em paralelo para groupId={}", groupId, e);
        }
    }

    private UUID parseUuid(String str) {
        if (str == null || str.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(str.trim());
        } catch (IllegalArgumentException e) {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
            java.util.regex.Matcher matcher = pattern.matcher(str);
            if (matcher.find()) {
                try {
                    return UUID.fromString(matcher.group());
                } catch (IllegalArgumentException ex) {
                    return null;
                }
            }
            return null;
        }
    }
}
