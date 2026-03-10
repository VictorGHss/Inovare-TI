package br.dev.ctrls.inovareti.domain.ticket.dto;

import java.util.UUID;

import br.dev.ctrls.inovareti.domain.asset.dto.AssetRequestDTO;

/**
 * DTO para resolver um chamado com entrega opcional de equipamento ou item.
 */
public record ResolveTicketDTO(
    String resolutionNotes,
    UUID assetIdToDeliver,
    UUID inventoryItemIdToDeliver,
    Integer quantityToDeliver,
    AssetRequestDTO newAssetToDeliver
) {}
