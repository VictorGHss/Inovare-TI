package br.dev.ctrls.inovareti.domain.appointment.dto;

import java.util.regex.Pattern;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
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
                // Fallback manual para templates específicos conhecidos
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
                if (rawBody == null || rawBody.isBlank()) return null;

                String trimmed = rawBody.trim();
                if (!(trimmed.startsWith("{") || trimmed.startsWith("["))) return trimmed;

                try {
                        JsonNode root = OBJECT_MAPPER.readTree(trimmed);
                        JsonNode components = resolveComponentsNode(root);
                        if (components != null && components.isArray()) {
                                for (JsonNode component : components) {
                                        if ("BODY".equalsIgnoreCase(component.path("type").asText())) {
                                                return component.path("text").asText(null);
                                        }
                                }
                        }
                } catch (JsonProcessingException ex) {
                        return rawBody;
                }
                return rawBody;
        }

        private static JsonNode resolveComponentsNode(JsonNode root) {
                if (root == null) return null;
                if (root.path("components").isArray()) return root.path("components");
                if (root.path("template").path("components").isArray()) return root.path("template").path("components");
                if (root.path("content").path("components").isArray()) return root.path("content").path("components");
                return null;
        }
}