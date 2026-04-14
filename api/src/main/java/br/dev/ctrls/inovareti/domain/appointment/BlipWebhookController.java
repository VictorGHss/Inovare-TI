package br.dev.ctrls.inovareti.domain.appointment;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.dev.ctrls.inovareti.domain.appointment.usecase.HandleBlipWebhookUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/v1/webhook/blip")
@RequiredArgsConstructor
public class BlipWebhookController {

    private final HandleBlipWebhookUseCase handleBlipWebhookUseCase;

    @PostMapping
    public ResponseEntity<Void> consume(@RequestBody @Valid BlipWebhookRequest request) {
        log.info("Webhook received: {}", request);
        handleBlipWebhookUseCase.execute(new HandleBlipWebhookUseCase.BlipWebhookPayload(
                request.messageId(),
                request.appointmentId(),
                request.action(),
                request.from()));

        return ResponseEntity.accepted().build();
    }

    public record BlipWebhookRequest(
            @NotBlank(message = "messageId é obrigatório")
            String messageId,
            @NotBlank(message = "appointmentId é obrigatório")
            String appointmentId,
            @NotBlank(message = "action é obrigatório")
            String action,
            @NotBlank(message = "from é obrigatório")
            String from) {
    }
}
