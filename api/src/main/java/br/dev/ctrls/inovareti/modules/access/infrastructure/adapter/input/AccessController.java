package br.dev.ctrls.inovareti.modules.access.infrastructure.adapter.input;

import br.dev.ctrls.inovareti.modules.access.domain.model.AccessCredential;
import br.dev.ctrls.inovareti.modules.access.domain.model.UserType;
import br.dev.ctrls.inovareti.modules.access.domain.port.output.AccessCredentialRepositoryPort;
import br.dev.ctrls.inovareti.modules.access.domain.service.AccessService;
import br.dev.ctrls.inovareti.modules.access.domain.model.CompanionAccessInfo;
import br.dev.ctrls.inovareti.modules.access.infrastructure.adapter.input.dto.AccessValidationRequest;
import br.dev.ctrls.inovareti.modules.access.infrastructure.config.InovareMotorProperties;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Controlador REST para o controle de acesso integrado às catracas físicas.
 * Traduzido para o inglês seguindo as Regras de Nomenclatura Cruciais.
 * Comentários mantidos em PT-BR.
 */
@Slf4j
@RestController
@RequestMapping("/v1/access")
@RequiredArgsConstructor
public class AccessController {

    private final InovareMotorProperties inovareMotorProperties;
    private final AccessCredentialRepositoryPort accessCredentialRepositoryPort;
    private final AccessService accessService;

    /**
     * Endpoint de teste manual para validação de acesso das catracas.
     * Aceita apenas doctorId igual a 1 ou 70. Qualquer outro ID resulta em um retorno 403 Forbidden
     * imediato para blindar a produção.
     *
     * @param doctorId Identificador do médico.
     * @return ResponseEntity com o resultado da operação.
     */
    @PostMapping("/test")
    public ResponseEntity<?> testAccess(@RequestParam("doctorId") Long doctorId) {
        log.info("[AccessControl] Executando validação de acesso de teste para o doctorId: {}", doctorId);

        // Validação estrita: Aceita apenas os IDs de teste manual (1 ou 70) configurados nas propriedades
        if (doctorId == null || inovareMotorProperties.getTestDoctorIds() == null || !inovareMotorProperties.getTestDoctorIds().contains(doctorId)) {
            log.warn("[AccessControl] Acesso proibido. O doctorId {} não é permitido para testes ou a produção está ativa.", doctorId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "Forbidden", "message", "Acesso negado. O ID fornecido não é elegível para testes."));
        }

        // Criando e persistindo uma credencial de teste para validar a infraestrutura JPA/Flyway
        AccessCredential credential = AccessCredential.builder()
            .id(UUID.randomUUID())
            .appointmentId("TEST-APP-" + doctorId + "-" + System.currentTimeMillis())
            .name("PACIENTE TESTE DOCTORID " + doctorId)
            .cpf("123.456.789-00")
            .userType(UserType.PATIENT)
            .accessCredential("CRED-TEST-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
            .locator("LOC-" + System.currentTimeMillis())
            .createdAt(LocalDateTime.now())
            .build();

        AccessCredential saved = accessCredentialRepositoryPort.save(credential);
        log.info("[AccessControl] Credencial de teste salva com sucesso: ID={}, Nome={}", saved.getId(), saved.getName());

        return ResponseEntity.ok(Map.of(
            "status", "success",
            "message", "Acesso de teste permitido e credencial persistida com sucesso.",
            "doctorId", doctorId,
            "credencialId", saved.getId()
        ));
    }

    /**
     * Endpoint de validação de acesso utilizado pelo Blip bot ou front-end.
     * Consulta prontuários no Feegow, agrupa agendamentos diários, cadastra paciente/acompanhantes no GerAcesso
     * e gera as credenciais unificadas.
     * Trata o caso de CPF ausente retornando 'requiresCpfFallback: true'.
     *
     * @param request Payload contendo dados do agendamento, CPF e acompanhantes.
     * @return ResponseEntity com o resultado da validação de acesso.
     */
    @PostMapping("/validate")
    public ResponseEntity<?> validateAccess(@RequestBody @Valid AccessValidationRequest request) {
        log.info("[AccessControl] Solicitação de validação de acesso: agendamento={}, cpf={}", 
                request.appointmentId(), request.cpf());

        java.util.List<CompanionAccessInfo> domainCompanions = null;
        if (request.companions() != null) {
            domainCompanions = request.companions().stream()
                    .map(c -> new CompanionAccessInfo(c.name(), c.cpf(), c.phone(), c.email()))
                    .toList();
        }

        AccessService.AccessValidationResult result = accessService.processAccessRequest(
                request.appointmentId(), request.cpf(), domainCompanions);

        // Se o CPF estiver ausente, retornamos a flag de fallback conforme os requisitos
        if (result.requiresCpfFallback()) {
            log.warn("[AccessControl] CPF ausente para agendamento {}. Retornando requerimento de fallback de CPF.", request.appointmentId());
            return ResponseEntity.ok(Map.of(
                "authorized", false,
                "requiresCpfFallback", true,
                "message", result.message()
            ));
        }

        if (!result.authorized()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
        }

        return ResponseEntity.ok(result);
    }
}
