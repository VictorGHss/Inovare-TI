package br.dev.ctrls.inovareti.modules.appointment.application.service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import br.dev.ctrls.inovareti.modules.appointment.application.dto.AppointmentPayload;
import br.dev.ctrls.inovareti.modules.appointment.application.dto.BlipContactUpdateCommand;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.client.BlipLIMEClient;
import io.micrometer.observation.annotation.Observed;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@org.springframework.stereotype.Service
@Observed
public class BlipContextService {

    private final BlipLIMEClient limeClient;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final org.springframework.core.task.AsyncTaskExecutor applicationTaskExecutor;
    private final BlipIdentityReconciler blipIdentityReconciler;

    @org.springframework.beans.factory.annotation.Value("${APP_BLIP_APPOINTMENT_ID:}")
    private String blipAppointmentId;

    public BlipContextService(
            BlipLIMEClient limeClient, 
            com.fasterxml.jackson.databind.ObjectMapper objectMapper, 
            org.springframework.core.task.AsyncTaskExecutor applicationTaskExecutor,
            BlipIdentityReconciler blipIdentityReconciler) {
        this.limeClient = limeClient;
        this.objectMapper = objectMapper;
        this.applicationTaskExecutor = applicationTaskExecutor;
        this.blipIdentityReconciler = blipIdentityReconciler;
    }

    public String resolveMasterIdentity(String userIdentity) {
        if (userIdentity == null || userIdentity.isBlank()) return null;
        String clean = userIdentity.trim();
        if (clean.contains("@tunnel.msging.net")) {
            String reconciled = blipIdentityReconciler.resolveAndReconcileIdentity(clean, null);
            if (reconciled != null && !reconciled.isBlank()) {
                return reconciled.contains("@") ? reconciled : limeClient.normalizeUserIdentity(reconciled);
            }
            String local = clean.substring(0, clean.indexOf('@'));
            if (local.contains(".")) {
                String digits = local.substring(0, local.indexOf('.')).replaceAll("\\D", "");
                if (!digits.isBlank()) {
                    return limeClient.normalizeUserIdentity(digits);
                }
            }
            return null;
        }
        return limeClient.normalizeUserIdentity(clean);
    }

    public String resolveTunnelIdentity(String userIdentity) {
        if (userIdentity == null || userIdentity.isBlank()) return null;
        String clean = userIdentity.trim();
        if (clean.contains("@tunnel.msging.net")) {
            return clean;
        }
        String normalized = limeClient.normalizeUserIdentity(clean);
        String phoneDigits = normalized.contains("@") ? normalized.substring(0, normalized.indexOf('@')).replaceAll("\\D", "") : normalized.replaceAll("\\D", "");
        if (phoneDigits.isBlank()) return null;

        String subbotLocalPart = "fluxov1";
        if (blipAppointmentId != null && !blipAppointmentId.isBlank()) {
            subbotLocalPart = blipAppointmentId.contains("@") ? blipAppointmentId.substring(0, blipAppointmentId.indexOf('@')) : blipAppointmentId.trim();
        }
        return phoneDigits + "." + subbotLocalPart + "@tunnel.msging.net";
    }

    public void setUserContextForUser(String userIdentity, String key, String value) {
        setUserContext(userIdentity, key, value);
    }

