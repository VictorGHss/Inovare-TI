package br.dev.ctrls.inovareti.modules.appointment.application.service;

import io.micrometer.observation.annotation.Observed;

import java.util.Map;
import java.util.UUID;


import br.dev.ctrls.inovareti.modules.appointment.application.dto.AppointmentPayload;
import br.dev.ctrls.inovareti.modules.appointment.application.dto.BlipContactUpdateCommand;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.client.BlipLIMEClient;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@org.springframework.stereotype.Service
@Observed
public class BlipContextService {

    private final BlipLIMEClient limeClient;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Value("${APP_BLIP_APPOINTMENT_ID:}")
    private String blipAppointmentId;

    public BlipContextService(BlipLIMEClient limeClient, com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.limeClient = limeClient;
        this.objectMapper = objectMapper;
    }

    public void setUserContextForUser(String userIdentity, String key, String value) {
        if (userIdentity == null || userIdentity.isBlank()) return;
        String normalizedIdentity = limeClient.normalizeUserIdentity(userIdentity);
        setUserContext(normalizedIdentity, key, value);
    }

    public void setUserContextFieldsInParallel(String userIdentity, Map<String, String> fields) {
        if (userIdentity == null || userIdentity.isBlank() || fields == null || fields.isEmpty()) {
            return;
        }
        String normalizedIdentity = limeClient.normalizeUserIdentity(userIdentity);
        log.info("[LIME-SEQUENCE] Configurando contexto LIME sequencialmente para target: {}. Campos: {}", normalizedIdentity, fields.keySet());
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            try {
                setUserContext(normalizedIdentity, entry.getKey(), entry.getValue());
            } catch (Exception e) {
                log.error("Erro ao configurar contexto sequencial para {} key: {}", normalizedIdentity, entry.getKey(), e);
            }
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

    public void setUserContext(String normalizedIdentity, String key, String value) {
        if (normalizedIdentity == null || normalizedIdentity.isBlank() || value == null || value.isBlank()) return;

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
            log.info("Contexto configurado. identity={}, key={}", normalizedIdentity, key);
        } catch (org.springframework.web.client.RestClientException ex) {
            log.warn("Falha ao configurar contexto. identity={}, key={}", normalizedIdentity, key, ex);
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
    public void setJsonContext(String normalizedIdentity, String key, Object resourceObject) {
        if (normalizedIdentity == null || normalizedIdentity.isBlank() || resourceObject == null) return;

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
            log.info("[LIME] Contexto JSON configurado como text/plain. identity={}, key={}", normalizedIdentity, key);
        } catch (com.fasterxml.jackson.core.JsonProcessingException | org.springframework.web.client.RestClientException ex) {
            log.warn("[LIME] Falha ao configurar contexto JSON como text/plain. identity={}, key={}", normalizedIdentity, key, ex);
        }
    }

    public void setMasterState(String userIdentity, String botIdentity, String operation) {
        String targetBot = botIdentity != null && !botIdentity.isBlank() ? botIdentity : blipAppointmentId;
        String normalizedIdentity = limeClient.normalizeUserIdentity(userIdentity);

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
            log.info("Master-State atualizado. operation={}, targetBot={}", operation, targetBot);
        } catch (org.springframework.web.client.RestClientException ex) {
            log.error("Erro ao atualizar Master-State. operation={}", operation, ex);
        }
    }

