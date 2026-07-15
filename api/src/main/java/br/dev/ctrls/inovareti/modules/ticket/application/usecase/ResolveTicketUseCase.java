package br.dev.ctrls.inovareti.modules.ticket.application.usecase;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.core.shared.domain.model.exception.NotFoundException;
import br.dev.ctrls.inovareti.modules.asset.domain.model.Asset;
import br.dev.ctrls.inovareti.modules.asset.domain.model.AssetCategory;
import br.dev.ctrls.inovareti.modules.asset.domain.model.AssetMaintenance;
import br.dev.ctrls.inovareti.modules.asset.domain.port.output.AssetCategoryRepositoryPort;
import br.dev.ctrls.inovareti.modules.asset.domain.port.output.AssetMaintenanceRepositoryPort;
import br.dev.ctrls.inovareti.modules.asset.domain.port.output.AssetRepositoryPort;
import br.dev.ctrls.inovareti.modules.audit.application.service.AuditLogService;
import br.dev.ctrls.inovareti.modules.audit.domain.model.AuditAction;
import br.dev.ctrls.inovareti.modules.audit.domain.model.AuditEvent;
import br.dev.ctrls.inovareti.modules.inventory.application.service.StockDeductionService;
import br.dev.ctrls.inovareti.modules.notification.application.service.CreateNotificationService;
import br.dev.ctrls.inovareti.modules.notification.infrastructure.adapter.output.discord.bot.DiscordDirectMessageService;
import br.dev.ctrls.inovareti.modules.ticket.application.dto.ResolveTicketDTO;
import br.dev.ctrls.inovareti.modules.ticket.application.dto.TicketResponseDTO;
import br.dev.ctrls.inovareti.modules.inventory.application.usecase.AllocateConsumableUseCase;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.Ticket;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.TicketStatus;
import br.dev.ctrls.inovareti.modules.ticket.domain.port.output.TicketRepositoryPort;
import br.dev.ctrls.inovareti.modules.ticket.domain.event.TicketResolvedEvent;
import br.dev.ctrls.inovareti.modules.user.domain.model.User;
import br.dev.ctrls.inovareti.modules.user.domain.model.UserRole;
import br.dev.ctrls.inovareti.modules.user.domain.port.output.UserRepositoryPort;
import org.springframework.context.ApplicationEventPublisher;
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

        private final TicketRepositoryPort ticketRepository;
        private final AssetRepositoryPort assetRepository;
        private final AssetCategoryRepositoryPort assetCategoryRepository;
        private final AssetMaintenanceRepositoryPort assetMaintenanceRepository;
        private final CreateNotificationService createNotificationService;
        private final StockDeductionService stockDeductionService;
        private final br.dev.ctrls.inovareti.modules.finance.application.service.FinancialService financialService;
        private final DiscordDirectMessageService discordDirectMessageService;
        private final UserRepositoryPort userRepository;
        private final AuditLogService auditLogService;
        private final AllocateConsumableUseCase allocateConsumableUseCase;
        private final ApplicationEventPublisher eventPublisher;

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

        // Debita estoque de forma consolidada e atómica para todos os itens da entrega
        if (request.itemsToDeliver() != null && !request.itemsToDeliver().isEmpty()) {
            for (var itemDeduction : request.itemsToDeliver()) {
                if (itemDeduction.quantity() <= 0) {
                    throw new IllegalStateException("Quantidade a entregar deve ser maior que zero.");
                }

                java.math.BigDecimal totalDeduction;
                if (request.targetAssetId() != null) {
                    // Se o targetAssetId não for nulo, aloca o insumo ao ativo físico
                    totalDeduction = allocateConsumableUseCase.execute(
                            null,
                            request.targetAssetId(),
                            itemDeduction.itemId(),
                            itemDeduction.quantity(),
                            ticketId
                    );
                    log.info("[ALOCAÇÃO] Insumo alocado ao ativo físico no fechamento. Chamado: {}, Item: {}, Ativo: {}, Qtd: {}, Custo: {}",
                            ticketId, itemDeduction.itemId(), request.targetAssetId(), itemDeduction.quantity(), totalDeduction);
                } else {
                    // Fallback para dedução simples de estoque
                    String reference = "TICKET:" + ticketId;
                    totalDeduction = stockDeductionService.deductWithFifo(
                            itemDeduction.itemId(),
                            itemDeduction.quantity(),
                            reference,
                            itemDeduction.recipientUserId()
                    );
                    log.info("[ESTOQUE] Baixa de stock realizada no fechamento. Chamado: {}, Item: {}, Qtd: {}, Recebedor: {}, Custo: {}",
                            ticketId, itemDeduction.itemId(), itemDeduction.quantity(), itemDeduction.recipientUserId(), totalDeduction);
                }

                financialService.recordDebitForTicket(ticket, "INVENTORY", totalDeduction);
            }
        } else if (ticket.getRequestedItems() != null && !ticket.getRequestedItems().isEmpty()) {
            for (var reqItem : ticket.getRequestedItems()) {
                java.util.UUID recipient = request.recipientUserId() != null ? request.recipientUserId() : ticket.getRequester().getId();
                java.math.BigDecimal totalDeduction;

                if (request.targetAssetId() != null) {
                    // Se o targetAssetId não for nulo, aloca o insumo solicitado ao ativo físico
                    totalDeduction = allocateConsumableUseCase.execute(
                            null,
                            request.targetAssetId(),
                            reqItem.getItem().getId(),
                            reqItem.getQuantity(),
                            ticketId
                    );
                    log.info("[ALOCAÇÃO] Insumo solicitado alocado ao ativo físico no fechamento. Chamado: {}, Item: {}, Ativo: {}, Qtd: {}, Custo: {}",
                            ticketId, reqItem.getItem().getId(), request.targetAssetId(), reqItem.getQuantity(), totalDeduction);
                } else {
                    // Fallback para dedução simples de estoque
                    String reference = "TICKET:" + ticketId;
                    totalDeduction = stockDeductionService.deductWithFifo(
                            reqItem.getItem().getId(),
                            reqItem.getQuantity(),
                            reference,
                            recipient
                    );
                    log.info("[ESTOQUE] Baixa automatica de stock realizada no fechamento. Chamado: {}, Item: {}, Qtd: {}, Recebedor: {}, Custo: {}",
                            ticketId, reqItem.getItem().getId(), reqItem.getQuantity(), recipient, totalDeduction);
                }

                financialService.recordDebitForTicket(ticket, "INVENTORY", totalDeduction);
            }
        } else if (ticket.getRequestedItem() != null) {
            java.util.UUID recipient = request.recipientUserId() != null ? request.recipientUserId() : ticket.getRequester().getId();
            int qty = ticket.getRequestedQuantity() != null ? ticket.getRequestedQuantity() : 1;
            java.math.BigDecimal totalDeduction;

            if (request.targetAssetId() != null) {
                totalDeduction = allocateConsumableUseCase.execute(
                        null,
                        request.targetAssetId(),
                        ticket.getRequestedItem().getId(),
                        qty,
                        ticketId
                );
                log.info("[ALOCAÇÃO] Insumo solicitado legado alocado ao ativo físico no fechamento. Chamado: {}, Item: {}, Ativo: {}, Qtd: {}, Custo: {}",
                        ticketId, ticket.getRequestedItem().getId(), request.targetAssetId(), qty, totalDeduction);
            } else {
                String reference = "TICKET:" + ticketId;
                totalDeduction = stockDeductionService.deductWithFifo(
                        ticket.getRequestedItem().getId(),
                        qty,
                        reference,
                        recipient
                );
                log.info("[ESTOQUE] Baixa automatica de stock legado realizada no fechamento. Chamado: {}, Item: {}, Qtd: {}, Recebedor: {}, Custo: {}",
                        ticketId, ticket.getRequestedItem().getId(), qty, recipient, totalDeduction);
            }
            financialService.recordDebitForTicket(ticket, "INVENTORY", totalDeduction);
        }

        if (request.targetAssetId() != null) {
            Asset targetAsset = assetRepository.findById(request.targetAssetId())
                    .orElseThrow(() -> new NotFoundException("Ativo não encontrado com id: " + request.targetAssetId()));
            ticket.setAsset(targetAsset);
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

        // Se houver manutenção informada no DTO, persistir em asset_maintenances vinculando o ticket
        if (request.maintenance() != null) {
            var maintReq = request.maintenance();
            if (maintReq.assetId() == null) {
                throw new IllegalStateException("Ativo (assetId) é obrigatório para registrar a manutenção.");
            }
            Asset asset = assetRepository.findById(maintReq.assetId())
                    .orElseThrow(() -> new NotFoundException("Ativo não encontrado com id: " + maintReq.assetId()));

            AssetMaintenance maintenanceObj = AssetMaintenance.builder()
                    .asset(asset)
                    .maintenanceDate(LocalDate.now())
                    .type(maintReq.type() != null ? maintReq.type() : AssetMaintenance.MaintenanceType.CORRECTIVE)
                    .description(maintReq.description() != null ? maintReq.description().trim() : null)
                    .cost(maintReq.cost() != null ? maintReq.cost() : BigDecimal.ZERO)
                    .technician(authenticatedUser)
                    .ticket(ticket)
                    .build();

            assetMaintenanceRepository.save(maintenanceObj);
            log.info("[MANUTENÇÃO] Registro de manutenção {} criado vinculado ao chamado {}", 
                    maintenanceObj.getType(), ticketId);
        }

        // Marca o chamado como resolvido e salva a nota de resolução (texto da solução)
        ticket.setStatus(TicketStatus.RESOLVED);
        ticket.setClosedAt(LocalDateTime.now());
        ticket.setSolutionText(request.resolutionNotes() != null ? request.resolutionNotes().trim() : null);

        Ticket resolvedTicket = ticketRepository.save(ticket);
        eventPublisher.publishEvent(new TicketResolvedEvent(resolvedTicket));
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

            for (br.dev.ctrls.inovareti.modules.user.domain.model.User affectedUser : resolvedTicket.getAdditionalUsers()) {
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


