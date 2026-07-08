package br.dev.ctrls.inovareti.modules.access.infrastructure.adapter.input;

import br.dev.ctrls.inovareti.modules.access.domain.model.AcessoCredencial;
import br.dev.ctrls.inovareti.modules.access.domain.model.TipoUsuario;
import br.dev.ctrls.inovareti.modules.access.domain.port.output.AcessoCredencialRepositoryPort;
import br.dev.ctrls.inovareti.modules.access.infrastructure.config.InovareMotorProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Controlador REST para o controle de acesso integrado às catracas físicas.
 * Código comentado em PT-BR conforme as Regras de Ouro.
 */
@Slf4j
@RestController
@RequestMapping("/v1/access")
@RequiredArgsConstructor
public class AcessoController {

    private final InovareMotorProperties inovareMotorProperties;
    private final AcessoCredencialRepositoryPort acessoCredencialRepositoryPort;

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
        AcessoCredencial credencial = AcessoCredencial.builder()
            .id(UUID.randomUUID())
            .idAgendamento("TEST-APP-" + doctorId + "-" + System.currentTimeMillis())
            .nome("PACIENTE TESTE DOCTORID " + doctorId)
            .cpf("123.456.789-00")
            .tipoUsuario(TipoUsuario.PACIENTE)
            .credencialGerAcesso("CRED-TEST-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
            .localizador("LOC-" + System.currentTimeMillis())
            .dataCriacao(LocalDateTime.now())
            .build();

        AcessoCredencial saved = acessoCredencialRepositoryPort.save(credencial);
        log.info("[AccessControl] Credencial de teste salva com sucesso: ID={}, Nome={}", saved.getId(), saved.getNome());

        return ResponseEntity.ok(Map.of(
            "status", "success",
            "message", "Acesso de teste permitido e credencial persistida com sucesso.",
            "doctorId", doctorId,
            "credencialId", saved.getId()
        ));
    }
}
