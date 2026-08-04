package br.dev.ctrls.inovareti.modules.access.application.usecase;

import br.dev.ctrls.inovareti.modules.access.domain.model.AccessCredential;
import br.dev.ctrls.inovareti.modules.access.domain.model.CompanionAccessInfo;
import br.dev.ctrls.inovareti.modules.access.domain.service.AccessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Caso de Uso SaveCompanionUseCase.
 * Orquestra a adição e o cadastro unificado de acompanhantes na API GerAcesso e banco local.
 * Comentários mantidos em PT-BR pelas Regras de Ouro.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SaveCompanionUseCase {

    private final AccessService accessService;

    /**
     * Executa o cadastro do acompanhante para um agendamento específico.
     *
     * @param appointmentId Identificador do agendamento titular.
     * @param companion Dados cadastrais do acompanhante.
     * @return AccessCredential do acompanhante com a credencial real gerada pela GerAcesso.
     */
    public AccessCredential execute(String appointmentId, CompanionAccessInfo companion) {
        log.info("[SaveCompanionUseCase] Executando cadastro unificado do acompanhante '{}' para o agendamento ID: {}", 
                companion.name(), appointmentId);
        return accessService.registerCompanion(appointmentId, companion);
    }
}
