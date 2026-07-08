package br.dev.ctrls.inovareti.modules.access.infrastructure.adapter.input;

import br.dev.ctrls.inovareti.modules.access.domain.model.CompanionAccessInfo;
import br.dev.ctrls.inovareti.modules.access.domain.service.AccessService;
import br.dev.ctrls.inovareti.modules.access.domain.port.output.FeegowClientPort;
import br.dev.ctrls.inovareti.modules.access.infrastructure.adapter.input.dto.CompanionRequest;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.Map;

/**
 * Adaptador de entrada REST para integração com o Webhook do Blip.
 * Expõe o endpoint POST /v1/access/blip/confirmation.
 * Processamento assíncrono via Java 21 Virtual Threads e retorno imediato para evitar timeouts.
 * Comentários em PT-BR pelas Regras de Ouro.
 */
@Slf4j
@RestController("accessBlipWebhookController")
@RequestMapping("/v1/access/blip")
@RequiredArgsConstructor
public class BlipWebhookController {

    private final AccessService accessService;
    private final FeegowClientPort feegowClientPort;

    /**
     * Recebe a confirmação de agendamento do Blip.
     * Se CPF estiver ausente no Blip e no Feegow, retorna metadados de fallback síncronos.
     * Caso contrário, delega o processamento da liberação física para uma Virtual Thread e retorna 200 OK imediatamente.
     */
    @PostMapping("/confirmation")
    public ResponseEntity<?> receiveConfirmation(@RequestBody @Valid BlipWebhookPayload payload) {
        log.info("[BlipWebhookController] Recebida notificação do Blip: action={}, appointmentId={}", 
                payload.action(), payload.appointmentId());

        if (!"Finalizar_Agendamento".equalsIgnoreCase(payload.action())) {
            log.warn("[BlipWebhookController] Ação '{}' ignorada. Apenas 'Finalizar_Agendamento' é aceito.", payload.action());
            return ResponseEntity.badRequest().body(Map.of("message", "Ação inválida. Esperado 'Finalizar_Agendamento'."));
        }

        // Resolvendo o CPF para validação síncrona rápida de fallback
        String cpf = payload.cpf();
        if (cpf == null || cpf.trim().isEmpty()) {
            try {
                var infoOpt = feegowClientPort.fetchPatientAccessInfo(payload.appointmentId());
                if (infoOpt.isPresent() && infoOpt.get().cpf() != null) {
                    cpf = infoOpt.get().cpf();
                }
            } catch (Exception ex) {
                log.warn("[BlipWebhookController] Erro ao buscar CPF no Feegow para o agendamento: {}", payload.appointmentId(), ex);
            }
        }

        // Se CPF continua ausente, aciona o fallback no bot do Blip imediatamente
        if (cpf == null || cpf.trim().replaceAll("\\D", "").isEmpty()) {
            log.warn("[BlipWebhookController] CPF ausente para o agendamento {}. Retornando requerimento de fallback de CPF.", payload.appointmentId());
            return ResponseEntity.ok(Map.of(
                "authorized", false,
                "requiresCpfFallback", true,
                "message", "CPF ausente no Blip e no Feegow. Redirecionando para coleta de CPF."
            ));
        }

        // Mapeia acompanhantes da infraestrutura para o domínio
        List<CompanionAccessInfo> domainCompanions = null;
        if (payload.companions() != null) {
            domainCompanions = payload.companions().stream()
                    .map(c -> new CompanionAccessInfo(c.name(), c.cpf(), c.phone(), c.email()))
                    .toList();
        }

        final String finalCpf = cpf;
        final List<CompanionAccessInfo> finalCompanions = domainCompanions;

        // Dispara o processamento pesado na rede local de forma totalmente assíncrona usando Java 21 Virtual Threads
        Thread.startVirtualThread(() -> {
            log.info("[BlipWebhookController] Iniciando processamento de acesso físico em Virtual Thread para agendamento {}", payload.appointmentId());
            try {
                accessService.processAccessRequest(payload.appointmentId(), finalCpf, finalCompanions);
                log.info("[BlipWebhookController] Processamento concluído com sucesso em segundo plano para o agendamento {}", payload.appointmentId());
            } catch (Exception ex) {
                log.error("[BlipWebhookController] Falha fatal no processamento em segundo plano para o agendamento {}", payload.appointmentId(), ex);
            }
        });

        // Retorna imediatamente para evitar timeout no chatbot do paciente
        return ResponseEntity.ok(Map.of(
            "authorized", true,
            "requiresCpfFallback", false,
            "message", "Solicitação recebida e em processamento assíncrono."
        ));
    }

    /**
     * Payload JSON estruturado do webhook do Blip.
     */
    public record BlipWebhookPayload(
        @JsonProperty("action") String action,
        @JsonProperty("id_agendamento") String appointmentId,
        @JsonProperty("cpf") String cpf,
        @JsonProperty("nome") String name,
        @JsonProperty("telefone") String phone,
        @JsonProperty("email") String email,
        @JsonProperty("listaAcompanhantes") List<CompanionRequest> companions
    ) {}
}
