package br.dev.ctrls.inovareti.domain.appointment;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;

import br.dev.ctrls.inovareti.domain.appointment.usecase.HandleBlipWebhookUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/v1/webhook")
@RequiredArgsConstructor
public class BlipWebhookController {

    private final HandleBlipWebhookUseCase handleBlipWebhookUseCase;

    @PostMapping("/blip")
    public ResponseEntity<Map<String, Object>> blipWebhook(@RequestBody(required = false) JsonNode payload) {
        log.info("Blip webhook received: {}", payload);

        if (payload == null || payload.isNull()) {
            log.warn("Blip webhook received without body at /v1/webhook/blip.");
            return ResponseEntity.accepted().body(Map.of(
                    "status", "ignored",
                    "reason", "body-empty"));
        }

        String from = extractFrom(payload);
        String action = extractActionText(payload);
        String messageId = extractMessageId(payload);
        String appointmentId = extractAppointmentId(payload);

        handleBlipWebhookUseCase.execute(new HandleBlipWebhookUseCase.BlipWebhookPayload(
                messageId,
                appointmentId,
                action,
                from
        ));

        return ResponseEntity.accepted().body(Map.of(
                "status", "processed"));
    }

    private String extractFrom(JsonNode payload) {
        return firstNonBlank(
                payload.path("from").asText(null),
                payload.path("resource").path("from").asText(null),
                payload.path("message").path("from").asText(null));
    }

    private String extractActionText(JsonNode payload) {
        return firstNonBlank(
                payload.path("action").asText(null),
                payload.path("content").asText(null),
                payload.path("content").path("text").asText(null),
                payload.path("content").path("title").asText(null),
                payload.path("resource").path("action").asText(null),
                payload.path("resource").path("content").asText(null),
                payload.path("resource").path("content").path("text").asText(null),
                payload.path("resource").path("content").path("title").asText(null));
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
