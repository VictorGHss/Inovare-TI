package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.input.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micrometer.observation.annotation.Observed;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller REST responsável pelo recebimento e registro da avaliação de satisfação (CSAT)
 * do paciente ao finalizar o atendimento no chatbot Take Blip.
 */
@Slf4j
@RestController
@RequestMapping("/v1/atendimento")
@Observed
public class CsatController {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CsatRatingRequest {
        private String patientId;
        private String cpf;
        private Integer rating; // 1 a 5
        private String feedback;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CsatRatingResponse {
        private boolean success;
        private String message;
    }

    /**
     * Endpoint para registrar a nota de CSAT enviada pelo paciente no WhatsApp.
     *
     * @param request Payload contendo patientId, cpf, rating (1-5) e feedback opcional
     * @return CsatRatingResponse indicando sucesso no registro
     */
    @PostMapping("/csat")
    public ResponseEntity<CsatRatingResponse> registerCsat(@RequestBody CsatRatingRequest request) {
        if (request == null || request.getRating() == null) {
            return ResponseEntity.badRequest().body(
                    CsatRatingResponse.builder()
                            .success(false)
                            .message("A nota de avaliação (rating) é obrigatória.")
                            .build()
            );
        }

        if (request.getRating() < 1 || request.getRating() > 5) {
            return ResponseEntity.badRequest().body(
                    CsatRatingResponse.builder()
                            .success(false)
                            .message("A nota de avaliação deve ser um número entre 1 e 5.")
                            .build()
            );
        }

        String cleanCpf = request.getCpf() != null ? request.getCpf().replaceAll("\\D", "") : "";
        log.info("[CSAT-AUDIT] [AVALIAÇÃO-RECEBIDA] Paciente ID: '{}', CPF: '{}', Nota: {}/5, Feedback: '{}'",
                request.getPatientId(), cleanCpf, request.getRating(), request.getFeedback());

        return ResponseEntity.ok(
                CsatRatingResponse.builder()
                        .success(true)
                        .message("Avaliação CSAT registrada com sucesso. Obrigado pelo seu feedback!")
                        .build()
        );
    }
}
