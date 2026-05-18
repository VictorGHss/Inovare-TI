package br.dev.ctrls.inovareti.domain.appointment.service;

import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class BlipContextService {

    private final BlipLIMEClient limeClient;

    @Value("${APP_BLIP_APPOINTMENT_ID:}")
    private String blipAppointmentId;

    public BlipContextService(BlipLIMEClient limeClient) {
        this.limeClient = limeClient;
    }

    public void setUserContextForUser(String userIdentity, String key, String value) {
        if (userIdentity == null || userIdentity.isBlank()) return;
        String normalizedIdentity = limeClient.normalizeUserIdentity(userIdentity);
        setUserContext(normalizedIdentity, key, value);
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
            ResponseEntity<Map<String, Object>> response = limeClient.executeCommand(command, BlipLIMEClient.AuthorizationScope.ROUTER);
            Map<String, Object> body = response != null ? response.getBody() : null;
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
        } catch (RestClientException ex) {
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
        } catch (RestClientException ex) {
            log.warn("Falha ao configurar contexto. identity={}, key={}", normalizedIdentity, key, ex);
        }
    }

    /**
     * Envia um contexto JSON ao Blip via comando LIME.
     * O resource deve ser passado como Object (Map, record, etc.) para que o Jackson
     * o serialize como objeto nativo, e não como String com aspas extras.
     * O Blip retorna Code 21 (not a JSON) se o resource chegar como string escapada.
     */
    public void setJsonContext(String normalizedIdentity, String key, Object resourceObject) {
        if (normalizedIdentity == null || normalizedIdentity.isBlank() || resourceObject == null) return;

        java.util.LinkedHashMap<String, Object> command = new java.util.LinkedHashMap<>();
        command.put("id", UUID.randomUUID().toString());
        command.put("to", "postmaster@msging.net");
        command.put("method", "set");
        command.put("uri", "/contexts/" + normalizedIdentity + "/" + key);
        command.put("type", "application/json");
        command.put("metadata", Map.of("expiration", "86400"));
        command.put("resource", resourceObject); // objeto nativo, não String serializada

        try {
            limeClient.executeCommand(command, BlipLIMEClient.AuthorizationScope.ROUTER);
            log.info("[LIME] Contexto JSON configurado. identity={}, key={}", normalizedIdentity, key);
        } catch (RestClientException ex) {
            log.warn("[LIME] Falha ao configurar contexto JSON. identity={}, key={}", normalizedIdentity, key, ex);
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
        } catch (RestClientException ex) {
            log.error("Erro ao atualizar Master-State. operation={}", operation, ex);
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
        } catch (RestClientException ex) {
            log.error("Erro ao atualizar User State. stateName={}", stateName, ex);
        }
    }

    public void setUserState(String userIdentity, String flowId, String blockId) {
        String normalizedIdentity = limeClient.normalizeUserIdentity(userIdentity);
        String uri = "/contexts/" + normalizedIdentity + "/stateid@" + flowId;

        Map<String, Object> command = Map.of(
                "id", UUID.randomUUID().toString(),
                "to", BlipLIMEClient.MASTER_STATE_COMMAND_TO,
                "method", "set",
                "uri", uri,
                "type", "text/plain",
                "resource", blockId
        );

        try {
            limeClient.executeCommand(command, BlipLIMEClient.AuthorizationScope.ROUTER);
            log.info("User State (Teletransporte Interno) atualizado. flowId={}, blockId={}", flowId, blockId);
        } catch (RestClientException ex) {
            log.error("Erro ao atualizar User State (Teletransporte Interno). flowId={}", flowId, ex);
        }
    }

    public boolean setQueueRedirect(String userIdentity, String queueName) {
        String normalizedIdentity = limeClient.normalizeUserIdentity(userIdentity);

        String safeQueueName = cleanQueueName(queueName);
        if (safeQueueName.isBlank()) {
            safeQueueName = "Recepção Central / Suporte";
            log.warn("[QUEUE] Nome de fila inválido ('{}') substituído por fallback: '{}'", queueName, safeQueueName);
        }

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
        } catch (RestClientException ex) {
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
        } catch (RestClientException ex) {
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