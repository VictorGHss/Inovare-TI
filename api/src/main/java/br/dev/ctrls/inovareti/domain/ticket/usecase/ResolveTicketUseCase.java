package br.dev.ctrls.inovareti.domain.ticket.usecase;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.core.exception.NotFoundException;
import br.dev.ctrls.inovareti.domain.audit.AuditAction;
import br.dev.ctrls.inovareti.domain.audit.AuditEvent;
import br.dev.ctrls.inovareti.domain.audit.AuditLogService;
import br.dev.ctrls.inovareti.domain.asset.Asset;
import br.dev.ctrls.inovareti.domain.asset.AssetCategory;
import br.dev.ctrls.inovareti.domain.asset.AssetCategoryRepository;
import br.dev.ctrls.inovareti.domain.asset.AssetMaintenance;
import br.dev.ctrls.inovareti.domain.asset.AssetMaintenanceRepository;
import br.dev.ctrls.inovareti.domain.asset.AssetRepository;
import br.dev.ctrls.inovareti.domain.inventory.StockDeductionService;
import br.dev.ctrls.inovareti.domain.notification.CreateNotificationService;
import br.dev.ctrls.inovareti.domain.notification.discord.bot.DiscordDirectMessageService;
import br.dev.ctrls.inovareti.domain.ticket.Ticket;
import br.dev.ctrls.inovareti.domain.ticket.TicketRepository;
import br.dev.ctrls.inovareti.domain.ticket.TicketStatus;
import br.dev.ctrls.inovareti.domain.ticket.dto.ResolveTicketDTO;
import br.dev.ctrls.inovareti.domain.ticket.dto.TicketResponseDTO;
import br.dev.ctrls.inovareti.domain.user.User;
import br.dev.ctrls.inovareti.domain.user.UserRepository;
import br.dev.ctrls.inovareti.domain.user.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Caso de uso: resolve um chamado com entrega opcional de equipamento ou item.
 * Regras de negócio críticas:
 * 1. Se o chamado possuir requestedItem e requestedQuantity,
 *    o estoque atual do item é decrementado atomicamente na mesma transação.
 * 2. Se assetIdToDeliver for fornecido, a propriedade do equipamento é transferida para o solicitante
 *    e um registro AssetMaintenance de TRANSFER é criado.
 * 3. Se inventoryItemIdToDeliver for fornecido, o estoque é decrementado.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResolveTicketUseCase {

        private final TicketRepository ticketRepository;
        private final AssetRepository assetRepository;
        private final AssetCategoryRepository assetCategoryRepository;
        private final AssetMaintenanceRepository assetMaintenanceRepository;
        private final CreateNotificationService createNotificationService;
        private final StockDeductionService stockDeductionService;
        private final DiscordDirectMessageService discordDirectMessageService;
        private final UserRepository userRepository;
        private final AuditLogService auditLogService;

    /**
     * Resolve um chamado e, opcionalmente, entrega equipamentos ou itens.
     *
     * @param ticketId UUID do chamado a ser resolvido
     * @param request DTO com notas de resolução e dados opcionais de entrega
     * @return DTO com os dados atualizados do chamado
     * @throws NotFoundException se o chamado ou equipamento/item não for encontrado
     * @throws IllegalStateException se regras de negócio forem violadas
     */
    @Transactional
    public TicketResponseDTO execute(UUID ticketId, ResolveTicketDTO request, UUID authenticatedUserId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new NotFoundException(
                        "Ticket not found with id: " + ticketId));

        User authenticatedUser = userRepository.findById(authenticatedUserId)
                .orElseThrow(() -> new NotFoundException(
                        "Authenticated user not found with id: " + authenticatedUserId));

        boolean isAdminOrTechnician = authenticatedUser.getRole() == UserRole.ADMIN
                || authenticatedUser.getRole() == UserRole.TECHNICIAN;
        boolean isRequester = ticket.getRequester().getId().equals(authenticatedUserId);

        if (!isAdminOrTechnician && !isRequester) {
            throw new AccessDeniedException("Você não tem permissão para alterar este chamado.");
        }

        // Debita estoque se o chamado tiver item solicitado
        if (ticket.getRequestedItem() != null && ticket.getRequestedQuantity() != null) {
            String reference = "TICKET:" + ticketId + "|REQUESTER:" + ticket.getRequester().getId();
            stockDeductionService.deductWithFifo(
                    ticket.getRequestedItem().getId(),
                    ticket.getRequestedQuantity(),
                    reference
            );
            log.info("Stock debited with FIFO for requested item in ticket {}", ticketId);
        }

        if (request.assetIdToDeliver() != null && request.newAssetToDeliver() != null) {
            throw new IllegalStateException(
                    "Choose only one asset delivery mode: existing asset or quick new asset registration.");
        }

        // Entrega equipamento existente se assetIdToDeliver for fornecido
        if (request.assetIdToDeliver() != null) {
            Asset asset = assetRepository.findById(request.assetIdToDeliver())
                    .orElseThrow(() -> new NotFoundException(
                            "Asset not found with id: " + request.assetIdToDeliver()));

            if (asset.getUserId() != null && !asset.getUserId().equals(ticket.getRequester().getId())) {
                throw new IllegalStateException(
                        "Asset '" + asset.getName() + "' is already assigned to another user. "
                        + "Cannot deliver asset that is already in use.");
            }

            // Transfere a propriedade do equipamento para o solicitante
            asset.setUserId(ticket.getRequester().getId());
            Asset deliveredAsset = assetRepository.save(asset);
            log.info("Asset {} transferred to user {} via ticket {}",
                    asset.getId(), ticket.getRequester().getId(), ticketId);

            // Cria registro de auditoria (AssetMaintenance) da transferência
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

        // Cadastra rapidamente e entrega novo equipamento se fornecido
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

        // Entrega item de estoque se inventoryItemIdToDeliver for fornecido
        if (request.inventoryItemIdToDeliver() != null && request.quantityToDeliver() != null) {
            String reference = "TICKET:" + ticketId + "|REQUESTER:" + ticket.getRequester().getId();
            stockDeductionService.deductWithFifo(
                    request.inventoryItemIdToDeliver(),
                    request.quantityToDeliver(),
                    reference
            );
            log.info("Stock debited with FIFO for manual item delivery in ticket {}", ticketId);
        }

        // Marca o chamado como resolvido
        ticket.setStatus(TicketStatus.RESOLVED);
        ticket.setClosedAt(LocalDateTime.now());

        Ticket resolvedTicket = ticketRepository.save(ticket);
        auditLogService.publish(AuditEvent.of(AuditAction.TICKET_RESOLVE)
                .userId(authenticatedUserId)
                .resourceType("Ticket")
                .resourceId(resolvedTicket.getId())
                .details("{\"status\": \"RESOLVED\"}")
                .build());

        String shortId = resolvedTicket.getId().toString().substring(0, 8).toUpperCase();
        String resolutionText = request.resolutionNotes() != null && !request.resolutionNotes().isBlank()
                ? request.resolutionNotes().trim()
                : "Não informada.";
        String dmTitle = "Chamado Resolvido";
        String dmDescription = "Seu chamado #" + shortId + " foi marcado como resolvido.\n"
                + "**Resolução:** " + resolutionText;
        discordDirectMessageService.sendTicketUpdateDM(resolvedTicket, dmTitle, dmDescription);

        createNotificationService.create(
                resolvedTicket.getRequester().getId(),
                "Chamado Resolvido",
                "Seu chamado #" + resolvedTicket.getId().toString().substring(0, 8) + " foi resolvido",
                "/tickets/" + resolvedTicket.getId()
        );

        log.info("Ticket {} resolved successfully with fulfillment", ticketId);

        return TicketResponseDTO.from(resolvedTicket);
    }

}

