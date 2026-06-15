package br.dev.ctrls.inovareti.modules.inventory.application.usecase;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.core.shared.domain.model.exception.BadRequestException;
import br.dev.ctrls.inovareti.core.shared.domain.model.exception.ConflictException;
import br.dev.ctrls.inovareti.core.shared.domain.model.exception.NotFoundException;
import br.dev.ctrls.inovareti.modules.inventory.domain.model.Item;
import br.dev.ctrls.inovareti.modules.inventory.domain.port.output.ItemRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Caso de uso responsável pelo acoplamento físico e hierárquico de um ativo filho a um ativo pai no inventário.
 * Exemplo típico: vincular um Monitor (ativo filho) ou Nobreak a um Computador Desktop (ativo pai).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LinkAssetComponentUseCase {

    private final ItemRepositoryPort itemRepository;

    /**
     * Executa a associação lógica e física entre os ativos no inventário.
     *
     * @param parentId O identificador único do ativo principal (pai).
     * @param childId  O identificador único do componente/dispositivo acoplado (filho).
     * @throws NotFoundException se o ativo pai ou filho não forem localizados.
     * @throws BadRequestException se o ativo pai tentar ser filho de si mesmo.
     * @throws ConflictException se o ativo filho já estiver vinculado a outro ativo principal.
     */
    @Transactional
    public void execute(UUID parentId, UUID childId) {
        // Validação 1: Impede a autorreferência cíclica direta (um item não pode ser pai de si mesmo).
        if (parentId.equals(childId)) {
            throw new BadRequestException("Regra Patrimonial: Um ativo de inventário não pode ser acoplado a ele mesmo.");
        }

        // Validação 2: Verifica a existência física do ativo principal no banco de dados.
        Item parentItem = itemRepository.findById(parentId)
                .orElseThrow(() -> new NotFoundException("Ativo principal (pai) não localizado com o id: " + parentId));

        // Validação 3: Verifica a existência física do ativo componente no banco de dados.
        Item childItem = itemRepository.findById(childId)
                .orElseThrow(() -> new NotFoundException("Ativo componente (filho) não localizado com o id: " + childId));

        // Validação 4: Valida integridade patrimonial contra duplicidade de alocação de ativos com identidade única.
        if (childItem.getParent() != null) {
            // Se o pai associado for diferente do solicitado, gera erro de negócio informando o conflito.
            if (!childItem.getParent().getId().equals(parentId)) {
                throw new ConflictException(String.format(
                        "Conflito de Alocação: O item '%s' já está acoplado ao ativo '%s' (ID: %s). " +
                        "Desvincule-o antes de realizar um novo acoplamento.",
                        childItem.getName(),
                        childItem.getParent().getName(),
                        childItem.getParent().getId()
                ));
            }
            // Se for o mesmo pai, a operação é tratada de forma idempotente e segura.
            log.info("O ativo componente {} já está vinculado ao ativo pai {}. Operação idempotente concluída.", childId, parentId);
            return;
        }

        // Realiza a atribuição hierárquica e persiste na base de dados.
        childItem.setParent(parentItem);
        itemRepository.save(childItem);

        log.info("Vínculo patrimonial estabelecido com sucesso: Ativo componente '{}' (ID: {}) associado ao Ativo '{}' (ID: {})",
                childItem.getName(), childId, parentItem.getName(), parentId);
    }
}
