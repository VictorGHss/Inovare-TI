package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

/**
 * Utilitario para leitura segura de JsonNode com suporte a caminhos aninhados.
 */
@Component
@RequiredArgsConstructor
public class JsonSafeReader {

    private final ObjectMapper objectMapper;

    public String readText(JsonNode node, String... paths) {
        for (String path : paths) {
            JsonNode current = node;
            for (String segment : path.split("\\.")) {
                if (current == null) {
                    break;
                }

                if (current.isArray()) {
                    int index;
                    try {
                        index = Integer.parseInt(segment);
                    } catch (NumberFormatException ex) {
                        current = null;
                        break;
                    }

                    current = index >= 0 && index < current.size() ? current.get(index) : null;
                    continue;
                }

                current = current.get(segment);
            }

            if (current != null && !current.isNull()) {
                String value = current.asText();
                if (StringUtils.hasText(value)) {
                    return value;
                }
            }
        }

        return null;
    }

    public Long readLong(JsonNode node, String... paths) {
        String value = readText(node, paths);
        if (!StringUtils.hasText(value)) {
            return null;
        }

        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public JsonNode readArrayNode(JsonNode node, String... paths) {
        for (String path : paths) {
            JsonNode current = node;
            for (String segment : path.split("\\.")) {
                if (current == null) {
                    break;
                }

                current = current.get(segment);
            }

            if (current != null && current.isArray()) {
                return current;
            }
        }

        return null;
    }

    public JsonNode resolveArrayNode(JsonNode root) {
        if (root == null || root.isNull()) {
            return objectMapper.createArrayNode();
        }

        if (root.isArray()) {
            return root;
        }

        if (root.has("data") && root.get("data").isArray()) {
            return root.get("data");
        }

        if (root.has("itens") && root.get("itens").isArray()) {
            return root.get("itens");
        }

        if (root.has("items") && root.get("items").isArray()) {
            return root.get("items");
        }

        if (root.has("content") && root.get("content").isArray()) {
            return root.get("content");
        }

        if (root.has("content") && root.get("content").isObject()) {
            JsonNode content = root.get("content");

            if (content.has("items") && content.get("items").isArray()) {
                return content.get("items");
            }

            if (content.has("data") && content.get("data").isArray()) {
                return content.get("data");
            }

            if (content.has("itens") && content.get("itens").isArray()) {
                return content.get("itens");
            }
        }

        return objectMapper.createArrayNode();
    }
}
