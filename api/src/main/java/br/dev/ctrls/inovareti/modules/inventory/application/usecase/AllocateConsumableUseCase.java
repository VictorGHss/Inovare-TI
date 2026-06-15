package br.dev.ctrls.inovareti.modules.inventory.application.usecase;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.core.shared.domain.model.exception.NotFoundException;
import br.dev.ctrls.inovareti.modules.inventory.application.service.StockDeductionService;
import br.dev.ctrls.inovareti.modules.inventory.domain.model.Item;
import br.dev.ctrls.inovareti.modules.inventory.domain.model.ItemAllocationEntity;
import br.dev.ctrls.inovareti.modules.inventory.domain.port.output.ItemRepositoryPort;
import br.dev.ctrls.inovareti.modules.inventory.infrastructure.adapter.output.jpa.repository.ItemAllocationJpaRepository;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.Ticket;
import br.dev.ctrls.inovareti.modules.ticket.domain.port.output.TicketRepositoryPort;
import br.dev.ctrls.inovareti.modules.user.domain.model.User;
import br.dev.ctrls.inovareti.modules.user.domain.port.output.UserRepositoryPort;
import br.dev.ctrls.inovareti.modules.asset.domain.model.Asset;
import br.dev.ctrls.inovareti.modules.asset.domain.port.output.AssetRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Caso de uso responsável pela alocação de consumíveis ou periféricos a um ativo principal,
 * coordenando a baixa física em estoque (FIFO) e o registro do vínculo de forma atômica e transacional.
 * Exemplo típico: instalação de um Toner (consumível) em uma Impressora (ativo principal).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AllocateConsumableUseCase {

    private final ItemRepositoryPort itemRepository;
    private final StockDeductionService stockDeductionService;
    private final ItemAllocationJpaRepository itemAllocationRepository;
    private final UserRepositoryPort userRepository;
    private final TicketRepositoryPort ticketRepository;
    private final AssetRepositoryPort assetRepository;

    /**
     * Executa a alocação e baixa de estoque de forma transacional.
     *
     * @param parentId    ID do ativo principal que recebe o componente (ex.: Impressora).
     * @param childItemId ID do consumível/periférico que será baixado e alocado (ex.: Toner).
     * @param quantity    Quantidade de unidades a serem alocadas.
     * @param ticketId    ID do chamado de suporte opcional que gerou a necessidade.
     * @throws NotFoundException se alguma entidade relacionada não for encontrada.
     * @throws IllegalStateException se o estoque físico for insuficiente.
     */
    @Transactional
    public void execute(UUID parentId, UUID childItemId, Integer quantity, UUID ticketId) {
        execute(parentId, null, childItemId, quantity, ticketId);
    }

    /**
     * Executa a alocação e baixa de estoque de forma transacional suportando ativo de CMDB ou inventário pai.
     */
    @Transactional
    public java.math.BigDecimal execute(UUID parentItemId, UUID targetAssetId, UUID childItemId, Integer quantity, UUID ticketId) {
        // 1. Validação da existência do ativo principal no inventário se fornecido.
        Item parentItem = null;
        if (parentItemId != null) {
            parentItem = itemRepository.findById(parentItemId)
                    .orElseThrow(() -> new NotFoundException("Ativo principal não localizado com o id: " + parentItemId));
        }

        // 1.1 Validação da existência do equipamento físico (Asset) no CMDB se fornecido.
        Asset targetAsset = null;
        if (targetAssetId != null) {
            targetAsset = assetRepository.findById(targetAssetId)
                    .orElseThrow(() -> new NotFoundException("Equipamento físico não localizado com o id: " + targetAssetId));
        }

        if (parentItem == null && targetAsset == null) {
            throw new IllegalStateException("É necessário informar um Ativo Principal ou um Equipamento Físico de destino para a alocação.");
        }

        // 2. Validação da existência do item consumível/periférico no catálogo do inventário.
        Item childItem = itemRepository.findById(childItemId)
                .orElseThrow(() -> new NotFoundException("Consumível/Periférico não localizado com o id: " + childItemId));

        // 3. Recuperação do usuário autenticado no contexto do Spring Security (o técnico logado).
        String userIdStr = SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();
        UUID authenticatedUserId = UUID.fromString(userIdStr);
        User currentTechnician = userRepository.findById(authenticatedUserId)
                .orElseThrow(() -> new NotFoundException("Usuário técnico autenticado não localizado com o id: " + authenticatedUserId));

        // 4. Validação opcional da existência do chamado de TI que motivou a troca do componente.
        Ticket associatedTicket = null;
        if (ticketId != null) {
            associatedTicket = ticketRepository.findById(ticketId)
                    .orElseThrow(() -> new NotFoundException("Chamado de suporte não localizado com o id: " + ticketId));
        }

        // 5. Dedução física do estoque usando a política FIFO e geração do histórico financeiro por lote.
        String destinationName = "desconhecido";
        UUID destinationId = null;
        if (parentItem != null) {
            destinationName = parentItem.getName();
            destinationId = parentItem.getId();
        } else if (targetAsset != null) {
            destinationName = targetAsset.getName();
            destinationId = targetAsset.getId();
        }
        String referenceDescription = String.format("Alocação do item '%s' ao ativo '%s' (ID: %s)",
                childItem.getName(), destinationName, destinationId);
        
        if (associatedTicket != null) {
            String shortTicketId = associatedTicket.getId().toString().substring(0, 8).toUpperCase();
            referenceDescription += String.format(" vinculada ao Chamado #%s", shortTicketId);
        }

        java.math.BigDecimal totalCost = stockDeductionService.deductWithFifo(childItemId, quantity, referenceDescription, authenticatedUserId);

        // 6. Registro do vínculo lógico de alocação patrimonial e de consumo.
        ItemAllocationEntity allocation = ItemAllocationEntity.builder()
                .parentItem(parentItem)
                .asset(targetAsset)
                .childItem(childItem)
                .quantity(quantity)
                .allocatedAt(LocalDateTime.now())
                .allocatedBy(currentTechnician)
                .ticket(associatedTicket)
                .build();

        itemAllocationRepository.save(allocation);

        log.info("Alocação patrimonial efetuada com sucesso: {} unidade(s) de '{}' alocadas ao ativo '{}'. " +
                 "Técnico responsável: {} (ID: {}).",
                 quantity, childItem.getName(), destinationName, currentTechnician.getName(), authenticatedUserId);

        return totalCost;
    }
}
