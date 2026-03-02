package br.dev.ctrls.inovareti.domain.user.dto;

import java.util.UUID;

import br.dev.ctrls.inovareti.domain.user.Sector;

/**
 * DTO de saída com os dados de um setor.
 */
public record SectorResponseDTO(
        UUID id,
        String name
) {
    /** Converte uma entidade {@link Sector} para este DTO. */
    public static SectorResponseDTO from(Sector sector) {
        return new SectorResponseDTO(sector.getId(), sector.getName());
    }
}
