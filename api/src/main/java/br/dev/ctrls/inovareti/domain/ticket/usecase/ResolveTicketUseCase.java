package br.dev.ctrls.inovareti.domain.ticket.usecase;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
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
import br.dev.ctrls.inovareti.domain.inventory.StockMovement;
import br.dev.ctrls.inovareti.domain.inventory.StockMovementRepository;
import br.dev.ctrls.inovareti.domain.inventory.StockMovementType;
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
        private final StockMovementRepository stockMovementRepository;
        private final br.dev.ctrls.inovareti.modules.finance.application.service.FinancialService financialService;
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
                        "Chamado não encontrado com id: " + ticketId));

        User authenticatedUser = userRepository.findById(authenticatedUserId)
                .orElseThrow(() -> new NotFoundException(
                        "Usuário autenticado não encontrado com id: " + authenticatedUserId));

        boolean isAdminOrTechnician = authenticatedUser.getRole() == UserRole.ADMIN
                || authenticatedUser.getRole() == UserRole.TECHNICIAN;
        boolean isRequester = ticket.getRequester().getId().equals(authenticatedUserId);

        if (!isAdminOrTechnician && !isRequester) {
            throw new AccessDeniedException("Você não tem permissão para alterar este chamado.");
        }

        // Debita estoque se o chamado tiver item solicitado
        // Observações em Português: garantimos aqui que a dedução de estoque
        // será realizada dentro da mesma transação que marca o chamado como RESOLVED.
        // O parâmetro `reference` segue o padrão: "TICKET:{ticketId}" para garantir
        // que relatórios que buscam por prefixo (ex.: TICKET:{id}) encontrem o movimento.
        if (ticket.getRequestedItem() != null && ticket.getRequestedQuantity() != null) {
            if (ticket.getRequestedQuantity() <= 0) {
                throw new IllegalStateException("Quantidade solicitada deve ser maior ou igual a 1.");
            }

            String reference = "TICKET:" + ticketId;
            java.math.BigDecimal totalDeduction = stockDeductionService.deductWithFifo(
                    ticket.getRequestedItem().getId(),
                    ticket.getRequestedQuantity(),
                    reference,
                    request.recipientUserId()
            );

            // Registra débito financeiro com base no valor apurado pela dedução FIFO
            financialService.recordDebitForTicket(ticket, "INVENTORY", totalDeduction);
            log.info("[ESTOQUE] Baixa automática realizada para item solicitado no chamado. "
                    + "Chamado: {}, Item: {}, Quantidade: {}, Valor total deduzido (FIFO): {}",
                    ticketId, ticket.getRequestedItem().getId(), ticket.getRequestedQuantity(), totalDeduction);

            // Fallback de segurança: caso o StockDeductionService não tenha persistido
            // o movimento (situação incomum), cria-se um registro adicional para
            // preservar a trilha de auditoria e permitir que relatórios encontrem a saída.
            try {
                var movements = stockMovementRepository.findByReferenceStartingWithAndTypeOrderByDateDesc(reference, StockMovementType.OUT);
                if (movements == null || movements.isEmpty()) {
                    StockMovement movement = StockMovement.builder()
                            .itemId(ticket.getRequestedItem().getId())
                            .type(StockMovementType.OUT)
                            .quantity(ticket.getRequestedQuantity())
                            .reference(reference)
                            .date(LocalDateTime.now())
                            .unitPriceAtTime(totalDeduction)
                            .recipientUserId(request.recipientUserId())
                            .build();
                    stockMovementRepository.save(movement);
                    log.info("Fallback: StockMovement criado para ticket {} (requestedItem)", ticketId);
                }
            } catch (Exception e) {
                log.warn("Falha ao verificar/criar fallback de StockMovement para ticket {}: {}", ticketId, e.getMessage());
            }
        }

        if (request.assetIdToDeliver() != null && request.newAssetToDeliver() != null) {
            throw new IllegalStateException(
                    "Choose only one asset delivery mode: existing asset or quick new asset registration.");
        }

        // Entrega equipamento existente se assetIdToDeliver for fornecido
        if (request.assetIdToDeliver() != null) {
            Asset asset = assetRepository.findById(request.assetIdToDeliver())
                    .orElseThrow(() -> new NotFoundException(
                            "Ativo não encontrado com id: " + request.assetIdToDeliver()));

                        // Verifica se o ativo já está em uso exclusivo por outro usuário
                        boolean emUso = asset.getUsers() != null && !asset.getUsers().isEmpty();
                        boolean pertenceAoSolicitante = emUso
                                && asset.getUsers().stream().anyMatch(u -> u.getId().equals(ticket.getRequester().getId()));

                        if (emUso && !pertenceAoSolicitante) {
                                throw new IllegalStateException(
                                                "Ativo '" + asset.getName() + "' já está atribuído a outro usuário. "
                                                + "Não é possível entregar um ativo que já está em uso.");
                        }

            // Adiciona o solicitante à coleção de usuários do ativo
            if (asset.getUsers() == null) {
                asset.setUsers(new HashSet<>());
            }
            asset.getUsers().add(ticket.getRequester());
            Asset deliveredAsset = assetRepository.save(asset);
            log.info("[ATIVO] Ativo {} vinculado ao usuário {} via chamado {}",
                    asset.getId(), ticket.getRequester().getId(), ticketId);

            AssetMaintenance maintenance = AssetMaintenance.builder()
                    .asset(deliveredAsset)
                    .maintenanceDate(LocalDate.now())
                    .type(AssetMaintenance.MaintenanceType.TRANSFER)
                    .description("[TICKET:" + ticket.getId() + "] Entregue via Chamado #" + ticket.getId().toString().substring(0, 8))
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
                    .users(new HashSet<>(java.util.Set.of(ticket.getRequester())))
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
                    .description("[TICKET:" + ticket.getId() + "] Ativo cadastrado e entregue via Chamado #" + ticket.getId().toString().substring(0, 8))
                    .cost(BigDecimal.ZERO)
                    .technician(ticket.getAssignedTo() != null ? ticket.getAssignedTo() : ticket.getRequester())
                    .build();
            assetMaintenanceRepository.save(maintenance);
            log.info("AssetMaintenance TRANSFER record created for quick-registered asset {}", deliveredAsset.getId());
        }

        // Entrega item de estoque se inventoryItemIdToDeliver for fornecido
        // Entrega item de estoque se inventoryItemIdToDeliver for fornecido
        // Garantimos quantidade mínima e uso do padrão de referência `TICKET:{id}`
        if (request.inventoryItemIdToDeliver() != null && request.quantityToDeliver() != null) {
            if (request.quantityToDeliver() <= 0) {
                throw new IllegalStateException("Quantidade para entrega deve ser maior ou igual a 1.");
            }

            String reference = "TICKET:" + ticketId;
            java.math.BigDecimal totalDeduction = stockDeductionService.deductWithFifo(
                    request.inventoryItemIdToDeliver(),
                    request.quantityToDeliver(),
                    reference,
                    request.recipientUserId()
            );

            // Registra débito financeiro para a entrega manual de item (INVENTORY)
            financialService.recordDebitForTicket(ticket, "INVENTORY", totalDeduction);
            log.info("[ESTOQUE] Baixa automática realizada via encerramento do chamado. "
                    + "Chamado: {}, Item: {}, Quantidade: {}, Valor total deduzido (FIFO): {}",
                    ticketId, request.inventoryItemIdToDeliver(), request.quantityToDeliver(), totalDeduction);

            // Mesmo fallback para entregas manuais: garante persistência do movimento OUT
            try {
                var movements = stockMovementRepository.findByReferenceStartingWithAndTypeOrderByDateDesc(reference, StockMovementType.OUT);
                if (movements == null || movements.isEmpty()) {
                    StockMovement movement = StockMovement.builder()
                            .itemId(request.inventoryItemIdToDeliver())
                            .type(StockMovementType.OUT)
                            .quantity(request.quantityToDeliver())
                            .reference(reference)
                            .date(LocalDateTime.now())
                            .unitPriceAtTime(totalDeduction)
                            .recipientUserId(request.recipientUserId())
                            .build();
                    stockMovementRepository.save(movement);
                    log.info("Fallback: StockMovement criado para ticket {} (manual delivery)", ticketId);
                }
            } catch (Exception e) {
                log.warn("Falha ao verificar/criar fallback de StockMovement para entrega manual no ticket {}: {}", ticketId, e.getMessage());
            }
        }

        // Marca o chamado como resolvido e salva a nota de resolução (texto da solução)
        ticket.setStatus(TicketStatus.RESOLVED);
        ticket.setClosedAt(LocalDateTime.now());
        ticket.setSolutionText(request.resolutionNotes() != null ? request.resolutionNotes().trim() : null);

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

        // Loop de notificação: DM para cada usuário adicional afetado pelo chamado
        if (resolvedTicket.getAdditionalUsers() != null && !resolvedTicket.getAdditionalUsers().isEmpty()) {
            String affectedTitle = "Problema Resolvido — Chamado #" + shortId;
            String affectedDescription = "Um chamado que lhe afetava (#" + shortId + ") foi resolvido.\n"
                    + "**Título:** " + resolvedTicket.getTitle() + "\n"
                    + "**Resolução:** " + resolutionText;

            for (br.dev.ctrls.inovareti.domain.user.User affectedUser : resolvedTicket.getAdditionalUsers()) {
                if (affectedUser.getDiscordUserId() != null && !affectedUser.getDiscordUserId().isBlank()) {
                    discordDirectMessageService.sendTicketUpdateDMToUser(
                            affectedUser.getDiscordUserId(),
                            resolvedTicket.getId(),
                            affectedTitle,
                            affectedDescription
                    );
                    log.info("[TICKET] DM de resolução enviada para usuário adicional afetado '{}' no chamado {}",
                            affectedUser.getName(), ticketId);
                } else {
                    log.debug("[TICKET] Usuário adicional '{}' não possui Discord vinculado. DM ignorada.",
                            affectedUser.getName());
                }
            }
        }

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