    public void setBuilderMasterState(String userIdentity, String stateId) {
        String normalizedIdentity = limeClient.normalizeUserIdentity(userIdentity);

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
            log.error("Erro ao atualizar Builder Master-State. stateId={}", stateId, ex);
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



    private void sendToBlipCommandsApi(Object commandPayload) {
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> commandMap = objectMapper.convertValue(commandPayload, java.util.Map.class);
        limeClient.executeCommand(commandMap, BlipLIMEClient.AuthorizationScope.ROUTER);
    }

    public void processAppointmentPush(String userPhone, String action, AppointmentPayload payload) {
        try {
            String normalizedPhone = limeClient.normalizeUserIdentity(userPhone);
            String userIdentity = normalizedPhone; // A identidade já possui o domínio correto pós-normalização

            String resolvedQueue = resolveQueueName(payload.getQueue());

            // PASSO 1: Atualiza os dados do Contato no Roteador (Garante consistência imediata)
            BlipContactUpdateCommand contactCommand = new BlipContactUpdateCommand();
            BlipContactUpdateCommand.ContactResource contactResource = new BlipContactUpdateCommand.ContactResource();
            contactResource.setIdentity(userIdentity);
            contactResource.setName(payload.getPatientName());
            contactResource.setTaxDocument(payload.getPatientCPF());

            java.util.Map<String, String> extras = new java.util.HashMap<>();
            extras.put("Medico", payload.getDoctorName());
            extras.put("fila", resolvedQueue);
            extras.put("nascimento", payload.getPatientBirthdate());
            extras.put("data_nascimento", payload.getPatientBirthdate());
            contactResource.setExtras(extras);
            contactCommand.setResource(contactResource);

            // POST para /commands
            log.info("[LIME PUSH] Passo 1: Atualizando contato para identity={}: Medico={}, fila={}", userIdentity, payload.getDoctorName(), resolvedQueue);
            sendToBlipCommandsApi(contactCommand);

            // Roteamento delegado ao payload nativo do Blip Builder.
            // Os Passos 3 e 4 (Master-State e stateid) foram removidos intencionalmente:
            // o próprio Blip detecta 'confirm_' ou 'alter_' no payload do botão e faz o desvio
            // de fluxo de forma nativa, eliminando a disputa de estado com o Roteador principal.
            log.info("[MENSAGERIA] Registro processado. Delegando roteamento ao payload nativo do Blip Builder para a identidade: {}", userIdentity);
        } catch (Exception e) {
            throw new RuntimeException("Falha ao executar orquestração de push no Blip", e);
        }
    }

    /**
     * Verifica se o contato possui um ticket de atendimento humano ativo ou aberto no Desk (live chat) do Blip.
     * Útil como barreira de segurança para pausar/abortar nudges automáticos durante atendimento humano.
     */
    public boolean hasActiveTicket(String userIdentity) {
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
                    boolean hasActive = !itemsList.isEmpty();
                    if (hasActive) {
                        log.info("[ATTENDANCE-GUARD] Contato {} possui {} tickets de live chat ativos no Desk.", normalizedIdentity, itemsList.size());
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
        String normalizedIdentity = limeClient.normalizeUserIdentity(userIdentity);
        String safeQueueName = resolveQueueName(queueNameOrId);

        if (!safeQueueName.isBlank()
                && !"Recepção Central / Suporte".equalsIgnoreCase(safeQueueName)
                && !"Recepção".equalsIgnoreCase(safeQueueName)
                && !safeQueueName.contains(" - ")) {
            log.warn("[QUEUE WARNING] Nome da fila pode estar incompleto para o Desk. fila='{}'", safeQueueName);
        }

        Map<String, Object> deleteCommand = Map.of(
            "id", UUID.randomUUID().toString(),
            "to", BlipLIMEClient.MASTER_STATE_COMMAND_TO,
            "method", "delete",
            "uri", "/contexts/" + normalizedIdentity + "/attendanceQueueToRedirect"
        );

        try {
            limeClient.executeCommand(deleteCommand, BlipLIMEClient.AuthorizationScope.ROUTER);
            log.debug("[QUEUE] Contexto anterior removido. identity={}", normalizedIdentity);
        } catch (org.springframework.web.client.RestClientException ex) {
            log.warn("[QUEUE] Falha ao limpar contexto anterior. identity={}", normalizedIdentity, ex);
        }

        Map<String, Object> command = Map.of(
            "id", UUID.randomUUID().toString(),
            "to", BlipLIMEClient.MASTER_STATE_COMMAND_TO,
            "method", "set",
            "uri", "/contexts/" + normalizedIdentity + "/attendanceQueueToRedirect",
            "type", "text/plain",
            "resource", safeQueueName
        );

        try {
            limeClient.executeCommand(command, BlipLIMEClient.AuthorizationScope.ROUTER);
            log.info("Fila de redirecionamento configurada no contexto. identity={}, fila={}", normalizedIdentity, safeQueueName);
            return true;
        } catch (org.springframework.web.client.RestClientException ex) {
            log.warn("Falha ao configurar fila no contexto. identity={}, fila={}", normalizedIdentity, safeQueueName, ex);
            return false;
        }
    }

    public String cleanQueueName(String queueName) {
        if (queueName == null) return "";
        String cleaned = queueName.replace("\u200E", "");
        cleaned = cleaned.replaceAll("(?i)null", "");
        cleaned = cleaned.replaceAll("\\s+", " ");
        cleaned = cleaned.trim();
        return cleaned;
    }
}

