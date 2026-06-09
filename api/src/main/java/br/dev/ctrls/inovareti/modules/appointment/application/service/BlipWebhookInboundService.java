package br.dev.ctrls.inovareti.modules.appointment.application.service;

import io.micrometer.observation.annotation.Observed;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Extrai campos do JSON do webhook Blip a partir de {@link Map} (binding seguro com Jackson 3).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Observed
public class BlipWebhookInboundService {

    private static final String LAST_PENDING_APPOINTMENT_ID_CONTEXT_KEY = "last_pending_appointment_id";

    private final BlipContextService blipContextService;
    private final ObjectMapper objectMapper;

    public record ParsedInbound(
            String from,
            String action,
            String messageId,
            String appointmentId,
            Object content,
            String bsuid,
            String type,
            boolean isOutbound,
            String rawFrom) {
        public ParsedInbound(String from, String action, String messageId, String appointmentId, Object content, String bsuid) {
            this(from, action, messageId, appointmentId, content, bsuid, null, false, null);
        }
    }

    public ParsedInbound parse(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return new ParsedInbound(null, null, null, null, null, null, null, false, null);
        }

        String messageType = firstNonBlank(
                asText(getNested(payload, "type")),
                asText(getNested(payload, "resource", "type")),
                asText(getNested(payload, "message", "type")));

        String from = extractFrom(payload);
        String action = extractActionText(payload, messageType);
        String messageId = extractMessageId(payload);
        String appointmentId = extractAppointmentId(payload);
        String bsuid = extractBsuid(payload);

        if (!org.springframework.util.StringUtils.hasText(action) || "Ver Agendamentos".equals(action.trim())) {
            String messageText = firstNonBlank(
                    asText(getNested(payload, "content", "text")),
                    asText(getNested(payload, "content")),
                    asText(getNested(payload, "resource", "content", "text")),
                    asText(getNested(payload, "resource", "content")),
                    asText(getNested(payload, "message", "content", "text")),
                    asText(getNested(payload, "message", "content"))
            );
            if (messageText != null && "Ver Agendamentos".equals(messageText.trim())) {
                action = "group_view_fallback";
            }
        }

        String breadcrumbAppointmentId = resolveBreadcrumbAppointmentId(action, from);
        if (breadcrumbAppointmentId != null) {
            appointmentId = breadcrumbAppointmentId;
            action = formatActionFromBreadcrumb(action, breadcrumbAppointmentId);
        }

        Object content = firstNonNullValue(
                getNested(payload, "content"),
                getNested(payload, "resource", "content"),
                getNested(payload, "message", "content"));

        String rawFrom = firstNonBlank(
                asText(getNested(payload, "from")),
                asText(getNested(payload, "resource", "from")),
                asText(getNested(payload, "message", "from")));

