package br.dev.ctrls.inovareti.domain.asset;

import java.util.Locale;

import org.springframework.stereotype.Service;

import br.dev.ctrls.inovareti.core.exception.BadRequestException;
import br.dev.ctrls.inovareti.domain.asset.dto.AssetResponseDTO;
import br.dev.ctrls.inovareti.domain.user.User;
import br.dev.ctrls.inovareti.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;

/**
 * Serviço de consulta para ativos: encapsula o parsing de parâmetros de filtro/ordenação
 * e a montagem do DTO de resposta a partir da entidade.
 *
 * Responsabilidade única: transformação e validação de parâmetros de leitura.
 */
@Service
@RequiredArgsConstructor
public class AssetQueryService {

    private final UserRepository userRepository;

    /**
     * Monta o {@link AssetResponseDTO} a partir da entidade, resolvendo o usuário vinculado.
     */
    public AssetResponseDTO toResponseDTO(Asset asset) {
        User assignedUser = asset.getUserId() != null
                ? userRepository.findById(asset.getUserId()).orElse(null)
                : null;
        return AssetResponseDTO.from(asset, assignedUser);
    }

    /**
     * Converte a string de status de filtro para o enum tipado, lançando {@link BadRequestException}
     * se o valor for inválido.
     */
    public AssetFilterStatus parseFilterStatus(String status) {
        try {
            return AssetFilterStatus.valueOf(status.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid status. Allowed values: ALL, IN_USE, IN_STOCK.");
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
            default -> throw new BadRequestException("Invalid sortBy. Allowed values: createdAt, maintenanceCount.");
        };
    }
}
