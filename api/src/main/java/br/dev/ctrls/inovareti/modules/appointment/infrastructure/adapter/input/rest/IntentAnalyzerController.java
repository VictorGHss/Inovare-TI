package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.input.rest;

import br.dev.ctrls.inovareti.modules.appointment.application.dto.IntentAnalysisResultDto;
import br.dev.ctrls.inovareti.modules.appointment.application.service.IntentAnalyzerService;
import io.micrometer.observation.annotation.Observed;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller REST do Motor de Intenções para integração com o Take Blip Builder.
 * Expõe os endpoints de análise de linguagem natural com suporte a paginação.
 */
@Slf4j
@RestController
@RequestMapping("/v1/nlp")
@RequiredArgsConstructor
@Observed
public class IntentAnalyzerController {

    private final IntentAnalyzerService intentAnalyzerService;

    @Data
    public static class AnalyzeRequest {
        @com.fasterxml.jackson.annotation.JsonAlias({"message", "text", "mensagem", "input", "query"})
        private String text;
        private Integer page;
    }

    /**
     * Analisa a mensagem do paciente, extrai intenções e retorna os médicos/opções de desambiguação paginados.
     *
     * @param request corpo contendo o texto enviado pelo paciente e parâmetro opcional de página
     * @param queryPage parâmetro de query string opcional (ex: ?page=2)
     * @return IntentAnalysisResultDto em JSON limpo e padronizado
     */
    @PostMapping("/analyze")
    public ResponseEntity<IntentAnalysisResultDto> analyzeText(
            @RequestBody(required = false) AnalyzeRequest request,
            @RequestParam(name = "page", required = false) Integer queryPage) {
        
        String textToAnalyze = request != null && request.getText() != null ? request.getText() : "";
        int page = 1;
        if (queryPage != null && queryPage > 0) {
            page = queryPage;
        } else if (request != null && request.getPage() != null && request.getPage() > 0) {
            page = request.getPage();
        }

        log.info("[BLIP-INBOUND] Recebida chamada no endpoint POST /v1/nlp/analyze com texto: '{}', página: {}", textToAnalyze, page);

        IntentAnalysisResultDto result = intentAnalyzerService.analyzeIntent(textToAnalyze, page);
        return ResponseEntity.ok(result);
    }

    /**
     * Endpoint GET alternativo para consultas simplificadas via HTTP Action no Take Blip.
     */
    @GetMapping("/analyze")
    public ResponseEntity<IntentAnalysisResultDto> analyzeTextGet(
            @RequestParam(name = "text", required = false, defaultValue = "") String text,
            @RequestParam(name = "page", required = false, defaultValue = "1") int page) {

        log.info("[BLIP-INBOUND] Recebida chamada no endpoint GET /v1/nlp/analyze com texto: '{}', página: {}", text, page);
        IntentAnalysisResultDto result = intentAnalyzerService.analyzeIntent(text, page);
        return ResponseEntity.ok(result);
    }
}