        return new ParsedInbound(from, action, messageId, appointmentId, content, bsuid, messageType, checkIsOutbound(payload), rawFrom);
    }

    private String extractBsuid(Map<String, Object> payload) {
        String[] keys = {
            "#wa.bsuid", "wa.bsuid", "bsuid", 
            "#tunnel.originalFrom", "tunnel.originalFrom", 
            "#tunnel.originator", "tunnel.originator"
        };
        
        String[] paths = {
            "metadata", 
            "envelope/metadata", 
            "message/metadata", 
            "resource/metadata", 
            "resource/envelope/metadata", 
            "resource/message/metadata"
        };
        
        for (String path : paths) {
            String[] splitPath = path.split("/");
            for (String key : keys) {
                String[] fullPath = new String[splitPath.length + 1];
                System.arraycopy(splitPath, 0, fullPath, 0, splitPath.length);
                fullPath[splitPath.length] = key;
                
                String val = asText(getNested(payload, fullPath));
                if (val != null && !val.isBlank()) {
                    return val;
                }
            }
        }
        
        for (String key : keys) {
            String val = asText(getNested(payload, "metadata", key));
            if (val != null && !val.isBlank()) {
                return val;
            }
        }
        
        return null;
    }

    private String extractFrom(Map<String, Object> payload) {
        String identity = firstNonBlank(
                asText(getNested(payload, "identity")),
                asText(getNested(payload, "resource", "identity")),
                asText(getNested(payload, "message", "identity")));

        String category = firstNonBlank(
                asText(getNested(payload, "category")),
                asText(getNested(payload, "resource", "category")),
                asText(getNested(payload, "message", "category")));

        boolean isFlow = "flow".equalsIgnoreCase(category);

        if (StringUtils.hasText(identity)) {
            return identity;
        }

        if (isFlow) {
            return identity;
        }

        String from = firstNonBlank(
                asText(getNested(payload, "from")),
                asText(getNested(payload, "resource", "from")),
                asText(getNested(payload, "message", "from")));

        String originator = firstNonBlank(
                asText(getNested(payload, "metadata", "#tunnel.originator")),
                asText(getNested(payload, "envelope", "metadata", "#tunnel.originator")),
                asText(getNested(payload, "message", "metadata", "#tunnel.originator")),
                asText(getNested(payload, "resource", "metadata", "#tunnel.originator")),
                asText(getNested(payload, "resource", "envelope", "metadata", "#tunnel.originator")),
                asText(getNested(payload, "resource", "message", "metadata", "#tunnel.originator")));

        if (StringUtils.hasText(originator)) {
            return originator;
        }

        return from;
    }

    private String extractActionText(Map<String, Object> payload, String messageType) {
        // Tenta extrair a ação do metadado interativo do WhatsApp (#whatsapp.interactiveReply)
        String interactiveReply = firstNonBlank(
                asText(getNested(payload, "metadata", "#whatsapp.interactiveReply")),
                asText(getNested(payload, "envelope", "metadata", "#whatsapp.interactiveReply")),
                asText(getNested(payload, "message", "metadata", "#whatsapp.interactiveReply")),
                asText(getNested(payload, "resource", "metadata", "#whatsapp.interactiveReply")),
                asText(getNested(payload, "resource", "envelope", "metadata", "#whatsapp.interactiveReply")),
                asText(getNested(payload, "resource", "message", "metadata", "#whatsapp.interactiveReply")));

        if (interactiveReply != null && !interactiveReply.isBlank()) {
            try {
                Map<?, ?> replyMap = objectMapper.readValue(interactiveReply, Map.class);
                Object idObj = replyMap.get("id");
                if (idObj != null) {
                    String extracted = idObj.toString().trim();
                    log.info("[WEBHOOK] action extraído do #whatsapp.interactiveReply: '{}'", extracted);
                    return extracted;
                }
            } catch (Exception e) {
                log.warn("[WEBHOOK] Falha ao parsear #whatsapp.interactiveReply JSON: {}", interactiveReply, e);
            }
        }

        // WhatsApp / LIME: clique em botão rápido (application/vnd.lime.reply+json) — valor em content.replied.value
        String replyButtonAction = firstNonBlank(
                asText(getNested(payload, "content", "replied", "value")),
                asText(getNested(payload, "resource", "content", "replied", "value")),
                asText(getNested(payload, "message", "content", "replied", "value")));
        if (replyButtonAction != null) {
            log.debug("[WEBHOOK] action extraído (reply+json content.replied.value): '{}'", replyButtonAction);
            return replyButtonAction;
        }

        Object contentNode = firstNonNullValue(
                getNested(payload, "content"),
                getNested(payload, "resource", "content"));

        Map<String, Object> contentMap = asMap(contentNode);
        if (contentMap != null) {
            if ("application/vnd.lime.reply+json".equalsIgnoreCase(messageType)) {
                String fromReplied = firstNonBlank(asText(getNested(contentMap, "replied", "value")));
                if (fromReplied != null) {
                    log.debug("[WEBHOOK] action extraído (reply+json via content map): '{}'", fromReplied);
                    return fromReplied;
                }
            }
            if ("application/vnd.lime.select+json".equalsIgnoreCase(messageType)) {
                String selectValue = firstNonBlank(
                        asText(contentMap.get("value")),
                        asText(contentMap.get("payload")));
                if (selectValue != null && !selectValue.isBlank()) {
                    log.debug("[WEBHOOK] action extraído (select+json value): '{}'", selectValue);
                    return selectValue;
                }
                String selectText = firstNonBlank(asText(contentMap.get("text")));
                if (selectText != null) {
                    log.debug("[WEBHOOK] action extraído (select+json text): '{}'", selectText);
                    return selectText;
                }
            }
            String objectText = firstNonBlank(asText(contentMap.get("text")));
            if (objectText != null) {
                log.debug("[WEBHOOK] action extraído (text): '{}'", objectText);
                return objectText;
            }
            String selectValue = firstNonBlank(
                    asText(contentMap.get("value")),
                    asText(contentMap.get("payload")));
            if (selectValue != null) {
                log.debug("[WEBHOOK] action extraído: '{}'", selectValue);
                return selectValue;
            }
        }

        String action = firstNonBlank(
                asText(getNested(payload, "action")),
                asText(getNested(payload, "content", "payload")),
                asText(getNested(payload, "content")),
                asText(getNested(payload, "content", "text")),
                asText(getNested(payload, "content", "title")),
                asText(getNested(payload, "resource", "action")),
                asText(getNested(payload, "resource", "content", "payload")),
                asText(getNested(payload, "resource", "content")),
                asText(getNested(payload, "resource", "content", "text")),
                asText(getNested(payload, "resource", "content", "title")));
        log.debug("[WEBHOOK] action extraído: '{}'", action);
        return action;
    }

    private String extractMessageId(Map<String, Object> payload) {
        return firstNonBlank(
                asText(getNested(payload, "id")),
                asText(getNested(payload, "message", "id")),
                UUID.randomUUID().toString());
    }

    private String extractAppointmentId(Map<String, Object> payload) {
        return firstNonBlank(
                asText(getNested(payload, "appointmentId")),
                asText(getNested(payload, "metadata", "appointmentId")),
                asText(getNested(payload, "envelope", "metadata", "appointmentId")),
                asText(getNested(payload, "resource", "appointmentId")),
                asText(getNested(payload, "resource", "metadata", "appointmentId")),
                asText(getNested(payload, "resource", "content", "appointmentId")));
    }

    private String resolveBreadcrumbAppointmentId(String action, String from) {
        if (!StringUtils.hasText(action) || !StringUtils.hasText(from)) {
            return null;
        }
        String normalizedAction = action.trim();
        if (!isBreadcrumbAction(normalizedAction)) {
            return null;
        }

        String breadcrumbId = blipContextService.getUserContext(from, LAST_PENDING_APPOINTMENT_ID_CONTEXT_KEY);
        if (!StringUtils.hasText(breadcrumbId)) {
            log.warn("[WEBHOOK] Contexto '{}' não encontrado para {}", LAST_PENDING_APPOINTMENT_ID_CONTEXT_KEY, from);
            return null;
        }
        return breadcrumbId.trim();
    }

    private String formatActionFromBreadcrumb(String action, String appointmentId) {
        if (!StringUtils.hasText(action) || !StringUtils.hasText(appointmentId)) {
            return action;
        }
        String normalizedAction = action.trim();
        if (isConfirmAction(normalizedAction)) {
            return "confirm_" + appointmentId.trim();
        }
        if (isAlterAction(normalizedAction)) {
            return "alter_" + appointmentId.trim();
        }
        return action;
    }

    private boolean isBreadcrumbAction(String action) {
        return isConfirmAction(action) || isAlterAction(action);
    }

    private boolean isConfirmAction(String action) {
        return "Confirmar Presença".equalsIgnoreCase(action);
    }

    private boolean isAlterAction(String action) {
        return "Solicitar Alteração".equalsIgnoreCase(action)
                || "Solicitar Alteracao".equalsIgnoreCase(action);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> cast = (Map<String, Object>) map;
            return cast;
        }
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.convertValue(value, new TypeReference<Map<String, Object>>() { });
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private Object firstNonNullValue(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object v : values) {
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    private Object getNested(Map<String, Object> root, String... path) {
        Object current = root;
        if (current == null || path == null) {
            return null;
        }
        for (String key : path) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(key);
        }
        return current;
    }

    private String asText(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            return text;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        return null;
    }

    private boolean checkIsOutbound(Map<String, Object> payload) {
        String direction = firstNonBlank(
                asText(getNested(payload, "direction")),
                asText(getNested(payload, "resource", "direction")),
                asText(getNested(payload, "message", "direction")));
        if ("out".equalsIgnoreCase(direction)) {
            return true;
        }

        String to = firstNonBlank(
                asText(getNested(payload, "to")),
                asText(getNested(payload, "resource", "to")),
                asText(getNested(payload, "message", "to")));
        if (to != null && (to.contains("@wa.gw.msging.net") || to.contains("@desk.msging.net") || to.contains("tunnel.msging.net"))) {
            return true;
        }

        String rawFrom = firstNonBlank(
                asText(getNested(payload, "from")),
                asText(getNested(payload, "resource", "from")),
                asText(getNested(payload, "message", "from")));
        if (rawFrom != null && (rawFrom.contains("roteadorprincipal57@msging.net") || rawFrom.toLowerCase().startsWith("roteadorprincipal"))) {
            return true;
        }

        String id = firstNonBlank(
                asText(getNested(payload, "id")),
                asText(getNested(payload, "resource", "id")),
                asText(getNested(payload, "message", "id")));
        return id != null && id.startsWith("fwd:");
    }
}


