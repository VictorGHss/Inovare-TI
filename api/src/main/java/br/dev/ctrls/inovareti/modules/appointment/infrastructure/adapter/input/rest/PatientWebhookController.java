package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.input.rest;

import br.dev.ctrls.inovareti.modules.appointment.application.dto.PatientAppointmentsResponse;
import br.dev.ctrls.inovareti.modules.appointment.application.dto.PatientWebhookRegistrationRequest;
import br.dev.ctrls.inovareti.modules.appointment.application.dto.PatientWebhookRegistrationResponse;
import br.dev.ctrls.inovareti.modules.appointment.application.service.PatientWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller REST para cadastro de pacientes e consulta de agendamentos futuros via webhook do Blip.
 * Protegido pela validação do cabeçalho X-API-KEY.
 * Comentários mantidos em PT-BR pelas Regras de Ouro.
 */
@Slf4j
@RestController
@RequestMapping({"/webhooks/paciente", "/api/webhooks/paciente", "/v1/webhooks/paciente"})
@Tag(name = "Webhooks - Paciente Blip", description = "Endpoints de cadastro de paciente e consulta de agendamentos para o Blip")
public class PatientWebhookController {

    @Value("${app.webhook.secret-key:}")
    private String secretKey;

    private final PatientWebhookService patientWebhookService;

    public PatientWebhookController(PatientWebhookService patientWebhookService) {
        this.patientWebhookService = patientWebhookService;
    }

    /**
     * Endpoint para cadastro de paciente no Feegow via webhook Blip.
     *
     * @param apiKey Chave recebida no cabeçalho X-API-KEY.
     * @param request Dados do paciente contendo CPF, nome e nascimento (DD/MM/AAAA).
     * @return ResponseEntity com o paciente_id gerado (HTTP 200 OK) ou HTTP 401.
     */
    @PostMapping(
        value = "/cadastrar",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(summary = "Cadastra ou sincroniza um paciente na Feegow a partir dos dados recebidos no Blip")
    public ResponseEntity<PatientWebhookRegistrationResponse> registerPatient(
            @RequestHeader(value = "X-API-KEY", required = false) String apiKey,
            @RequestBody @Valid PatientWebhookRegistrationRequest request) {

        log.info("[PatientWebhookController] Requisição recebida para cadastrar paciente.");

        if (secretKey == null || secretKey.trim().isEmpty() || apiKey == null || !secretKey.equals(apiKey)) {
            log.warn("[PatientWebhookController] Acesso não autorizado ao cadastro de paciente. X-API-KEY ausente ou inválida.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            PatientWebhookRegistrationResponse response = patientWebhookService.registerPatient(request);
            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            log.error("[PatientWebhookController] Erro ao cadastrar paciente no Feegow: {}", ex.getMessage(), ex);
            return ResponseEntity.ok(PatientWebhookRegistrationResponse.builder()
                    .status("error")
                    .mensagem("Falha ao cadastrar paciente: " + ex.getMessage())
                    .build());
        }
    }

    /**
     * Endpoint para consulta de agendamentos futuros do paciente via CPF.
     *
     * @param apiKey Chave recebida no cabeçalho X-API-KEY.
     * @param cpf CPF do paciente.
     * @return ResponseEntity contendo a lista de agendamentos futuros (HTTP 200 OK) ou HTTP 401.
     */
    @GetMapping(
        value = "/agendamentos",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(summary = "Consulta os agendamentos futuros do paciente na Feegow por CPF")
    public ResponseEntity<PatientAppointmentsResponse> getAppointments(
            @RequestHeader(value = "X-API-KEY", required = false) String apiKey,
            @RequestParam(value = "cpf", required = false) String cpf) {

        log.info("[PatientWebhookController] Requisição recebida para consultar agendamentos do CPF: {}", cpf);

        if (secretKey == null || secretKey.trim().isEmpty() || apiKey == null || !secretKey.equals(apiKey)) {
            log.warn("[PatientWebhookController] Acesso não autorizado à consulta de agendamentos. X-API-KEY ausente ou inválida.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            PatientAppointmentsResponse response = patientWebhookService.getFutureAppointments(cpf);
            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            log.error("[PatientWebhookController] Erro ao consultar agendamentos do paciente: {}", ex.getMessage(), ex);
            return ResponseEntity.ok(PatientAppointmentsResponse.builder()
                    .status("error")
                    .total(0)
                    .mensagem("Falha ao consultar agendamentos: " + ex.getMessage())
                    .agendamentos(java.util.List.of())
                    .build());
        }
    }
}
