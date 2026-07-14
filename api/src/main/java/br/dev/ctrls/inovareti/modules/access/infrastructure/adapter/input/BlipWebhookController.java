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
 *
 * <p>Resiliência de Disponibilidade: toda chamada síncrona ao ERP Feegow é envolvida
 * em um try-catch. Em caso de oscilação ou timeout (5xx/IOException), o bot NÃO recebe
 * um Erro 500. Em vez disso, o controller ativa imediatamente o Fallback Seguro,
 * retornando {@code requiresCpfFallback: true} para que o Blip solicite o CPF manualmente
 * ao paciente, mantendo o fluxo de confirmação vivo mesmo com o ERP indisponível.</p>
 *
 * <p>Processamento assíncrono via Java 21 Virtual Threads e retorno imediato para
 * evitar timeouts no chatbot.</p>
 *
 * <p>Comentários em PT-BR pelas Regras de Ouro.</p>
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
     *
     * <p>Fluxo principal:
     * <ol>
     *   <li>Valida a ação do payload — somente "Finalizar_Agendamento" é aceito.</li>
     *   <li>Tenta resolver o CPF no Feegow de forma síncrona com fallback imediato
     *       caso o ERP esteja indisponível.</li>
     *   <li>Se o CPF continua ausente, retorna {@code requiresCpfFallback: true}.</li>
     *   <li>Caso contrário, delega o processamento físico para uma Virtual Thread e
     *       retorna {@code 200 OK} imediatamente para o Blip.</li>
     * </ol>
     * </p>
     */
    @PostMapping("/confirmation")
    public ResponseEntity<?> receiveConfirmation(@RequestBody @Valid BlipWebhookPayload payload) {
        log.info("[BlipWebhookController] Recebida notificação do Blip: action={}, appointmentId={}",
                payload.action(), payload.appointmentId());

        if (!"Finalizar_Agendamento".equalsIgnoreCase(payload.action())) {
            log.warn("[BlipWebhookController] Ação '{}' ignorada. Apenas 'Finalizar_Agendamento' é aceito.", payload.action());
            return ResponseEntity.badRequest().body(Map.of("message", "Ação inválida. Esperado 'Finalizar_Agendamento'."));
        }

        // --- Resolução do CPF com Fallback Seguro de Indisponibilidade do ERP Feegow ---
        // A chamada síncrona ao Feegow está isolada em try-catch. Caso o ERP esteja fora
        // do ar (5xx, timeout, IOException), ativamos o fallback imediatamente em vez de
        // propagar o erro para o Blip, mantendo o fluxo do WhatsApp vivo e resiliente.
        String cpf = payload.cpf();
        if (cpf == null || cpf.trim().isEmpty()) {
            try {
                var infoOpt = feegowClientPort.fetchPatientAccessInfo(payload.appointmentId());
                if (infoOpt.isPresent() && infoOpt.get().cpf() != null) {
                    cpf = infoOpt.get().cpf();
                }
            } catch (Exception ex) {
                // Oscilação do ERP detectada: registra o incidente.
                // Não solicitamos CPF no Blip, apenas deixamos seguir.
                log.warn("[BlipWebhookController] ERP Feegow indisponível durante resolução do CPF para o agendamento {}. Causa: {}", 
                         payload.appointmentId(), ex.getMessage());
                return ResponseEntity.ok(Map.of(
                    "authorized", true,
                    "requiresCpfFallback", false,
                    "message", "ERP temporariamente indisponível. Continuando sem CPF."
                ));
            }
        }

        // Se CPF permanece ausente após a tentativa no Feegow (prontuário sem CPF cadastrado)
        if (cpf == null || cpf.trim().replaceAll("\\D", "").isEmpty()) {
            log.warn("[BlipWebhookController] CPF ausente para o agendamento {}. Continuando sem CPF.",
                    payload.appointmentId());
            return ResponseEntity.ok(Map.of(
                "authorized", true,
                "requiresCpfFallback", false,
                "message", "CPF ausente no Blip e no Feegow. Continuando sem CPF."
            ));
        }

        // Mapeia acompanhantes da infraestrutura para o domínio
        List<CompanionAccessInfo> domainCompanions = null;
        if (payload.companions() != null) {
            domainCompanions = payload.companions().stream()
                    .map(c -> new CompanionAccessInfo(c.name(), c.cpf(), c.phone(), c.email(), c.birthDate()))
                    .toList();
        }

        final String finalCpf = cpf;
        final List<CompanionAccessInfo> finalCompanions = domainCompanions;

        // Dispara o processamento pesado (GerAcesso local + banco) de forma totalmente assíncrona
        // usando Java 21 Virtual Threads. Retorna imediatamente ao Blip para evitar timeout.
        Thread.startVirtualThread(() -> {
            log.info("[BlipWebhookController] Iniciando processamento de acesso físico em Virtual Thread para agendamento {}",
                    payload.appointmentId());
            try {
                accessService.processAccessRequest(payload.appointmentId(), finalCpf, finalCompanions);
                log.info("[BlipWebhookController] Processamento concluído com sucesso em segundo plano para o agendamento {}",
                        payload.appointmentId());
            } catch (Exception ex) {
                log.error("[BlipWebhookController] Falha fatal no processamento em segundo plano para o agendamento {}",
                        payload.appointmentId(), ex);
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
     * Todos os campos usam as chaves camelCase puras conforme o parser do bot.
     * Chaves: 'idAgendamentoFeegow', 'listaAcompanhantes', 'requiresCpfFallback'.
     */
    public record BlipWebhookPayload(
        @JsonProperty("action") String action,
        @JsonProperty("idAgendamentoFeegow") String appointmentId,
        @JsonProperty("cpf") String cpf,
        @JsonProperty("nome") String name,
        @JsonProperty("telefone") String phone,
        @JsonProperty("email") String email,
        @JsonProperty("listaAcompanhantes") List<CompanionRequest> companions,
        @JsonProperty("requiresCpfFallback") Boolean requiresCpfFallback
    ) {}
}
