package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.input.rest;

import br.dev.ctrls.inovareti.modules.appointment.application.dto.IntentAnalysisRequest;
import br.dev.ctrls.inovareti.modules.appointment.application.dto.IntentAnalysisResponse;
import br.dev.ctrls.inovareti.modules.appointment.application.service.IntentAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller REST para o endpoint de extração de intenções e busca de médicos/especialidades.
 * Valida o cabeçalho X-API-KEY contra a chave secreta injetada via variável de ambiente.
 * Comentários mantidos em PT-BR pelas Regras de Ouro.
 */
@Slf4j
@RestController
@RequestMapping("/api/webhooks/intencao")
@Tag(name = "Webhooks - Intenção Blip", description = "Endpoint de extração de intenção e busca por relevância de médicos/especialidades")
public class IntentAnalysisController {

    @Value("${app.webhook.secret-key:}")
    private String secretKey;

    private final IntentAnalysisService intentAnalysisService;

    public IntentAnalysisController(IntentAnalysisService intentAnalysisService) {
        this.intentAnalysisService = intentAnalysisService;
    }

    /**
     * Endpoint de extração de intenções e busca de médicos/especialidades para o Blip.
     * Valida a chave secreta recebida no cabeçalho X-API-KEY.
     *
     * @param apiKey Chave recebida no cabeçalho X-API-KEY.
     * @param request Payload de entrada contendo a mensagem do usuário.
     * @return ResponseEntity contendo o IntentAnalysisResponse (HTTP 200 OK) ou HTTP 401 se não autorizado.
     */
    @PostMapping(
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(summary = "Processa mensagem do usuário e extrai a intenção ou busca o médico/especialidade correspondente")
    public ResponseEntity<IntentAnalysisResponse> analyzeIntent(
            @RequestHeader(value = "X-API-KEY", required = false) String apiKey,
            @RequestBody(required = false) IntentAnalysisRequest request) {

        log.info("[IntentAnalysisController] Requisição recebida para extração de intenção.");

        // Validação da chave secreta consumida da variável de ambiente WEBHOOK_SECRET_KEY
        if (secretKey == null || secretKey.trim().isEmpty() || apiKey == null || !secretKey.equals(apiKey)) {
            log.warn("[IntentAnalysisController] Acesso não autorizado ao webhook de intenção. X-API-KEY ausente ou inválida.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

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
