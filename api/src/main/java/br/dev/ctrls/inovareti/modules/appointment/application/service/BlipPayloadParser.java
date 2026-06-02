package br.dev.ctrls.inovareti.modules.appointment.application.service;

import java.util.Map;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Utilitário responsável por analisar payloads do Blip e extrair metadados úteis para o roteamento do webhook.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BlipPayloadParser {

    private final ObjectMapper objectMapper;

    /**
     * Tenta identificar e extrair a ação do usuário de dentro do payload 'content'.
     */
    public String resolveActionFromContent(Object content) {
        if (content == null) {
            return null;
        }
        if (content instanceof String text) {
            return text;
        }
        Map<String, Object> contentMap = toMap(content);
        if (contentMap == null) {
            return null;
        }
        String replyValue = null;
        Object replied = contentMap.get("replied");
        if (replied instanceof Map<?, ?> repliedMap) {
            replyValue = asText(repliedMap.get("value"));
        }
        return firstNonBlank(
            replyValue,
            asText(contentMap.get("text")),
            asText(contentMap.get("value")),
            asText(contentMap.get("payload")),
            asText(contentMap.get("id"))
        );
    }

    /**
     * Retorna o primeiro valor não-nulo e não-vazio.
     */
    public String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank() && !"null".equalsIgnoreCase(value)) {
                return value;
            }
        }
        return null;
    }

    /**
     * Converte um objeto para String se aplicável.
     */
    public String asText(Object value) {
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

    /**
     * Converte o valor para um Map genérico usando Jackson.
     */
    public Map<String, Object> toMap(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> cast = (Map<String, Object>) map;
            return cast;
        }
        try {
            return objectMapper.convertValue(value, new TypeReference<Map<String, Object>>() { });
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * Normaliza e purifica o ID de consulta da Feegow removendo casas decimais desnecessárias.
     */
    public String normalizeFeegowAppointmentId(String feegowAppointmentId) {
        if (feegowAppointmentId == null) {
            return "";
        }
        String normalized = feegowAppointmentId.trim();
        if (normalized.matches("^\\d+\\.0+$")) {
            return normalized.substring(0, normalized.indexOf('.'));
        }
        return normalized;
    }

    /**
     * Resolve a identidade real para o disparo de mensagens.
     */
    public String resolveDispatchIdentity(String from, AppointmentSession session) {
        String direct = normalizeDispatchIdentity(from);
        if (direct != null) {
            return direct;
        }
        String sessionPhone = session != null ? session.getPhoneNumber() : null;
        String fallback = normalizeDispatchIdentity(sessionPhone);
        if (fallback == null) {
            log.warn("Identidade de disparo inválida. from={}, sessionPhone={}", from, sessionPhone);
        }
        return fallback;
    }

    /**
     * Normaliza a identidade de disparo.
     */
    public String normalizeDispatchIdentity(String identity) {
        if (identity == null || identity.isBlank()) {
            return null;
        }
        return identity.trim();
    }
}
