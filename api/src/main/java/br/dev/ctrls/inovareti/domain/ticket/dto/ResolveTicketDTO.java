package br.dev.ctrls.inovareti.domain.ticket.dto;

import java.util.UUID;

import br.dev.ctrls.inovareti.domain.asset.dto.AssetRequestDTO;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;

/**
 * DTO para resolver um chamado com entrega opcional de equipamento ou item.
 */
public record ResolveTicketDTO(
    @NotBlank(message = "Notas de resolução são obrigatórias.")
    @Size(max = 2000, message = "As notas de resolução devem conter no máximo 2000 caracteres.")
    String resolutionNotes,
    
    UUID assetIdToDeliver,
    UUID inventoryItemIdToDeliver,
    
    @Positive(message = "A quantidade a entregar deve ser maior que zero.")
    Integer quantityToDeliver,
    
    AssetRequestDTO newAssetToDeliver,
    UUID recipientUserId
) {}
