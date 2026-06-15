package br.dev.ctrls.inovareti.modules.asset.application.service;

import io.micrometer.observation.annotation.Observed;

import br.dev.ctrls.inovareti.modules.asset.domain.model.AssetFilterStatus;
import br.dev.ctrls.inovareti.modules.asset.domain.model.AssetSortBy;
import br.dev.ctrls.inovareti.modules.asset.domain.model.Asset;


import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;

import br.dev.ctrls.inovareti.core.shared.domain.model.exception.BadRequestException;
import br.dev.ctrls.inovareti.modules.asset.application.dto.AssetResponseDTO;
import lombok.RequiredArgsConstructor;

/**
 * Serviço de consulta para ativos: encapsula o parsing de parâmetros de filtro/ordenação
 * e a montagem do DTO de resposta a partir da entidade.
 *
 * <p>Responsabilidade única: transformação e validação de parâmetros de leitura.
 * A resolução de usuários vinculados ao ativo é delegada diretamente para
 * {@link AssetResponseDTO#from(Asset)}, que deriva os dados da coleção {@code users}
 * já carregada pela entidade â€” eliminando a necessidade de consultas adicionais ao banco.
 */
@Service
@RequiredArgsConstructor
@Observed
public class AssetQueryService {

    /**
     * Monta o {@link AssetResponseDTO} a partir da entidade.
     * Os dados multiusuário são extraídos diretamente da coleção {@code asset.users}.
     */
    public AssetResponseDTO toResponseDTO(Asset asset) {
        return AssetResponseDTO.from(asset);
    }

    /**
     * Converte a string de status de filtro para o enum tipado, lançando {@link BadRequestException}
     * se o valor for inválido.
     */
    public AssetFilterStatus parseFilterStatus(String status) {
        try {
            return AssetFilterStatus.valueOf(status.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Status inválido. Valores permitidos: ALL, IN_USE, IN_STOCK.");
        }
    }

    /**
     * Converte a string de ordenação para o enum tipado, lançando {@link BadRequestException}
     * se o valor for inválido.
     */
    public AssetSortBy parseSortBy(String sortBy) {
        return switch (sortBy) {
            case "createdAt"        -> AssetSortBy.CREATED_AT;
            case "maintenanceCount" -> AssetSortBy.MAINTENANCE_COUNT;
            default -> throw new BadRequestException("sortBy inválido. Valores permitidos: createdAt, maintenanceCount.");
        };
    }

    /**
     * Retorna uma página de ativos do ecrã de listagem com base nos filtros de categoria, status e ordenação.
     * 
     * @param categoryId ID da categoria de ativos
     * @param status Status do ativo imobilizado (ALL, IN_USE, IN_STOCK)
     * @param sortBy Campo de ordenação (createdAt, maintenanceCount)
     * @param pageable Parâmetros de paginação fornecidos pelo utilizador
     * @param assetRepository Repositório de acesso aos ativos
     * @return Página de AssetResponseDTO contendo metadados de paginação
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public org.springframework.data.domain.Page<AssetResponseDTO> listAssets(
            UUID categoryId,
            String status,
            String sortBy,
            String search,
            org.springframework.data.domain.Pageable pageable,
            br.dev.ctrls.inovareti.modules.asset.domain.port.output.AssetRepositoryPort assetRepository) {
        
        AssetFilterStatus parsedStatus = parseFilterStatus(status);
        AssetSortBy parsedSortBy = parseSortBy(sortBy);

        org.springframework.data.domain.Page<Asset> assets = parsedSortBy == AssetSortBy.MAINTENANCE_COUNT
                ? assetRepository.findWithFiltersOrderByMaintenanceCountDesc(categoryId, parsedStatus.name(), search, pageable)
                : assetRepository.findWithFiltersOrderByCreatedAtDesc(categoryId, parsedStatus.name(), search, pageable);

        return assets.map(this::toResponseDTO);
    }
}


