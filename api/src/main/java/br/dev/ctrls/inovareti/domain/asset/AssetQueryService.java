package br.dev.ctrls.inovareti.domain.asset;

import java.util.Locale;

import org.springframework.stereotype.Service;

import br.dev.ctrls.inovareti.core.exception.BadRequestException;
import br.dev.ctrls.inovareti.domain.asset.dto.AssetResponseDTO;
import lombok.RequiredArgsConstructor;

/**
 * Serviço de consulta para ativos: encapsula o parsing de parâmetros de filtro/ordenação
 * e a montagem do DTO de resposta a partir da entidade.
 *
 * <p>Responsabilidade única: transformação e validação de parâmetros de leitura.
 * A resolução de usuários vinculados ao ativo é delegada diretamente para
 * {@link AssetResponseDTO#from(Asset)}, que deriva os dados da coleção {@code users}
 * já carregada pela entidade — eliminando a necessidade de consultas adicionais ao banco.
 */
@Service
@RequiredArgsConstructor
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
}
