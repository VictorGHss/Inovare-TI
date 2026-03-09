package br.dev.ctrls.inovareti.domain.ticket.dto;

import java.util.UUID;

import br.dev.ctrls.inovareti.domain.asset.dto.AssetRequestDTO;

/**
 * DTO for resolving a ticket with optional asset/item delivery fulfillment.
 */
public record ResolveTicketDTO(
    String resolutionNotes,
    UUID assetIdToDeliver,
    UUID inventoryItemIdToDeliver,
    Integer quantityToDeliver,
    AssetRequestDTO newAssetToDeliver
) {}
