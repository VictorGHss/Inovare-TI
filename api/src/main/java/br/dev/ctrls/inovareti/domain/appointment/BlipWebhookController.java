package br.dev.ctrls.inovareti.domain.appointment;

import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.dev.ctrls.inovareti.domain.appointment.usecase.HandleBlipWebhookUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/v1/webhook")
@RequiredArgsConstructor
public class BlipWebhookController {

    private final ObjectMapper objectMapper;
    private final HandleBlipWebhookUseCase handleBlipWebhookUseCase;

    @PostMapping("/blip")
    public ResponseEntity<Map<String, Object>> blipWebhook(
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-Inovare-Token", required = false) String inovareToken,
            HttpServletRequest request) {

        // ===== LOG DE DIAGNÓSTICO BRUTO =====
        // Lê o corpo diretamente do InputStream antes de qualquer parsing.
        // Se esta linha não aparecer, o Blip não está chegando no Java.
        String rawPayload;
        try {
            byte[] bodyBytes = StreamUtils.copyToByteArray(request.getInputStream());
            rawPayload = new String(bodyBytes, java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            log.error("[WEBHOOK] Falha ao ler o corpo da requisição: {}", e.getMessage());
            rawPayload = "";
        }

        // Loga headers relevantes para diagnóstico
        StringBuilder headers = new StringBuilder();
        java.util.Enumeration<String> headerNames = request.getHeaderNames();
        if (headerNames != null) {
            while (headerNames.hasMoreElements()) {
                String name = headerNames.nextElement();
                headers.append(name).append("=").append(request.getHeader(name)).append("; ");
            }
        }
        log.info("[WEBHOOK] HEADERS: {}", headers);
        log.info("[WEBHOOK] RAW BODY: {}", rawPayload);
        // ===== FIM LOG BRUTO =====

        JsonNode payload = parsePayload(rawPayload);

        if (payload == null || payload.isNull()) {
            log.warn("Blip webhook received without body at /v1/webhook/blip.");
            return ResponseEntity.accepted().body(Map.of(
                    "status", "ignored",
                    "reason", "body-empty"));
        }

        String from       = extractFrom(payload);
        String action     = extractActionText(payload);
        String messageId  = extractMessageId(payload);
        String appointmentId = extractAppointmentId(payload);

        // Log de auditoria estruturado
        log.info("[WEBHOOK RECEBIDO] from='{}' | action='{}' | messageId='{}' | appointmentId='{}'",
            from, action, messageId, appointmentId);

        handleBlipWebhookUseCase.execute(new HandleBlipWebhookUseCase.BlipWebhookPayload(
                messageId,
                appointmentId,
                action,
                from,
                inovareToken
        ));

        return ResponseEntity.accepted().body(Map.of(
                "status", "processed"));
    }

    private JsonNode parsePayload(String rawPayload) {
        if (!StringUtils.hasText(rawPayload)) {
            return null;
        }

        try {
            return objectMapper.readTree(rawPayload);
        } catch (JsonProcessingException ex) {
            log.warn("Falha ao parsear payload do Blip: {}", ex.getMessage());
            return null;
        }
    }

    private String extractFrom(JsonNode payload) {
        String from = firstNonBlank(
            payload.path("from").asText(null),
            payload.path("resource").path("from").asText(null),
            payload.path("message").path("from").asText(null));

        String originator = firstNonBlank(
            payload.path("metadata").path("#tunnel.originator").asText(null),
            payload.path("envelope").path("metadata").path("#tunnel.originator").asText(null),
            payload.path("message").path("metadata").path("#tunnel.originator").asText(null),
            payload.path("resource").path("metadata").path("#tunnel.originator").asText(null),
            payload.path("resource").path("envelope").path("metadata").path("#tunnel.originator").asText(null),
            payload.path("resource").path("message").path("metadata").path("#tunnel.originator").asText(null));

        if (StringUtils.hasText(originator)) {
            return originator;
        }

        return from;
    }

    private String extractActionText(JsonNode payload) {
        // Captura o payload do botão de resposta rápida do WhatsApp.
        // vnd.lime.select+json: o clique vem em content como objeto com
        // .value ou .payload de um option selecionado.

        // 1) Tenta capturar payload de select+json (opção selecionada)
        JsonNode contentNode = firstNonNullNode(
            payload.path("content"),
            payload.path("resource").path("content"));

        if (contentNode != null && !contentNode.isMissingNode()) {
            // select+json: selected option pode ter .value ou .payload
            String selectValue = firstNonBlankNode(
                contentNode.path("value"),
                contentNode.path("payload"),
                contentNode.path("id"));
            if (selectValue != null) {
                log.info("[WEBHOOK] action extraído (select+json): '{}'", selectValue);
                return selectValue;
            }
        }

        // 2) Fallback: caminhos textuais simples
        String action = firstNonBlank(
                payload.path("action").asText(null),
                payload.path("content").path("payload").asText(null),
                payload.path("content").path("id").asText(null),
                payload.path("content").asText(null),
                payload.path("content").path("text").asText(null),
                payload.path("content").path("title").asText(null),
                payload.path("resource").path("action").asText(null),
                payload.path("resource").path("content").path("payload").asText(null),
                payload.path("resource").path("content").asText(null),
                payload.path("resource").path("content").path("text").asText(null),
                payload.path("resource").path("content").path("title").asText(null));
        log.info("[WEBHOOK] action extraído: '{}'", action);
        return action;
    }

    /** Retorna o primeiro JsonNode que não seja null/missing/null-node. */
    private JsonNode firstNonNullNode(JsonNode... nodes) {
        for (JsonNode n : nodes) {
            if (n != null && !n.isMissingNode() && !n.isNull()) return n;
        }
        return null;
    }

    /** Extrai texto não-vazio do primeiro nó que tenha valor. */
    private String firstNonBlankNode(JsonNode... nodes) {
        for (JsonNode n : nodes) {
            if (n != null && !n.isMissingNode() && !n.isNull()) {
                String text = n.asText(null);
                if (text != null && !text.isBlank() && !"null".equalsIgnoreCase(text)) return text;
            }
        }
        return null;
    }

    private String extractMessageId(JsonNode payload) {
        return firstNonBlank(
                payload.path("id").asText(null),
                payload.path("message").path("id").asText(null),
                UUID.randomUUID().toString()
        );
    }

    private String extractAppointmentId(JsonNode payload) {
        return firstNonBlank(
                payload.path("appointmentId").asText(null),
                payload.path("metadata").path("appointmentId").asText(null),
                payload.path("envelope").path("metadata").path("appointmentId").asText(null),
                payload.path("resource").path("appointmentId").asText(null),
                payload.path("resource").path("metadata").path("appointmentId").asText(null),
                payload.path("resource").path("content").path("appointmentId").asText(null));
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
}
