package br.dev.ctrls.inovareti.domain.appointment.dto;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * DTO para apresentação de templates aprovados ao frontend com contagem de variáveis.
 */
public record BlipTemplateDto(
        String id,
        String name,
        String body) {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{\\d+\\}\\}");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public int getVariableCount() {
        return switch (this.name) {
            case "confirmacao_consulta_v5" -> 4;
            case "aviso_interacao_necessariav1", "aviso_final_cancelamento" -> 0;
            default -> countVariablesFromBody();
        };
    }

    private int countVariablesFromBody() {
        String bodyText = extractBodyText(this.body);
        if (bodyText == null || bodyText.isBlank()) {
            return 0;
        }

        int count = 0;
        var matcher = VARIABLE_PATTERN.matcher(bodyText);
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private static String extractBodyText(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return null;
        }

        String trimmed = rawBody.trim();
        if (!(trimmed.startsWith("{") || trimmed.startsWith("["))) {
            return trimmed;
        }

        try {
            Map<String, Object> root = OBJECT_MAPPER.readValue(trimmed, new TypeReference<Map<String, Object>>() { });
            List<?> components = resolveComponentsList(root);
            if (components != null) {
                for (Object component : components) {
                    if (component instanceof Map<?, ?> comp) {
                        Object type = comp.get("type");
                        if ("BODY".equalsIgnoreCase(type != null ? type.toString() : "")) {
                            Object text = comp.get("text");
                            return text != null ? text.toString() : null;
                        }
                    }
                }
            }
        } catch (JsonProcessingException ex) {
            return rawBody;
        }
        return rawBody;
    }

    @SuppressWarnings("unchecked")
    private static List<?> resolveComponentsList(Map<String, Object> root) {
        if (root == null) {
            return null;
        }
        Object c = root.get("components");
        if (c instanceof List<?> list) {
            return list;
        }
        Object template = root.get("template");
        if (template instanceof Map<?, ?> tm) {
            Object c2 = ((Map<String, Object>) tm).get("components");
            if (c2 instanceof List<?> list2) {
                return list2;
            }
        }
        Object content = root.get("content");
        if (content instanceof Map<?, ?> cm) {
            Object c3 = ((Map<String, Object>) cm).get("components");
            if (c3 instanceof List<?> list3) {
                return list3;
            }
        }
        return null;
    }
}
