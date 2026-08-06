package br.dev.ctrls.inovareti.modules.appointment.application.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import br.dev.ctrls.inovareti.modules.appointment.application.usecase.HandleBlipWebhookUseCase;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentDoctorMapping;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSession;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.NotificationGroup;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentDoctorMappingRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentSessionRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.NotificationGroupRepositoryPort;
import br.dev.ctrls.inovareti.modules.access.domain.port.output.BlipContactClientPort;
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
    private final TransactionTemplate transactionTemplate;
    private final FeegowBulkIntegrationHandler feegowBulkIntegrationHandler;
    private final BlipIdentityReconciler blipIdentityReconciler;
    private final AppointmentDoctorMappingRepositoryPort appointmentDoctorMappingRepository;
    private final BlipContactClientPort blipContactClientPort;
    private final BlipContextService blipContextService;
    private final BlipAppointmentFormatter blipAppointmentFormatter;
    private final BlipNotificationService blipNotificationService;

    /**
     * Intercepta e processa as ações voltadas a agendamento de grupo.
     */
    public HandleBlipWebhookUseCase.WebhookResult handleGroupAction(String action, String fromPhone, String bsuid, Object metadata) {
        if (action == null || action.isBlank()) {
            return null;
        }
        
        // Sanitização de identidade LIME
        if (fromPhone != null) {
            if (fromPhone.contains("@desk.msging.net")) {
                fromPhone = fromPhone.replace("@desk.msging.net", "");
            }
            if (fromPhone.contains("%40")) {
                fromPhone = fromPhone.replace("%40", "@");
            }
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
                                isConfirmGroupText(lowerAction) ||
                                isAlterGroupText(lowerAction) ||
                                isGroupHelpOrNoButton(lowerAction);

        if (!isGroupAction && parseUuid(action.trim()) == null) {
            return null;
        }

        UUID groupId;
        String actionType = null;
        
        if (isGroupHelpOrNoButton(lowerAction)) {
            actionType = "group_help";
            groupId = resolveFallbackGroupId(fromPhone, bsuid, metadata);
        } else if (lowerAction.startsWith("ver_agenda_")) {
            actionType = "ver_agenda";
            groupId = parseUuid(action.substring("ver_agenda_".length()).trim());
        } else if (lowerAction.startsWith("confirm_group_")) {
            actionType = "confirm_group";
            groupId = parseUuid(action.substring("confirm_group_".length()).trim());
        } else if (isConfirmGroupText(lowerAction)) {
            actionType = "confirm_group";
            groupId = resolveFallbackGroupId(fromPhone, bsuid, metadata);
        } else if (lowerAction.startsWith("alter_group_")) {
            actionType = "alter_group";
            groupId = parseUuid(action.substring("alter_group_".length()).trim());
        } else if (isAlterGroupText(lowerAction)) {
            actionType = "alter_group";
            groupId = resolveFallbackGroupId(fromPhone, bsuid, metadata);
        } else if (lowerAction.startsWith("group_view_")) {
            actionType = "group_view";
            groupId = parseUuid(action.substring("group_view_".length()).trim());
        } else if ("group_view_fallback".equalsIgnoreCase(action) || "group_view".equalsIgnoreCase(action.trim()) || "ver_agenda".equalsIgnoreCase(action.trim())) {
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
            groups = findByGroupIdAndPhoneNumberNormalized(groupId, dbPhone);
            if (groups == null || groups.isEmpty()) {
                groups = notificationGroupRepository.findByGroupId(groupId);
                if (groups != null && !groups.isEmpty()) {
                    log.info("[WEBHOOK] Grupo {} recuperado sem filtro de telefone para {}.", groupId, dbPhone);
                }
            }
        }

        if (groups == null || groups.isEmpty()) {
            log.info("[WEBHOOK] Grupo {} não encontrado no banco. Tentando recuperação defensiva por busca de grupo/sessão por telefone...", groupId);
            if (fromPhone != null && !fromPhone.isBlank()) {
                try {
                    // 1. Tentar buscar pelo grupo mais recente via findLatestGroupNormalized
                    Optional<NotificationGroup> latestGroupOpt = findLatestGroupNormalized(dbPhone);
                    if (latestGroupOpt.isPresent()) {
                        NotificationGroup latestGroup = latestGroupOpt.get();
                        groupId = latestGroup.getGroupId();
                        groups = notificationGroupRepository.findByGroupId(groupId);
                        if (groups != null && !groups.isEmpty()) {
                            log.info("[WEBHOOK] Recuperado grupo com sucesso via findLatestGroupNormalized para o telefone={}. Novo groupId={}", dbPhone, groupId);
                        }
                    }

                    // 2. Fallback secundário: buscar por sessões ativas
                    if (groups == null || groups.isEmpty()) {
                        List<AppointmentSession> activeSessions = findActiveSessionsNormalized(dbPhone);
                        if (activeSessions != null && !activeSessions.isEmpty()) {
                            for (AppointmentSession session : activeSessions) {
                                if (session.getCurrentGroupId() != null) {
                                    groupId = session.getCurrentGroupId();
                                    groups = notificationGroupRepository.findByGroupId(groupId);
                                    if (groups != null && !groups.isEmpty()) {
                                        log.info("[WEBHOOK] Recuperado grupo com sucesso via currentGroupId da sessão ativa para o telefone={}. Novo groupId={}", dbPhone, groupId);
                                        break;
                                    }
                                }
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
            if (groups == null || groups.isEmpty()) {
                log.info("[WEBHOOK] Grupo não encontrado no banco para groupId={} (actionType={}).", groupId, actionType);
                
                boolean shouldForceHumanDesk = "confirm_group".equalsIgnoreCase(actionType) || "alter_group".equalsIgnoreCase(actionType);
                if (shouldForceHumanDesk && fromPhone != null && !fromPhone.isBlank()) {
                    log.info("[WEBHOOK] Executando fallback por telefone para {} devido a falha na confirmação/alteração do grupo.", fromPhone);
                    try {
                        String purifiedPhone = "";
                        String reconciliationInput = fromPhone.trim();
                        boolean isTunnel = reconciliationInput.contains("@tunnel.msging.net");
                        String localPart = reconciliationInput;
                        if (reconciliationInput.contains("@")) {
                            localPart = reconciliationInput.substring(0, reconciliationInput.indexOf("@"));
                        }
                        boolean startsWithNumeric = localPart.matches("^\\d+.*$");
                        
                        if (isTunnel || !startsWithNumeric) {
                            log.info("[WEBHOOK-FALLBACK] Identidade '{}' sinaliza ser GUID de túnel. Invocando BlipIdentityReconciler...", reconciliationInput);
                            String resolvedPhone = blipIdentityReconciler.resolveAndReconcileIdentity(reconciliationInput, bsuid);
                            if (resolvedPhone != null && !resolvedPhone.isBlank()) {
                                purifiedPhone = purifyPhoneNumberForSearch(resolvedPhone);
                            }
                        }
                        
                        if (purifiedPhone.isEmpty()) {
                            purifiedPhone = purifyPhoneNumberForSearch(dbPhone);
                        }
                        if (purifiedPhone.isEmpty()) {
                            purifiedPhone = purifyPhoneNumberForSearch(fromPhone);
                        }
                        List<AppointmentSession> activeSessions = findActiveSessionsNormalized(purifiedPhone);
                        if (activeSessions != null && !activeSessions.isEmpty()) {
                            AppointmentSession session = activeSessions.get(0);
                            String doctorId = session.getDoctorProfissionalId();
                            if (doctorId != null && !doctorId.isBlank()) {
                                Optional<AppointmentDoctorMapping> doctorMappingOpt = appointmentDoctorMappingRepository.findByProfissionalId(doctorId);
                                if (doctorMappingOpt.isPresent()) {
                                    AppointmentDoctorMapping mapping = doctorMappingOpt.get();
                                    String blipQueueId = mapping.getBlipQueueId();
                                    if (blipQueueId != null && !blipQueueId.isBlank()) {
                                        String blipQueueName = blipContextService.resolveQueueName(blipQueueId);
                                        if (blipQueueName == null || blipQueueName.isBlank() || "Recepção Central / Suporte".equalsIgnoreCase(blipQueueName)) {
                                            blipQueueName = blipQueueId;
                                        } else {
                                            log.info("[WEBHOOK-FALLBACK] Fila UUID {} traduzida com sucesso para o nome descritivo '{}' antes do push de sincronização.", blipQueueId, blipQueueName);
                                        }
                                        log.info("[WEBHOOK-FALLBACK] Forçando push da fila '{}' para o contato {} no Blip Router.", blipQueueName, fromPhone);
                                        boolean syncOk = blipContactClientPort.syncContact(fromPhone, "", "", blipQueueName, doctorId);
                                        if (syncOk) {
                                            blipContextService.setQueueRedirect(fromPhone, blipQueueName);
                                            log.info("[WEBHOOK-FALLBACK] Forçando redirecionamento de Master-State do usuário {} para o bloco de destino humano de pauta.", fromPhone);
                                            String targetBot = "fluxov1@msging.net";
                                            String stateId = "b3461299-9500-46b1-b423-12ffef3e1aba";
                                            blipContextService.setMasterState(fromPhone, targetBot, stateId);

                                            if (bsuid != null && !bsuid.isBlank()) {
                                                String cleanBsuid = bsuid.trim();
                                                if (cleanBsuid.contains("@")) {
                                                    cleanBsuid = cleanBsuid.substring(0, cleanBsuid.indexOf("@"));
                                                }
                                                cleanBsuid = cleanBsuid.trim();
                                                if (cleanBsuid.matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")) {
                                                    String tunnelId = cleanBsuid + "@tunnel.msging.net";
                                                    if (!tunnelId.equalsIgnoreCase(fromPhone)) {
                                                        blipContextService.setQueueRedirect(tunnelId, blipQueueName);
                                                        blipContextService.setMasterState(tunnelId, targetBot, stateId);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (Exception ex) {
                        log.error("[WEBHOOK-FALLBACK] Erro ao executar fallback de fila por telefone para {}: {}", fromPhone, ex.getMessage(), ex);
                    }
                } else {
                    log.info("[WEBHOOK] Ignorando fallback para atendimento humano para actionType={}.", actionType);
                }
                return null;
            }
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
            case "ver_agenda" -> handleVerAgenda(groupId, fromPhone, rawFrom);
            case "confirm_group" -> handleConfirmGroup(groupId, fromPhone);
            case "alter_group" -> handleAlterGroup(groupId, fromPhone);
            case "group_view" -> handleGroupView(groupId, fromPhone, rawFrom);
            case "group_view_fallback" -> handleGroupViewFallback(groupId, fromPhone, rawFrom);
            case "group_help" -> handleGroupHelp(fromPhone, dbPhone);
            default -> {}
        }
        
        return new HandleBlipWebhookUseCase.WebhookResult("", "", "", "", "group_action_processed", "");
    }

    private boolean isGroupHelpOrNoButton(String lower) {
        if (lower == null) return false;
        String t = lower.trim();
        return t.contains("não tem botão") || t.contains("nao tem botao") ||
               t.contains("não tem botões") || t.contains("nao tem botoes") ||
               t.contains("sem botão") || t.contains("sem botao") ||
               t.contains("como faço") || t.contains("como faco") ||
               t.contains("como confirmar");
    }

    private boolean isConfirmGroupText(String lower) {
        if (lower == null) return false;
        String t = lower.trim();
        return t.contains("confirmar tudo") || t.contains("confirmar_tudo") ||
               t.equalsIgnoreCase("1") || t.equalsIgnoreCase("1️⃣") ||
               t.equalsIgnoreCase("opcao 1") || t.equalsIgnoreCase("opção 1") ||
               t.equalsIgnoreCase("confirmar") || t.equalsIgnoreCase("confirmo") ||
               t.startsWith("1 ") || t.startsWith("1-") || t.startsWith("1.");
    }

    private boolean isAlterGroupText(String lower) {
        if (lower == null) return false;
        String t = lower.trim();
        return t.contains("preciso alterar") || t.contains("preciso_alterar") ||
               t.equalsIgnoreCase("2") || t.equalsIgnoreCase("2️⃣") ||
               t.equalsIgnoreCase("opcao 2") || t.equalsIgnoreCase("opção 2") ||
               t.equalsIgnoreCase("alterar") ||
               t.startsWith("2 ") || t.startsWith("2-") || t.startsWith("2.");
    }

    private void handleGroupHelp(String fromPhone, String dbPhone) {
        log.info("[WEBHOOK-HELP] Interceptando solicitação de ajuda/sem botões do paciente para {}.", fromPhone);
        String searchPhone = (dbPhone != null && !dbPhone.isBlank()) ? dbPhone : fromPhone;
        List<AppointmentSession> activeSessions = findActiveSessionsNormalized(searchPhone);
        if (activeSessions == null || activeSessions.isEmpty()) {
            activeSessions = findActiveSessionsNormalized(purifyPhoneNumberForSearch(fromPhone));
        }

        if (activeSessions != null && !activeSessions.isEmpty()) {
            UUID groupId = null;
            for (AppointmentSession s : activeSessions) {
                if (s.getCurrentGroupId() != null) {
                    groupId = s.getCurrentGroupId();
                    break;
                }
            }
            if (groupId == null) {
                Optional<NotificationGroup> latestGroupOpt = findLatestGroupNormalized(searchPhone);
                if (latestGroupOpt.isPresent()) {
                    groupId = latestGroupOpt.get().getGroupId();
                }
            }
            if (groupId != null) {
                saveGroupIdToSessions(fromPhone, groupId);
            }

            String textMessage = blipAppointmentFormatter.buildListaDetalhada(activeSessions);
            if (textMessage != null && !textMessage.isBlank()) {
                String helpMessage = textMessage + "\n\nPor favor, confirme sua presença:\n1️⃣ Digite *1* ou clique em *CONFIRMAR TUDO*\n2️⃣ Digite *2* ou clique em *PRECISO ALTERAR*";
                blipNotificationService.sendPlainTextMessage(fromPhone, helpMessage);
                log.info("[WEBHOOK-HELP] Enviada mensagem com lista detalhada e opções numéricas em texto plano para {}", fromPhone);
            }
        } else {
            log.warn("[WEBHOOK-HELP] Nenhuma sessão ativa encontrada para enviar mensagem de ajuda para {}", fromPhone);
        }
    }

    /**
     * Trata o clique em 'Ver Agendamentos':
     * 1. Persiste o groupId no banco.
     * 2. Injeta contexto (lista_detalhada, groupId, isConfirmingAgenda) ativamente no Blip.
     * 3. Muda o master-state para o bloco Exibir_Agenda, corrigindo a rota do bot.
     */
    private void handleVerAgenda(UUID groupId, String fromPhone, String rawFrom) {
        log.info("[WEBHOOK] Clique em 'Ver Agendamentos' recebido. groupId={} para {}", groupId, fromPhone);
        saveGroupIdToSessions(fromPhone, groupId);
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

            // 1. Tentar buscar pelas sessões ativas do banco
            List<AppointmentSession> activeSessions = findActiveSessionsNormalized(targetPhone);
            if (activeSessions != null) {
                for (AppointmentSession s : activeSessions) {
                    if (s.getCurrentGroupId() != null) {
                        log.info("[WEBHOOK] Fallback groupId encontrado em sessão ativa: {} para telefone: {}", s.getCurrentGroupId(), targetPhone);
                        return s.getCurrentGroupId();
                    }
                }
            }

            // 2. Tentar buscar o grupo ativo mais recente pelo número de telefone
            NotificationGroup latestGroup = transactionTemplate.execute(status ->
                findLatestGroupNormalized(targetPhone).orElse(null)
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
    }

    private void handleGroupView(UUID groupId, String fromPhone, String rawFrom) {
        log.info("[WEBHOOK] Clique em visualização de grupo. groupId={} para {}", groupId, fromPhone);
        saveGroupIdToSessions(fromPhone, groupId);
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
                findActiveSessionsNormalized(dbPhone);
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

    private String purifyPhoneNumberForSearch(String rawPhone) {
        if (rawPhone == null || rawPhone.isBlank()) {
            return "";
        }
        String clean = rawPhone.trim();
        if (clean.contains("@")) {
            clean = clean.substring(0, clean.indexOf('@')).trim();
        }
        if (clean.contains(".")) {
            clean = clean.substring(0, clean.indexOf('.')).trim();
        }
        clean = clean.replaceAll("\\D", "");
        if (clean.startsWith("55") && clean.length() > 10) {
            clean = clean.substring(2);
        }
        return clean;
    }

    private List<AppointmentSession> findActiveSessionsNormalized(String phone) {
        if (phone == null || phone.isBlank()) return List.of();
        String digitsOnly = phone.replaceAll("\\D", "");
        if (digitsOnly.isBlank()) return List.of();

        String withDdi = digitsOnly.startsWith("55") ? digitsOnly : "55" + digitsOnly;
        String withoutDdi = withDdi.length() > 2 ? withDdi.substring(2) : withDdi;

        List<AppointmentSession> sessions = appointmentSessionRepository.findActiveByPhoneNumber(withDdi);
        if (sessions != null && !sessions.isEmpty()) {
            return sessions;
        }
        sessions = appointmentSessionRepository.findActiveByPhoneNumber(withoutDdi);
        if (sessions != null && !sessions.isEmpty()) {
            return sessions;
        }
        return List.of();
    }

    private Optional<NotificationGroup> findLatestGroupNormalized(String phone) {
        if (phone == null || phone.isBlank()) return Optional.empty();
        String digitsOnly = phone.replaceAll("\\D", "");
        if (digitsOnly.isBlank()) return Optional.empty();

        String withDdi = digitsOnly.startsWith("55") ? digitsOnly : "55" + digitsOnly;
        String withoutDdi = withDdi.length() > 2 ? withDdi.substring(2) : withDdi;

        Optional<NotificationGroup> opt = notificationGroupRepository.findLatestByPhone(withDdi);
        if (opt.isPresent()) return opt;

        return notificationGroupRepository.findLatestByPhone(withoutDdi);
    }

    private List<NotificationGroup> findByGroupIdAndPhoneNumberNormalized(UUID groupId, String phone) {
        if (groupId == null || phone == null || phone.isBlank()) return List.of();
        String digitsOnly = phone.replaceAll("\\D", "");
        if (digitsOnly.isBlank()) return List.of();

        String withDdi = digitsOnly.startsWith("55") ? digitsOnly : "55" + digitsOnly;
        String withoutDdi = withDdi.length() > 2 ? withDdi.substring(2) : withDdi;

        List<NotificationGroup> groups = notificationGroupRepository.findByGroupIdAndPhoneNumber(groupId, withDdi);
        if (groups != null && !groups.isEmpty()) return groups;

        groups = notificationGroupRepository.findByGroupIdAndPhoneNumber(groupId, withoutDdi);
        if (groups != null && !groups.isEmpty()) return groups;

        return List.of();
    }
}
