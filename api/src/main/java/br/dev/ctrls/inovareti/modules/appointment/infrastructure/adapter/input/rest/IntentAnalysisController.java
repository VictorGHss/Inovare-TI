package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.input.rest;

import br.dev.ctrls.inovareti.modules.appointment.application.dto.IntentAnalysisRequest;
import br.dev.ctrls.inovareti.modules.appointment.application.dto.IntentAnalysisResponse;
import br.dev.ctrls.inovareti.modules.appointment.application.service.IntentAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller REST para o endpoint de extração de intenções e busca de médicos/especialidades.
 * Integrado ao fluxo do bot Blip.
 * Comentários mantidos em PT-BR pelas Regras de Ouro.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Webhooks - Intenção Blip", description = "Endpoint de extração de intenção e busca por relevância de médicos/especialidades")
public class IntentAnalysisController {

    private final IntentAnalysisService intentAnalysisService;

    /**
     * Endpoint de extração de intenções e busca de médicos/especialidades para o Blip.
     * Mapeia as rotas /api/webhooks/intencao, /v1/webhooks/intencao e /webhooks/intencao.
     * Garante resposta com HTTP status 200 em todas as situações de execução.
     *
     * @param request Payload de entrada contendo a mensagem do usuário.
     * @return ResponseEntity contendo o IntentAnalysisResponse (HTTP 200 OK).
     */
    @PostMapping(
        value = {"/api/webhooks/intencao", "/v1/webhooks/intencao", "/webhooks/intencao"},
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(summary = "Processa mensagem do usuário e extrai a intenção ou busca o médico/especialidade correspondente")
    public ResponseEntity<IntentAnalysisResponse> analyzeIntent(@RequestBody(required = false) IntentAnalysisRequest request) {
        log.info("[IntentAnalysisController] Requisição recebida para extração de intenção.");
        try {
            IntentAnalysisResponse response = intentAnalysisService.processIntent(request);
            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            log.error("[IntentAnalysisController] Falha ao processar intenção. Retornando fallback NENHUM_RESULTADO. Erro: {}", ex.getMessage(), ex);
            String termoFallback = (request != null && request.getMensagem() != null) ? request.getMensagem().trim() : "";
            IntentAnalysisResponse fallback = IntentAnalysisResponse.builder()
                .tipo("NENHUM_RESULTADO")
                .termoBuscado(termoFallback)
                .build();
            return ResponseEntity.ok(fallback);
        }
    }
}
