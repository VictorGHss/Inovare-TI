package br.dev.ctrls.inovareti.modules.appointment.application.service;

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
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.BlipUserIdentityReconciliationRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.NotificationGroupRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.BlipProperties;
import io.micrometer.observation.annotation.Observed;
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
    private final BlipProperties blipProperties;
    private final BlipAppointmentFormatter blipAppointmentFormatter;
    private final org.springframework.core.task.AsyncTaskExecutor applicationTaskExecutor;
    private final BlipUserIdentityReconciliationRepositoryPort blipUserIdentityReconciliationRepository;

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
                                "group_view_fallback".equalsIgnoreCase(action) ||
                                "confirmar tudo".equalsIgnoreCase(lowerAction.trim()) ||
                                "confirmar_tudo".equalsIgnoreCase(lowerAction.trim()) ||
                                "preciso alterar".equalsIgnoreCase(lowerAction.trim()) ||
                                "preciso_alterar".equalsIgnoreCase(lowerAction.trim());

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
        } else if ("confirmar tudo".equalsIgnoreCase(lowerAction.trim()) || "confirmar_tudo".equalsIgnoreCase(lowerAction.trim())) {
            actionType = "confirm_group";
            groupId = resolveFallbackGroupId(fromPhone, bsuid, metadata);
        } else if (lowerAction.startsWith("alter_group_")) {
            actionType = "alter_group";
            groupId = parseUuid(action.substring("alter_group_".length()).trim());
        } else if ("preciso alterar".equalsIgnoreCase(lowerAction.trim()) || "preciso_alterar".equalsIgnoreCase(lowerAction.trim())) {
            actionType = "alter_group";
            groupId = resolveFallbackGroupId(fromPhone, bsuid, metadata);
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
        
        String dbPhone = blipIdentityReconciler.resolveAndReconcileIdentity(fromPhone, bsuid);
        List<NotificationGroup> groups = null;
        if (groupId != null) {
            groups = notificationGroupRepository.findByGroupIdAndPhoneNumber(groupId, dbPhone);
        }

        if (groups == null || groups.isEmpty()) {
            log.info("[WEBHOOK] Grupo {} não encontrado no banco. Tentando recuperação defensiva por busca de sessão...", groupId);
            if (fromPhone != null && !fromPhone.isBlank()) {
                try {
                    // 1. Tentar buscar pelo agendamento mais recente vinculado ao phone_number que esteja com status PENDING
                    Optional<NotificationGroup> latestGroupOpt = notificationGroupRepository.findLatestByPhone(dbPhone);
                    if (latestGroupOpt.isPresent()) {
                        NotificationGroup latestGroup = latestGroupOpt.get();
                        Optional<AppointmentSession> sessionOpt = appointmentSessionRepository.findById(latestGroup.getSessionId());
                        if (sessionOpt.isPresent() && sessionOpt.get().getStatus() == br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSessionStatus.PENDING) {
                            groupId = latestGroup.getGroupId();
                            groups = notificationGroupRepository.findByGroupIdAndPhoneNumber(groupId, dbPhone);
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
                } catch (Exception e) {
                    log.debug("[WEBHOOK] Erro defensivo durante a recuperação de grupo/sessão para o telefone {}: {}", dbPhone, e.getMessage());
                }
            }
        }
        
        if (groups == null || groups.isEmpty()) {
            log.debug("[WEBHOOK] Falha ao processar ação: Grupo não encontrado e nenhuma sessão ativa pôde recuperar o groupId={}.", groupId);
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

    /**
     * Trata o clique em 'Ver Agendamentos':
     * 1. Persiste o groupId no banco.
     * 2. Injeta contexto (lista_detalhada, groupId, isConfirmingAgenda) ativamente no Blip.
     * 3. Muda o master-state para o bloco Exibir_Agenda, corrigindo a rota do bot.
     */
    private void handleVerAgenda(UUID groupId, String fromPhone, String bsuid, String rawFrom) {
        log.info("[WEBHOOK] Clique em 'Ver Agendamentos' recebido. groupId={} para {}", groupId, fromPhone);
        saveGroupIdToSessions(fromPhone, groupId);
        injectContextAndRedirectToExibirAgenda(groupId, fromPhone, rawFrom);
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
        log.info("[WEBHOOK] Visualização de grupo com fallback. groupId={} para {}", groupId, fromPhone);
        saveGroupIdToSessions(fromPhone, groupId);
        injectContextAndRedirectToExibirAgenda(groupId, fromPhone, rawFrom);
    }

    private void handleGroupView(UUID groupId, String fromPhone, String rawFrom) {
        log.info("[WEBHOOK] Clique em visualização de grupo. groupId={} para {}", groupId, fromPhone);
        saveGroupIdToSessions(fromPhone, groupId);
        injectContextAndRedirectToExibirAgenda(groupId, fromPhone, rawFrom);
    }

    /**
     * Persiste o groupId nas sessões ativas do paciente para que o endpoint
     * síncrono Exibir_Agenda possa construir a lista_detalhada no momento certo.
     */
    private void saveGroupIdToSessions(String fromPhone, UUID groupId) {
        if (fromPhone == null || fromPhone.isBlank()) {
            log.warn("[WEBHOOK] fromPhone ausente ao salvar groupId={}", groupId);
            return;
        }
        String normalizedPhone = fromPhone.trim();
        String dbPhone = blipIdentityReconciler.resolveAndReconcileIdentity(normalizedPhone, null);
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
        log.info("[WEBHOOK] groupId={} salvo no banco para {}", groupId, normalizedPhone);
    }

    /**
     * Injeta contexto (lista_detalhada, groupId, isConfirmingAgenda) no Blip e
     * seta master-state de volta para Preparar_Atendimento, cujo bypass nativo
     * avalia o input 'ver_agenda_' e transita para Exibir_Agenda,
     * disparando o entering action que renderiza o conteúdo ao usuário.
     * Executado de forma assíncrona para não bloquear o 200 OK ao Blip.
     */
    private void injectContextAndRedirectToExibirAgenda(UUID groupId, String fromPhone, String rawFrom) {
        if (fromPhone == null || fromPhone.isBlank()) {
            return;
        }

        // Usa Preparar_Atendimento como target: o bypass nativo do Builder avalia ver_agenda_
        // e transita para Exibir_Agenda, disparando o entering action correto.
        final String preparar_atendimentoBlockId;
        String rawPrepararAtendimento = blipProperties.getBlocks().getPrepararAtendimento();
        preparar_atendimentoBlockId = (rawPrepararAtendimento != null && !rawPrepararAtendimento.isBlank())
            ? rawPrepararAtendimento
            : "a0776d9c-6486-42f3-8a4f-2706f0185908";

        String subbotId = blipProperties.getSubbotId();
        String dbPhone = blipIdentityReconciler.resolveAndReconcileIdentity(fromPhone.trim(), null);

        // Monta a lista_detalhada
        String listaDetalhada = "";
        try {
            List<NotificationGroup> groups = notificationGroupRepository.findByGroupIdAndPhoneNumber(groupId, dbPhone);
            if (groups != null && !groups.isEmpty()) {
                for (NotificationGroup g : groups) {
                    if (g.getPreCompiledScheduleText() != null && !g.getPreCompiledScheduleText().isBlank()) {
                        listaDetalhada = g.getPreCompiledScheduleText();
                        break;
                    }
                }
            }
        } catch (Exception e) {
            log.error("[WEBHOOK] Erro ao buscar NotificationGroup para groupId={}", groupId, e);
        }

        if (listaDetalhada.isBlank()) {
            log.info("[WEBHOOK] preCompiledScheduleText vazio para groupId={}. Compilando em tempo de execução...", groupId);
            try {
                List<AppointmentSession> activeSessions = appointmentSessionRepository.findActiveByPhoneNumber(dbPhone);
                if (activeSessions != null && !activeSessions.isEmpty()) {
                    listaDetalhada = blipAppointmentFormatter.buildListaDetalhada(activeSessions);
                }
            } catch (Exception e) {
                log.error("[WEBHOOK] Erro ao compilar lista detalhada em tempo de execução para {}", fromPhone, e);
            }
        }

        final String listaFinal = listaDetalhada;
        Map<String, String> fields = Map.of(
            "lista_detalhada", listaFinal,
            "listaDetalhada", listaFinal,
            "groupId", groupId.toString(),
            "isConfirmingAgenda", "true"
        );

        // Determina identidades de túnel
        java.util.List<String> tunnelIdentities = new java.util.ArrayList<>();
        if (rawFrom != null && !rawFrom.isBlank() && !rawFrom.trim().equalsIgnoreCase(fromPhone.trim())) {
            tunnelIdentities.add(rawFrom.trim());
        } else if (subbotId != null && !subbotId.isBlank()) {
            String userLocalPart = fromPhone.trim();
            if (userLocalPart.contains("@")) userLocalPart = userLocalPart.substring(0, userLocalPart.indexOf('@'));
            String subbotLocalPart = subbotId.trim();
            if (subbotLocalPart.contains("@")) subbotLocalPart = subbotLocalPart.substring(0, subbotLocalPart.indexOf('@'));
            tunnelIdentities.add(userLocalPart + "." + subbotLocalPart + "@tunnel.msging.net");
        }

        // Reconciliação de túneis reais via banco
        try {
            String purified = fromPhone.trim();
            if (purified.contains("@")) purified = purified.substring(0, purified.indexOf('@')).trim();
            purified = purified.replaceAll("\\D", "");
            if (!purified.startsWith("55") && !purified.isEmpty()) purified = "55" + purified;
            String cleanPhone = purified;
            String altPhone = cleanPhone.startsWith("55") ? cleanPhone.substring(2) : "55" + cleanPhone;
            java.util.List<br.dev.ctrls.inovareti.modules.appointment.domain.model.BlipUserIdentityReconciliation> recs = new java.util.ArrayList<>();
            recs.addAll(blipUserIdentityReconciliationRepository.findByPhoneNumber(cleanPhone));
            recs.addAll(blipUserIdentityReconciliationRepository.findByPhoneNumber(altPhone));
            for (var rec : recs) {
                if (rec.getBlipGuid() != null && !rec.getBlipGuid().isBlank()) {
                    String tunnelId = rec.getBlipGuid().trim() + "@tunnel.msging.net";
                    if (!tunnelId.equalsIgnoreCase(fromPhone) && !tunnelIdentities.contains(tunnelId)) {
                        tunnelIdentities.add(tunnelId);
                        log.info("[WEBHOOK] Túnel reconciliado adicionado: {}", tunnelId);
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("[WEBHOOK] Falha na reconciliação de túneis: {}", ex.getMessage());
        }

        final List<String> finalTunnels = java.util.List.copyOf(tunnelIdentities);
        final String finalFromPhone = fromPhone.trim();
        final String finalBlockId = preparar_atendimentoBlockId;
        final String finalSubbotId = subbotId;

        // Executa de forma assíncrona para não atrasar o 200 OK
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            long start = System.currentTimeMillis();
            try {
                // 1. Injeta contexto para o telefone principal e todos os túneis
                java.util.List<java.util.concurrent.CompletableFuture<Void>> ctxFutures = new java.util.ArrayList<>();
                ctxFutures.add(java.util.concurrent.CompletableFuture.runAsync(() ->
                    blipContextService.setUserContextFieldsInParallel(finalFromPhone, fields), applicationTaskExecutor));
                for (String tunnel : finalTunnels) {
                    ctxFutures.add(java.util.concurrent.CompletableFuture.runAsync(() ->
                        blipContextService.setUserContextFieldsInParallel(tunnel, fields), applicationTaskExecutor));
                }
                java.util.concurrent.CompletableFuture.allOf(ctxFutures.toArray(java.util.concurrent.CompletableFuture[]::new)).join();

                // 2. Seta master-state para Preparar_Atendimento - o fluxo nativo vai concluir em Exibir_Agenda
                java.util.List<java.util.concurrent.CompletableFuture<Void>> stateFutures = new java.util.ArrayList<>();
                stateFutures.add(java.util.concurrent.CompletableFuture.runAsync(() -> {
                    try {
                        blipContextService.setMasterState(finalFromPhone, finalSubbotId, finalBlockId);
                    } catch (Exception e) {
                        log.error("[WEBHOOK] Erro ao atualizar Master-State do Roteador para {}", finalFromPhone, e);
                    }
                }, applicationTaskExecutor));
                for (String tunnel : finalTunnels) {
                    stateFutures.add(java.util.concurrent.CompletableFuture.runAsync(() -> {
                        try {
                            blipContextService.setBuilderMasterState(tunnel, finalBlockId);
                        } catch (Exception e) {
                            log.error("[WEBHOOK] Erro ao atualizar Builder Master-State para {}", tunnel, e);
                        }
                    }, applicationTaskExecutor));
                }
                java.util.concurrent.CompletableFuture.allOf(stateFutures.toArray(java.util.concurrent.CompletableFuture[]::new)).join();

                log.info("[GRUPO-OK] Master-State direcionado para Preparar Atendimento como escada para o fluxo nativo.");
            } catch (Exception e) {
                log.error("[WEBHOOK] Erro ao injetar contexto/master-state para groupId={}", groupId, e);
            }
        }, applicationTaskExecutor);
    }

    private static final java.util.regex.Pattern STRICT_UUID_PATTERN = 
        java.util.regex.Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private UUID parseUuid(String str) {
        if (str == null || str.isBlank()) {
            return null;
        }
        String trimmed = str.trim();
        if (!STRICT_UUID_PATTERN.matcher(trimmed).matches()) {
            return null;
        }
        try {
            return UUID.fromString(trimmed);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
