package br.dev.ctrls.inovareti.domain.ticket.usecase;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.core.exception.NotFoundException;
import br.dev.ctrls.inovareti.domain.asset.Asset;
import br.dev.ctrls.inovareti.domain.asset.AssetCategory;
import br.dev.ctrls.inovareti.domain.asset.AssetCategoryRepository;
import br.dev.ctrls.inovareti.domain.asset.AssetMaintenance;
import br.dev.ctrls.inovareti.domain.asset.AssetMaintenanceRepository;
import br.dev.ctrls.inovareti.domain.asset.AssetRepository;
import br.dev.ctrls.inovareti.domain.inventory.ItemRepository;
import br.dev.ctrls.inovareti.domain.notification.CreateNotificationService;
import br.dev.ctrls.inovareti.domain.ticket.Ticket;
import br.dev.ctrls.inovareti.domain.ticket.TicketRepository;
import br.dev.ctrls.inovareti.domain.ticket.TicketStatus;
import br.dev.ctrls.inovareti.domain.ticket.dto.ResolveTicketDTO;
import br.dev.ctrls.inovareti.domain.ticket.dto.TicketResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Use case: resolves a ticket with optional asset/item fulfillment.
 * Critical business rules:
 * 1. If the ticket has requestedItem and requestedQuantity,
 *    item currentStock is decremented atomically in the same transaction.
 * 2. If assetIdToDeliver is provided, asset ownership is transferred to requester
 *    and an AssetMaintenance TRANSFER record is created.
 * 3. If inventoryItemIdToDeliver is provided, stock is decremented.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResolveTicketUseCase {

    private final TicketRepository ticketRepository;
    private final ItemRepository itemRepository;
    private final AssetRepository assetRepository;
        private final AssetCategoryRepository assetCategoryRepository;
    private final AssetMaintenanceRepository assetMaintenanceRepository;
    private final CreateNotificationService createNotificationService;

    /**
     * Resolves a ticket and optionally delivers assets/items.
     *
     * @param ticketId UUID of the ticket to resolve
     * @param request DTO with resolution notes and optional fulfillment data
     * @return DTO with updated ticket data
     * @throws NotFoundException if ticket or asset/item is not found
     * @throws IllegalStateException if business rules are violated
     */
    @Transactional
    public TicketResponseDTO execute(UUID ticketId, ResolveTicketDTO request) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new NotFoundException(
                        "Ticket not found with id: " + ticketId));

        // Debit inventory stock if ticket has requested item
        if (ticket.getRequestedItem() != null && ticket.getRequestedQuantity() != null) {
            var item = ticket.getRequestedItem();
            int newStock = item.getCurrentStock() - ticket.getRequestedQuantity();

            if (newStock < 0) {
                throw new IllegalStateException(
                        "Insufficient stock for item '" + item.getName()
                        + "'. Current stock: " + item.getCurrentStock()
                        + ", requested quantity: " + ticket.getRequestedQuantity());
            }

            item.setCurrentStock(newStock);
            itemRepository.save(item);
            log.info("Stock debited for ticket {}: item '{}', quantity: {}, new stock: {}",
                    ticketId, item.getName(), ticket.getRequestedQuantity(), newStock);
        }

        if (request.assetIdToDeliver() != null && request.newAssetToDeliver() != null) {
            throw new IllegalStateException(
                    "Choose only one asset delivery mode: existing asset or quick new asset registration.");
        }

        // Deliver asset if assetIdToDeliver is provided
        if (request.assetIdToDeliver() != null) {
            Asset asset = assetRepository.findById(request.assetIdToDeliver())
                    .orElseThrow(() -> new NotFoundException(
                            "Asset not found with id: " + request.assetIdToDeliver()));

            if (asset.getUserId() != null && !asset.getUserId().equals(ticket.getRequester().getId())) {
                throw new IllegalStateException(
                        "Asset '" + asset.getName() + "' is already assigned to another user. "
                        + "Cannot deliver asset that is already in use.");
            }

            // Transfer asset ownership to requester
            asset.setUserId(ticket.getRequester().getId());
            Asset deliveredAsset = assetRepository.save(asset);
            log.info("Asset {} transferred to user {} via ticket {}",
                    asset.getId(), ticket.getRequester().getId(), ticketId);

            // Create AssetMaintenance record for audit trail
            AssetMaintenance maintenance = AssetMaintenance.builder()
                    .asset(deliveredAsset)
                    .maintenanceDate(LocalDate.now())
                    .type(AssetMaintenance.MaintenanceType.TRANSFER)
                    .description("Entregue via Chamado #" + ticket.getId().toString().substring(0, 8))
                    .cost(BigDecimal.ZERO)
                    .technician(ticket.getAssignedTo() != null ? ticket.getAssignedTo() : ticket.getRequester())
                    .build();
            assetMaintenanceRepository.save(maintenance);
            log.info("AssetMaintenance TRANSFER record created for asset {}", asset.getId());
        }

                // Quick-register and deliver a new asset if provided
                if (request.newAssetToDeliver() != null) {
                        var newAssetRequest = request.newAssetToDeliver();

                        if (newAssetRequest.name() == null || newAssetRequest.name().isBlank()) {
                                throw new IllegalStateException("New asset name is required.");
                        }
                        if (newAssetRequest.patrimonyCode() == null || newAssetRequest.patrimonyCode().isBlank()) {
                                throw new IllegalStateException("New asset patrimony code is required.");
                        }
                        if (newAssetRequest.categoryId() == null) {
                                throw new IllegalStateException("New asset category is required.");
                        }
                        if (assetRepository.existsByPatrimonyCode(newAssetRequest.patrimonyCode().trim())) {
                                throw new IllegalStateException(
                                                "Patrimony code '" + newAssetRequest.patrimonyCode().trim() + "' is already in use.");
                        }

                        AssetCategory category = assetCategoryRepository.findById(newAssetRequest.categoryId())
                                        .orElseThrow(() -> new NotFoundException(
                                                        "Asset category not found with id: " + newAssetRequest.categoryId()));

                        Asset newAsset = Asset.builder()
                                        .userId(ticket.getRequester().getId())
                                        .name(newAssetRequest.name().trim())
                                        .patrimonyCode(newAssetRequest.patrimonyCode().trim())
                                        .category(category)
                                        .specifications(newAssetRequest.specifications())
                                        .build();

                        Asset deliveredAsset = assetRepository.save(newAsset);
                        log.info("New asset {} created and delivered to requester {} via ticket {}",
                                        deliveredAsset.getId(), ticket.getRequester().getId(), ticketId);

                        AssetMaintenance maintenance = AssetMaintenance.builder()
                                        .asset(deliveredAsset)
                                        .maintenanceDate(LocalDate.now())
                                        .type(AssetMaintenance.MaintenanceType.TRANSFER)
                                        .description("Ativo cadastrado e entregue via Chamado #" + ticket.getId())
                                        .cost(BigDecimal.ZERO)
                                        .technician(ticket.getAssignedTo() != null ? ticket.getAssignedTo() : ticket.getRequester())
                                        .build();
                        assetMaintenanceRepository.save(maintenance);
                        log.info("AssetMaintenance TRANSFER record created for quick-registered asset {}", deliveredAsset.getId());
                }

        // Deliver inventory item if inventoryItemIdToDeliver is provided
        if (request.inventoryItemIdToDeliver() != null && request.quantityToDeliver() != null) {
            var item = itemRepository.findById(request.inventoryItemIdToDeliver())
                    .orElseThrow(() -> new NotFoundException(
                            "Item not found with id: " + request.inventoryItemIdToDeliver()));

            int newStock = item.getCurrentStock() - request.quantityToDeliver();

            if (newStock < 0) {
                throw new IllegalStateException(
                        "Insufficient stock for item '" + item.getName()
                        + "'. Current stock: " + item.getCurrentStock()
                        + ", requested quantity: " + request.quantityToDeliver());
            }

            item.setCurrentStock(newStock);
            itemRepository.save(item);
            log.info("Stock debited for delivery in ticket {}: item '{}', quantity: {}, new stock: {}",
                    ticketId, item.getName(), request.quantityToDeliver(), newStock);
        }

        // Mark ticket as resolved
        ticket.setStatus(TicketStatus.RESOLVED);
        ticket.setClosedAt(LocalDateTime.now());

        Ticket resolvedTicket = ticketRepository.save(ticket);

        createNotificationService.create(
                resolvedTicket.getRequester().getId(),
                "Chamado Resolvido",
                "Seu chamado #" + resolvedTicket.getId().toString().substring(0, 8) + " foi resolvido",
                "/tickets/" + resolvedTicket.getId()
        );

        log.info("Ticket {} resolved successfully with fulfillment", ticketId);

        return TicketResponseDTO.from(resolvedTicket);
    }

    /**
     * Legacy method for backward compatibility: resolves without fulfillment.
     */
    @Transactional
    public TicketResponseDTO execute(UUID ticketId) {
                return execute(ticketId, new ResolveTicketDTO(null, null, null, null, null));
    }
}

