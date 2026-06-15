package br.dev.ctrls.inovareti.modules.ticket.application.dto;

import java.util.UUID;

import br.dev.ctrls.inovareti.modules.asset.application.dto.AssetRequestDTO;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
/**
 * DTO para resolver um chamado com entrega opcional de equipamento ou item.
 */
public record ResolveTicketDTO(
    @NotBlank(message = "Notas de resolução são obrigatórias.")
    @Size(max = 2000, message = "As notas de resolução devem conter no máximo 2000 caracteres.")
    String resolutionNotes,
    
    UUID assetIdToDeliver,
    AssetRequestDTO newAssetToDeliver,
    UUID recipientUserId,
    UUID targetAssetId,
    
    java.util.List<TicketItemDeductionDTO> itemsToDeliver
) {
    public record TicketItemDeductionDTO(
        UUID itemId,
        Integer quantity,
        UUID recipientUserId
    ) {}
}
