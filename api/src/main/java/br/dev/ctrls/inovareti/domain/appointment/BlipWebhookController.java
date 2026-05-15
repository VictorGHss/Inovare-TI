package br.dev.ctrls.inovareti.domain.appointment;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import br.dev.ctrls.inovareti.domain.appointment.service.BlipWebhookInboundService;
import br.dev.ctrls.inovareti.domain.appointment.usecase.HandleBlipWebhookUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
public class BlipWebhookController {

    private final HandleBlipWebhookUseCase handleBlipWebhookUseCase;
    private final BlipWebhookInboundService blipWebhookInboundService;

    @PostMapping(value = {"/v1/webhook/blip", "/webhooks/blip"},
        consumes = {
            MediaType.APPLICATION_JSON_VALUE,
            "application/vnd.lime.select+json",
            "application/vnd.lime.reply+json"
        })
    public ResponseEntity<?> blipWebhook(
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-Inovare-Token", required = false) String inovareToken,
            @RequestBody(required = false) Map<String, Object> body) {

        Map<String, Object> payload = body != null ? body : Map.of();

        if (payload.isEmpty()) {
            log.warn("Blip webhook received without body at /v1/webhook/blip.");
            return ResponseEntity.ok(Map.of(
                    "status", "ignored",
                    "reason", "body-empty"));
        }

        BlipWebhookInboundService.ParsedInbound parsed = blipWebhookInboundService.parse(payload);

        String from = parsed.from();
        String action = parsed.action();
        String messageId = parsed.messageId();
        String appointmentId = parsed.appointmentId();
        Object content = parsed.content();

        log.info("[WEBHOOK RECEBIDO] from='{}' | action='{}' | messageId='{}' | appointmentId='{}'",
            from, action, messageId, appointmentId);

        HandleBlipWebhookUseCase.WebhookResult result = handleBlipWebhookUseCase.execute(new HandleBlipWebhookUseCase.BlipWebhookPayload(
                messageId,
                appointmentId,
                action,
                from,
                inovareToken,
                content));

        if (result == null) {
            return ResponseEntity.ok(Map.of("status", "processed", "queue", ""));
        }

        return ResponseEntity.ok(new WebhookResponse(
            Objects.requireNonNullElse(result.queue(), ""),
            Objects.requireNonNullElse(result.patientName(), ""),
            Objects.requireNonNullElse(result.patientCPF(), ""),
            Objects.requireNonNullElse(result.patientBirthdate(), ""),
            Objects.requireNonNullElse(result.action(), ""),
            Objects.requireNonNullElse(result.doctorName(), "")
        ));
    }

    @PostMapping(value = "/webhooks/blip/manual-trigger", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> manualTrigger(
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-Inovare-Token", required = false) String inovareToken,
            @RequestBody ManualTriggerRequest body) {
        if (body == null
                || !StringUtils.hasText(body.identity())
                || !StringUtils.hasText(body.appointmentId())
                || !StringUtils.hasText(body.action())) {
            return ResponseEntity.badRequest().body(Map.of(
                "status", "ignored",
                "reason", "missing-fields"));
        }

        String normalizedAction = body.action().trim().toLowerCase();
        String actionPrefix = switch (normalizedAction) {
            case "confirm" -> "confirm";
            case "alter" -> "alter";
            default -> null;
        };

        if (actionPrefix == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "status", "ignored",
                "reason", "invalid-action"));
        }

        String appointmentId = body.appointmentId().trim();
        String action = actionPrefix + "_" + appointmentId;

        log.info("[MANUAL TRIGGER] identity='{}' | action='{}' | appointmentId='{}'",
            body.identity(), action, appointmentId);

        HandleBlipWebhookUseCase.WebhookResult result = handleBlipWebhookUseCase.execute(new HandleBlipWebhookUseCase.BlipWebhookPayload(
                UUID.randomUUID().toString(),
                appointmentId,
                action,
                body.identity().trim(),
                inovareToken,
                null), true);

        if (result == null) {
            return ResponseEntity.ok(Map.of("queue", ""));
        }

        return ResponseEntity.ok(new WebhookResponse(
            Objects.requireNonNullElse(result.queue(), ""),
            Objects.requireNonNullElse(result.patientName(), ""),
            Objects.requireNonNullElse(result.patientCPF(), ""),
            Objects.requireNonNullElse(result.patientBirthdate(), ""),
            Objects.requireNonNullElse(result.action(), ""),
            Objects.requireNonNullElse(result.doctorName(), "")
        ));
    }

    public record ManualTriggerRequest(
            @JsonProperty("identity") String identity,
            @JsonAlias({"appointment_id", "appointmentId"}) @JsonProperty("appointment_id") String appointmentId,
            @JsonProperty("action") String action) {
    }

    public record WebhookResponse(
            @JsonProperty("queue") String queue,
            @JsonProperty("patientName") String patientName,
            @JsonProperty("patientCPF") String patientCPF,
            @JsonProperty("patientBirthdate") String patientBirthdate,
            @JsonProperty("action") String action,
            @JsonProperty("doctorName") String doctorName) {
    }
}
