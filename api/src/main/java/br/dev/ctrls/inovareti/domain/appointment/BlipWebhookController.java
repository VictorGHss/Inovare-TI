package br.dev.ctrls.inovareti.domain.appointment;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.dev.ctrls.inovareti.domain.appointment.usecase.HandleBlipWebhookUseCase;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/v1/webhook/blip")
@RequiredArgsConstructor
public class BlipWebhookController {

    private final HandleBlipWebhookUseCase handleBlipWebhookUseCase;
    private final ObjectMapper objectMapper;

    @PostMapping({ "", "/" })
    public ResponseEntity<Void> consume(HttpServletRequest request, @RequestBody(required = false) String rawBody) {
        log.info("Webhook Blip recebido. method={}, uri={}, contentType={}",
                request.getMethod(),
                request.getRequestURI(),
                request.getContentType());

        if (isBlank(rawBody)) {
            log.warn("Webhook Blip recebido sem body. uri={}", request.getRequestURI());
            return ResponseEntity.accepted().build();
        }

        log.debug("RAW WEBHOOK BODY: {}", rawBody);

        try {
            JsonNode root = objectMapper.readTree(rawBody);
            JsonNode resource = root.path("resource");

            String messageId = firstNonBlank(
                    root.path("messageId").asText(null),
                    root.path("id").asText(null),
                    resource.path("id").asText(null),
                    UUID.randomUUID().toString());

            String appointmentId = firstNonBlank(
                    root.path("appointmentId").asText(null),
                    root.path("metadata").path("appointmentId").asText(null),
                    root.path("envelope").path("metadata").path("appointmentId").asText(null),
                    resource.path("appointmentId").asText(null),
                    resource.path("metadata").path("appointmentId").asText(null),
                    resource.path("content").path("appointmentId").asText(null));

            String action = normalizeAction(firstNonBlank(
                    root.path("action").asText(null),
                    resource.path("action").asText(null),
                    resource.path("content").path("action").asText(null),
                    resource.path("content").asText(null)));

            String from = firstNonBlank(
                    root.path("from").asText(null),
                    resource.path("from").asText(null));

            if (isBlank(appointmentId) || isBlank(action) || isBlank(from)) {
                log.debug("Webhook recebido sem campos obrigatórios para processamento. appointmentId={}, action={}, from={}",
                        appointmentId, action, from);
                return ResponseEntity.accepted().build();
            }

            handleBlipWebhookUseCase.execute(new HandleBlipWebhookUseCase.BlipWebhookPayload(
                    messageId,
                    appointmentId,
                    action,
                    from));
        } catch (JsonProcessingException ex) {
            log.error("Payload JSON inválido recebido no webhook Blip", ex);
        } catch (RuntimeException ex) {
            log.error("Falha ao interpretar/processar webhook Blip", ex);
        }

        return ResponseEntity.accepted().build();
    }

    private String normalizeAction(String action) {
        if (isBlank(action)) {
            return action;
        }

        String value = action.trim().toUpperCase();
        if (value.contains("CONFIRMAR")) {
            return "CONFIRMAR";
        }
        if (value.contains("ALTERAR")) {
            return "ALTERAR";
        }
        return value;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }

        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