    public void setUserContextFieldsInParallel(String userIdentity, Map<String, String> fields) {
        if (userIdentity == null || userIdentity.isBlank() || fields == null || fields.isEmpty()) {
            return;
        }
        log.debug("[LIME-PARALLEL] Configurando contexto LIME em paralelo para target: {}. Campos: {}", userIdentity, fields.keySet());
        java.util.List<java.util.concurrent.CompletableFuture<Void>> futures = fields.entrySet().stream()
            .map(entry -> java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    setUserContext(userIdentity, entry.getKey(), entry.getValue());
                } catch (Exception e) {
                    log.error("Erro ao configurar contexto para {} key: {}", userIdentity, entry.getKey(), e);
                }
            }, applicationTaskExecutor))
            .toList();
        try {
            java.util.concurrent.CompletableFuture.allOf(futures.toArray(java.util.concurrent.CompletableFuture[]::new)).join();
        } catch (Exception e) {
            log.error("Erro ao aguardar configuração de contexto para {}", userIdentity, e);
        }
    }

    public String getUserContext(String userIdentity, String key) {
        if (userIdentity == null || userIdentity.isBlank() || key == null || key.isBlank()) return null;
        String normalizedIdentity = limeClient.normalizeUserIdentity(userIdentity);

        Map<String, Object> command = Map.of(
            "id", UUID.randomUUID().toString(),
            "to", BlipLIMEClient.MASTER_STATE_COMMAND_TO,
            "method", "get",
            "uri", "/contexts/" + normalizedIdentity + "/" + key
        );

        try {
            Map<String, Object> response = limeClient.executeCommand(command, BlipLIMEClient.AuthorizationScope.ROUTER);
            Map<String, Object> body = response;
            if (body == null) return null;
            Object resource = body.get("resource");
            if (resource == null) return null;

            String value;
            if (resource instanceof Map<?, ?> map) {
                Object rawValue = map.get("value");
                value = rawValue != null ? String.valueOf(rawValue) : null;
            } else {
                value = String.valueOf(resource);
            }

            if (value == null) return null;
            String normalizedValue = value.trim();
            if (normalizedValue.isBlank() || "null".equalsIgnoreCase(normalizedValue)) return null;
            return normalizedValue;
        } catch (org.springframework.web.client.RestClientException ex) {
            log.warn("Falha ao consultar contexto. identity={}, key={}", normalizedIdentity, key, ex);
            return null;
        }
    }

    public void setUserContext(String userIdentity, String key, String value) {
        if (userIdentity == null || userIdentity.isBlank() || value == null || value.isBlank()) return;

        String masterIdentity = resolveMasterIdentity(userIdentity);
        String tunnelIdentity = resolveTunnelIdentity(userIdentity);

        if (masterIdentity != null && !masterIdentity.isBlank()) {
            sendSingleUserContext(masterIdentity, key, value);
        }
        if (tunnelIdentity != null && !tunnelIdentity.isBlank() && !tunnelIdentity.equalsIgnoreCase(masterIdentity)) {
            sendSingleUserContext(tunnelIdentity, key, value);
        }
    }

    private void sendSingleUserContext(String normalizedIdentity, String key, String value) {
        Map<String, Object> command = Map.of(
            "id", UUID.randomUUID().toString(),
            "to", "postmaster@msging.net",
            "method", "set",
            "uri", "/contexts/" + normalizedIdentity + "/" + key,
            "type", "text/plain",
            "metadata", Map.of("expiration", "86400"),
            "resource", value
        );

        try {
            limeClient.executeCommand(command, BlipLIMEClient.AuthorizationScope.ROUTER);
            log.info("Contexto configurado (escopo dual). identity={}, key={}", normalizedIdentity, key);
        } catch (org.springframework.web.client.RestClientException ex) {
            log.warn("Falha ao configurar contexto. identity={}, key={}", normalizedIdentity, key, ex);
        }
    }

    public void deleteUserContext(String userIdentity, String key) {
        if (userIdentity == null || userIdentity.isBlank() || key == null || key.isBlank()) return;

        String normalizedIdentity = limeClient.normalizeUserIdentity(userIdentity);

        Map<String, Object> command = Map.of(
            "id", UUID.randomUUID().toString(),
            "to", BlipLIMEClient.MASTER_STATE_COMMAND_TO,
            "method", "delete",
            "uri", "/contexts/" + normalizedIdentity + "/" + key
        );

        try {
            limeClient.executeCommand(command, BlipLIMEClient.AuthorizationScope.ROUTER);
            log.info("Contexto removido. identity={}, key={}", normalizedIdentity, key);
        } catch (org.springframework.web.client.RestClientException ex) {
            log.warn("Falha ao remover contexto. identity={}, key={}", normalizedIdentity, key, ex);
        }
    }

    /**
     * Envia um contexto JSON ao Blip via comando LIME.
     * O resource é passado como Object (Map, record, etc.) e serializado para string JSON,
     * sendo enviado como type: "text/plain".
     * Isso evita o erro Code 21 (quando enviado como application/json com string) AND
     * evita o erro de [object Object] / Redirecionamento incorreto no Javascript do Blip
     * (já que o Blip receberá e armazenará uma string JSON pura que o script consegue parsear).
     */
    public void setJsonContext(String userIdentity, String key, Object resourceObject) {
        if (userIdentity == null || userIdentity.isBlank() || resourceObject == null) return;

        String masterIdentity = resolveMasterIdentity(userIdentity);
        String tunnelIdentity = resolveTunnelIdentity(userIdentity);

        if (masterIdentity != null && !masterIdentity.isBlank()) {
            sendSingleJsonContext(masterIdentity, key, resourceObject);
        }
        if (tunnelIdentity != null && !tunnelIdentity.isBlank() && !tunnelIdentity.equalsIgnoreCase(masterIdentity)) {
            sendSingleJsonContext(tunnelIdentity, key, resourceObject);
        }
    }

    private void sendSingleJsonContext(String normalizedIdentity, String key, Object resourceObject) {
        try {
            String jsonString = objectMapper.writeValueAsString(resourceObject);

            java.util.LinkedHashMap<String, Object> command = new java.util.LinkedHashMap<>();
            command.put("id", UUID.randomUUID().toString());
            command.put("to", "postmaster@msging.net");
            command.put("method", "set");
            command.put("uri", "/contexts/" + normalizedIdentity + "/" + key);
            command.put("type", "text/plain");
            command.put("metadata", Map.of("expiration", "86400"));
            command.put("resource", jsonString);

            limeClient.executeCommand(command, BlipLIMEClient.AuthorizationScope.ROUTER);
            log.info("[LIME] Contexto JSON configurado (escopo dual). identity={}, key={}", normalizedIdentity, key);
        } catch (com.fasterxml.jackson.core.JsonProcessingException | org.springframework.web.client.RestClientException ex) {
            log.warn("[LIME] Falha ao configurar contexto JSON. identity={}, key={}", normalizedIdentity, key, ex);
        }
    }

    public void setMasterState(String userIdentity, String botIdentity, String operation) {
        if (userIdentity == null || userIdentity.isBlank()) return;

        String targetBot = botIdentity != null && !botIdentity.isBlank() ? botIdentity : blipAppointmentId;
        String masterIdentity = resolveMasterIdentity(userIdentity);
        String tunnelIdentity = resolveTunnelIdentity(userIdentity);

        if (masterIdentity != null && !masterIdentity.isBlank()) {
            sendSingleMasterState(masterIdentity, targetBot, operation);
        }
        if (tunnelIdentity != null && !tunnelIdentity.isBlank() && !tunnelIdentity.equalsIgnoreCase(masterIdentity)) {
            sendSingleMasterState(tunnelIdentity, targetBot, operation);
        }
    }

    private void sendSingleMasterState(String normalizedIdentity, String targetBot, String operation) {
        String stateId = operation != null && !operation.isBlank() ? operation : "stateid";
        String flowId = targetBot.contains("@") ? targetBot.substring(0, targetBot.indexOf('@')) : targetBot;
        String combined = stateId + "@" + flowId;
        String encodedState = java.net.URLEncoder.encode(combined, java.nio.charset.StandardCharsets.UTF_8);

        Map<String, Object> command = Map.of(
            "id", UUID.randomUUID().toString(),
            "to", BlipLIMEClient.MASTER_STATE_COMMAND_TO,
            "method", "set",
            "uri", "/contexts/" + normalizedIdentity + "/" + encodedState,
            "type", "text/plain",
            "metadata", Map.of("expiration", "86400"),
            "resource", targetBot
        );

        try {
            limeClient.executeCommand(command, BlipLIMEClient.AuthorizationScope.ROUTER);
            log.info("Master-State atualizado. identity={}, operation={}, targetBot={}", normalizedIdentity, operation, targetBot);
        } catch (org.springframework.web.client.RestClientException ex) {
            log.error("Erro ao atualizar Master-State. identity={}, operation={}", normalizedIdentity, operation, ex);
        }
    }

    public void setBuilderMasterState(String userIdentity, String stateId) {
        if (userIdentity == null || userIdentity.isBlank()) return;

        String masterIdentity = resolveMasterIdentity(userIdentity);
        String tunnelIdentity = resolveTunnelIdentity(userIdentity);

        if (masterIdentity != null && !masterIdentity.isBlank()) {
            sendSingleBuilderMasterState(masterIdentity, stateId);
        }
        if (tunnelIdentity != null && !tunnelIdentity.isBlank() && !tunnelIdentity.equalsIgnoreCase(masterIdentity)) {
            sendSingleBuilderMasterState(tunnelIdentity, stateId);
        }
    }

    private void sendSingleBuilderMasterState(String normalizedIdentity, String stateId) {
        Map<String, Object> command = Map.of(
            "id", UUID.randomUUID().toString(),
            "to", BlipLIMEClient.MASTER_STATE_COMMAND_TO,
            "method", "set",
            "uri", "/contexts/" + normalizedIdentity + "/master-state",
            "type", "text/plain",
            "metadata", Map.of("expiration", "86400"),
            "resource", stateId
        );

        try {
            limeClient.executeCommand(command, BlipLIMEClient.AuthorizationScope.ROUTER);
            log.info("[LIME] Builder Master-State atualizado para o bloco stateId={}, user={}", stateId, normalizedIdentity);
        } catch (org.springframework.web.client.RestClientException ex) {
            log.error("Erro ao atualizar Builder Master-State. stateId={}, user={}", stateId, normalizedIdentity, ex);
        }
    }

    public void setUserState(String userIdentity, String stateName) {
        String normalizedIdentity = limeClient.normalizeUserIdentity(userIdentity);

        Map<String, Object> command = Map.of(
                "id", UUID.randomUUID().toString(),
                "to", BlipLIMEClient.MASTER_STATE_COMMAND_TO,
                "method", "set",
                "uri", "/contexts/" + normalizedIdentity + "/state",
                "type", "text/plain",
                "resource", stateName
        );

        try {
            limeClient.executeCommand(command, BlipLIMEClient.AuthorizationScope.ROUTER);
            log.info("User State atualizado. stateName={}", stateName);
        } catch (org.springframework.web.client.RestClientException ex) {
            log.error("Erro ao atualizar User State. stateName={}", stateName, ex);
        }
    }

    public void processAppointmentPush(String userPhone, String action, AppointmentPayload payload) {
        try {
            String userPhoneClean = userPhone != null ? userPhone.trim() : "";
            String masterIdentity = null;
            String tunnelIdentity = null;

            if (userPhoneClean.contains("@tunnel.msging.net")) {
                tunnelIdentity = userPhoneClean;
                // Reconcilia para obter o telefone real do paciente
                String reconciledPhone = blipIdentityReconciler.resolveAndReconcileIdentity(userPhoneClean, null);
                if (reconciledPhone != null && !reconciledPhone.isBlank()) {
                    masterIdentity = reconciledPhone.contains("@") ? reconciledPhone : reconciledPhone + "@wa.gw.msging.net";
                }
            } else {
                // Se for um telefone comum, normaliza normalmente
                masterIdentity = limeClient.normalizeUserIdentity(userPhoneClean);
            }

            String resolvedQueue = resolveQueueName(payload.getQueue());

            java.util.Map<String, String> extras = new java.util.HashMap<>();
            extras.put("Medico", payload.getDoctorName());
            extras.put("fila", resolvedQueue);
            extras.put("deskFila", resolvedQueue);
            extras.put("nascimento", payload.getPatientBirthdate());
            extras.put("data_nascimento", payload.getPatientBirthdate());

            // Injeta o nome também nas chaves extras de texto que o fluxo lê
            extras.put("paciente", payload.getPatientName());
            extras.put("Nome", payload.getPatientName());

            String blipBirthDate = convertBirthdateToBlipFormat(payload.getPatientBirthdate());

            // PASSO 1a: Atualiza os dados do Contato no Roteador (usando a identidade real master)
            if (masterIdentity != null && !masterIdentity.isBlank()) {
                BlipContactUpdateCommand masterCommand = new BlipContactUpdateCommand();
                BlipContactUpdateCommand.ContactResource masterResource = new BlipContactUpdateCommand.ContactResource();
                masterResource.setIdentity(masterIdentity);
                masterResource.setName(payload.getPatientName());
                masterResource.setTaxDocument(payload.getPatientCPF());
                masterResource.setBirthDate(blipBirthDate);
                masterResource.setExtras(extras);
                masterCommand.setResource(masterResource);

                log.info("[LIME PUSH] Atualizando contato no ROTEADOR para identity={}: Medico={}, fila={}, birthDate={}", 
                        masterIdentity, payload.getDoctorName(), resolvedQueue, blipBirthDate);
                
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> masterMap = objectMapper.convertValue(masterCommand, java.util.Map.class);
                limeClient.executeCommand(masterMap, BlipLIMEClient.AuthorizationScope.ROUTER);
            }

            // PASSO 1b: Atualiza os dados do Contato no Subbot/Desk (usando a identidade de túnel)
            if (tunnelIdentity != null && !tunnelIdentity.isBlank()) {
                BlipContactUpdateCommand tunnelCommand = new BlipContactUpdateCommand();
                BlipContactUpdateCommand.ContactResource tunnelResource = new BlipContactUpdateCommand.ContactResource();
                tunnelResource.setIdentity(tunnelIdentity);
                tunnelResource.setName(payload.getPatientName());
                tunnelResource.setTaxDocument(payload.getPatientCPF());
                tunnelResource.setBirthDate(blipBirthDate);
                tunnelResource.setExtras(extras);
                tunnelCommand.setResource(tunnelResource);

                log.info("[LIME PUSH] Atualizando contato no SUBBOT/DESK para identity={}: Medico={}, fila={}, birthDate={}", 
                        tunnelIdentity, payload.getDoctorName(), resolvedQueue, blipBirthDate);
                
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> tunnelMap = objectMapper.convertValue(tunnelCommand, java.util.Map.class);
                limeClient.executeCommand(tunnelMap, BlipLIMEClient.AuthorizationScope.DESK);
            }

            // Roteamento delegado ao payload nativo do Blip Builder.
            log.info("[MENSAGERIA] Registro processado. Delegando roteamento ao payload nativo do Blip Builder para a identidade: {}", userPhoneClean);
        } catch (RuntimeException ex) {
            throw new RuntimeException("Falha ao executar orquestração de push no Blip", ex);
        }
    }

    private String convertBirthdateToBlipFormat(String birthdate) {
        if (birthdate == null || birthdate.isBlank()) {
            return null;
        }
        String clean = birthdate.trim();
        try {
            if (clean.matches("\\d{2}/\\d{2}/\\d{4}")) {
                java.time.LocalDate date = java.time.LocalDate.parse(clean, java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                return date.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")) + "T00:00:00Z";
            }
            if (clean.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return clean + "T00:00:00Z";
            }
        } catch (Exception ex) {
            log.warn("Falha ao converter data de nascimento para formato do Blip: {}", birthdate);
        }
        return null;
    }

    /**
     * Verifica se o contato possui um ticket de atendimento humano ativo ou aberto no Desk (live chat) do Blip.
     * Útil como barreira de segurança para pausar/abortar nudges automáticos durante atendimento humano.
     */
    public boolean hasActiveTicket(String userIdentity) {
        return hasActiveTicket(userIdentity, null);
    }

    /**
     * Verifica se o contato possui um ticket de atendimento humano ativo/aberto recente no Desk (live chat) do Blip.
     * Considera ativo apenas se criado/atualizado nas últimas 12 horas ou após a última notificação enviada.
     */
    public boolean hasActiveTicket(String userIdentity, LocalDateTime lastNotificationSentAt) {
        if (userIdentity == null || userIdentity.isBlank()) return false;
        String normalizedIdentity = limeClient.normalizeUserIdentity(userIdentity);

        Map<String, Object> command = Map.of(
            "id", UUID.randomUUID().toString(),
            "to", "postmaster@desk.msging.net",
            "method", "get",
            "uri", "/tickets?$filter=customerIdentity eq '" + normalizedIdentity + "' and (status eq 'Open' or status eq 'Waiting')"
        );

        try {
            Map<String, Object> response = limeClient.executeCommand(command, br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.client.BlipLIMEClient.AuthorizationScope.ROUTER);
            if (response == null) return false;
            Object resourceNode = response.get("resource");
            if (resourceNode instanceof Map<?, ?> resourceMap) {
                Object itemsNode = resourceMap.get("items");
                if (itemsNode instanceof java.util.Collection<?> itemsList) {
                    boolean hasActive = false;
                    LocalDateTime twelveHoursAgo = LocalDateTime.now().minusHours(12);

                    for (Object itemObj : itemsList) {
                        if (itemObj instanceof Map<?, ?> itemMap) {
                            Object statusVal = itemMap.get("status");
                            if (statusVal != null) {
                                String status = statusVal.toString().trim();
                                if ("Open".equalsIgnoreCase(status) || "Waiting".equalsIgnoreCase(status)) {
                                    LocalDateTime ticketTime = parseTicketTimestamp(itemMap);
                                    if (ticketTime == null) {
                                        hasActive = true;
                                        log.info("[ATTENDANCE-GUARD] Contato {} possui ticket sem data legível. Considerando ativo por segurança.", normalizedIdentity);
                                        break;
                                    }
                                    boolean isWithin12Hours = ticketTime.isAfter(twelveHoursAgo);
                                    boolean isAfterLastNotification = lastNotificationSentAt != null && ticketTime.isAfter(lastNotificationSentAt);

                                    if (isWithin12Hours || isAfterLastNotification) {
                                        hasActive = true;
                                        log.info("[ATTENDANCE-GUARD] Ticket ativo recente encontrado para {}. Data do ticket: {}", normalizedIdentity, ticketTime);
                                        break;
                                    } else {
                                        log.info("[ATTENDANCE-GUARD] Ticket antigo em aberto ignorado para {}. Data do ticket: {}", normalizedIdentity, ticketTime);
                                    }
                                }
                            }
                        }
                    }
                    if (hasActive) {
                        log.info("[ATTENDANCE-GUARD] Contato {} possui tickets de live chat ativos e recentes no Desk.", normalizedIdentity);
                    }
                    return hasActive;
                }
            }
            return false;
        } catch (Exception ex) {
            log.warn("[ATTENDANCE-GUARD] Falha ao verificar ticket ativo no Desk para {}: {}", normalizedIdentity, ex.getMessage());
            return false; // Fail-open para não travar os nudges normais em caso de falha de rede/autorização
        }
    }

    private LocalDateTime parseTicketTimestamp(Map<?, ?> itemMap) {
        Object val = itemMap.get("statusDate");
        if (val == null) val = itemMap.get("storageDate");
        if (val == null) val = itemMap.get("openDate");
        if (val == null) val = itemMap.get("updatedAt");
        if (val == null) val = itemMap.get("createdAt");

        if (val == null) return null;
        String str = val.toString().trim();
        if (str.isEmpty()) return null;

        try {
            return java.time.OffsetDateTime.parse(str).toLocalDateTime();
        } catch (Exception e1) {
            try {
                return java.time.LocalDateTime.parse(str);
            } catch (Exception e2) {
                try {
                    return java.time.LocalDateTime.parse(str.replace(" ", "T"));
                } catch (Exception e3) {
                    log.debug("Não foi possível converter data do ticket Blip: {}", str);
                    return null;
                }
            }
        }
    }

    public String resolveQueueName(String queueNameOrId) {
        if (queueNameOrId == null) return "";
        String resolvedQueueName = queueNameOrId;

        // Se o valor de entrada parecer com a estrutura de um UUID de fila, realiza a tradução dinâmica
        if (queueNameOrId.trim().matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")) {
            String uuid = queueNameOrId.trim();
            log.info("[QUEUE-RESOLVER] Detectado UUID de fila '{}'. Buscando nome descritivo correspondente na API do Blip...", uuid);
            try {
                java.util.List<br.dev.ctrls.inovareti.modules.appointment.domain.port.output.BlipClientPort.BlipQueue> queues = limeClient.listBlipQueues();
                String foundName = null;
                if (queues != null) {
                    for (var q : queues) {
                        if (uuid.equalsIgnoreCase(q.id())) {
                            foundName = q.name();
                            break;
                        }
                    }
                }
                if (foundName != null && !foundName.isBlank()) {
                    log.info("[QUEUE-RESOLVER] Traduzido UUID {} para o nome de pauta '{}'", uuid, foundName);
                    resolvedQueueName = foundName;
                } else {
                    log.warn("[QUEUE-RESOLVER] UUID {} não foi localizado na listagem de filas oficiais do Blip. Aplicando fallback de segurança.", uuid);
                    resolvedQueueName = "Recepção Central / Suporte";
                }
            } catch (Exception ex) {
                log.error("[QUEUE-RESOLVER] Falha na comunicação com a API do Blip ao resolver o UUID {}. Aplicando fallback de segurança.", uuid, ex);
                resolvedQueueName = "Recepção Central / Suporte";
            }
        }

        String safeQueueName = cleanQueueName(resolvedQueueName);
        return safeQueueName.isBlank() ? "Recepção Central / Suporte" : safeQueueName;
    }

    public boolean setQueueRedirect(String userIdentity, String queueNameOrId) {
        if (userIdentity == null || userIdentity.isBlank()) return false;
        String safeQueueName = resolveQueueName(queueNameOrId);
        String rawQueueId = (queueNameOrId != null && queueNameOrId.trim().matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"))
                ? queueNameOrId.trim() : null;

        if (!safeQueueName.isBlank()
                && !"Recepção Central / Suporte".equalsIgnoreCase(safeQueueName)
                && !"Recepção".equalsIgnoreCase(safeQueueName)
                && !safeQueueName.contains(" - ")) {
            log.warn("[QUEUE WARNING] Nome da fila pode estar incompleto para o Desk. fila='{}'", safeQueueName);
        }

        String masterIdentity = resolveMasterIdentity(userIdentity);
        String tunnelIdentity = resolveTunnelIdentity(userIdentity);

        if (masterIdentity != null && !masterIdentity.isBlank()) {
            setUserContext(masterIdentity, "attendanceQueueToRedirect", safeQueueName);
            setUserContext(masterIdentity, "fila", safeQueueName);
        }
        if (tunnelIdentity != null && !tunnelIdentity.isBlank() && !tunnelIdentity.equalsIgnoreCase(masterIdentity)) {
            setUserContext(tunnelIdentity, "attendanceQueueToRedirect", safeQueueName);
            setUserContext(tunnelIdentity, "fila", safeQueueName);
        }

        // Sincronização explícita dos extras do contato no Roteador e no Desk/Subbot
        updateContactExtras(userIdentity, safeQueueName, rawQueueId);

        log.info("Fila de redirecionamento e extras configurados no contexto (escopo dual). master={}, tunnel={}, fila={}", masterIdentity, tunnelIdentity, safeQueueName);
        return true;
    }

    public String cleanQueueName(String queueName) {
        if (queueName == null) return "";
        String cleaned = queueName.replace("\u200E", "");
        cleaned = cleaned.replaceAll("(?i)null", "");
        cleaned = cleaned.replaceAll("\\s+", " ");
        cleaned = cleaned.trim();
        return cleaned;
    }

    public void setVariable(String userIdentity, String key, String value) {
        setUserContext(userIdentity, key, value);
    }

    public void setContactExtra(String userIdentity, String key, String value) {
        if (userIdentity == null || userIdentity.isBlank()) return;
        try {
            updateContactExtras(userIdentity, Map.of(key, value));
        } catch (Exception ex) {
            log.warn("Falha ao definir extras do contato no Blip. identity={}, key={}, value={}", userIdentity, key, value, ex);
        }
    }

    public void updateContactExtras(String userIdentity, String queueName, String blipQueueId) {
        if (userIdentity == null || userIdentity.isBlank()) return;

        Map<String, String> extras = new java.util.HashMap<>();
        if (queueName != null && !queueName.isBlank()) {
            String clean = cleanQueueName(queueName);
            extras.put("fila", clean);
            extras.put("deskFila", clean);
        }
        if (blipQueueId != null && !blipQueueId.isBlank()) {
            extras.put("blipQueueId", blipQueueId.trim());
        }

        if (extras.isEmpty()) return;

        updateContactExtras(userIdentity, extras);
    }

    public void updateContactExtras(String userIdentity, Map<String, String> extras) {
        if (userIdentity == null || userIdentity.isBlank() || extras == null || extras.isEmpty()) return;

        String masterIdentity = resolveMasterIdentity(userIdentity);
        String tunnelIdentity = resolveTunnelIdentity(userIdentity);

        if (masterIdentity != null && !masterIdentity.isBlank()) {
            limeClient.mergeContactExtras(masterIdentity, extras, BlipLIMEClient.AuthorizationScope.ROUTER);
        }
        if (tunnelIdentity != null && !tunnelIdentity.isBlank()) {
            limeClient.mergeContactExtras(tunnelIdentity, extras, BlipLIMEClient.AuthorizationScope.DESK);
        }
    }
}

