package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.input.rest;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.DoctorConfigurationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Controller REST público responsável por resolver o redirecionamento dinâmico
 * de avaliações do Google (302 Found) com base no ID do médico ou fallback da clínica.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class PublicReviewController {

    public static final String CLINIC_FALLBACK_URL = "https://search.google.com/local/writereview?placeid=ChIJN3Bz2lwa6JQRzi0rQrcYanw";
    private final DoctorConfigurationRepository doctorConfigurationRepository;

    /**
     * Endpoint GET público de redirecionamento dinâmico.
     * Busca a googleReviewUrl do médico pelo ID no PostgreSQL e redireciona o paciente.
     * Se não localizada, redireciona para a URL padrão da Clínica Inovare.
     *
     * @param doctorId ID do profissional Feegow ou identificador do médico.
     * @return 302 Found redirecionando para a URL final do Google Review.
     */
    @GetMapping({"/review/{doctorId}", "/v1/doctors/configurations/review/{doctorId}", "/api/review/{doctorId}"})
    public ResponseEntity<Void> redirectToGoogleReview(@PathVariable String doctorId) {
        log.info("[REVIEW-REDIRECT] Solicitação de redirecionamento para avaliação do médico ID='{}'", doctorId);
        String targetUrl = CLINIC_FALLBACK_URL;
        if (doctorId != null && !doctorId.isBlank()) {
            try {
                Long id = Long.parseLong(doctorId.trim());
                var configOpt = doctorConfigurationRepository.findById(id);
                if (configOpt.isPresent()) {
                    String docUrl = configOpt.get().getGoogleReviewUrl();
                    if (docUrl != null && !docUrl.isBlank()) {
                        targetUrl = docUrl.trim();
                    }
                }
            } catch (NumberFormatException nfe) {
                log.debug("[REVIEW-REDIRECT] doctorId não numérico ('{}'), utilizando fallback da clínica.", doctorId);
            } catch (Exception ex) {
                log.warn("[REVIEW-REDIRECT] Erro ao buscar URL de avaliação para médico ID='{}': {}", doctorId, ex.getMessage());
            }
        }

        log.info("[REVIEW-REDIRECT] Redirecionando paciente do médico ID='{}' para a URL: {}", doctorId, targetUrl);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(targetUrl))
                .build();
    }
}
