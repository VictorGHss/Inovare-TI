package br.dev.ctrls.inovareti.modules.asset.application.service;

import io.micrometer.observation.annotation.Observed;

import br.dev.ctrls.inovareti.modules.asset.domain.model.AssetFilterStatus;
import br.dev.ctrls.inovareti.modules.asset.domain.model.AssetSortBy;
import br.dev.ctrls.inovareti.modules.asset.domain.model.Asset;


import java.util.Locale;

import org.springframework.stereotype.Service;

import br.dev.ctrls.inovareti.core.shared.domain.model.exception.BadRequestException;
import br.dev.ctrls.inovareti.modules.asset.application.dto.AssetResponseDTO;
import lombok.RequiredArgsConstructor;

/**
 * ServiÃ§o de consulta para ativos: encapsula o parsing de parÃ¢metros de filtro/ordenaÃ§Ã£o
 * e a montagem do DTO de resposta a partir da entidade.
 *
 * <p>Responsabilidade Ãºnica: transformaÃ§Ã£o e validaÃ§Ã£o de parÃ¢metros de leitura.
 * A resoluÃ§Ã£o de usuÃ¡rios vinculados ao ativo Ã© delegada diretamente para
 * {@link AssetResponseDTO#from(Asset)}, que deriva os dados da coleÃ§Ã£o {@code users}
 * jÃ¡ carregada pela entidade â€” eliminando a necessidade de consultas adicionais ao banco.
 */
@Service
@RequiredArgsConstructor
@Observed
public class AssetQueryService {

    /**
     * Monta o {@link AssetResponseDTO} a partir da entidade.
     * Os dados multiusuÃ¡rio sÃ£o extraÃ­dos diretamente da coleÃ§Ã£o {@code asset.users}.
     */
    public AssetResponseDTO toResponseDTO(Asset asset) {
        return AssetResponseDTO.from(asset);
    }

    /**
     * Converte a string de status de filtro para o enum tipado, lanÃ§ando {@link BadRequestException}
     * se o valor for invÃ¡lido.
     */
    public AssetFilterStatus parseFilterStatus(String status) {
        try {
            return AssetFilterStatus.valueOf(status.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Status invÃ¡lido. Valores permitidos: ALL, IN_USE, IN_STOCK.");
        }
    }

    /**
     * Converte a string de ordenaÃ§Ã£o para o enum tipado, lanÃ§ando {@link BadRequestException}
     * se o valor for invÃ¡lido.
     */
    public AssetSortBy parseSortBy(String sortBy) {
        return switch (sortBy) {
            case "createdAt"        -> AssetSortBy.CREATED_AT;
            case "maintenanceCount" -> AssetSortBy.MAINTENANCE_COUNT;
            default -> throw new BadRequestException("sortBy invÃ¡lido. Valores permitidos: createdAt, maintenanceCount.");
        };
    }
}


