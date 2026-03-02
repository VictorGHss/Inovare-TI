package br.dev.ctrls.inovareti.domain.user.usecase;

import br.dev.ctrls.inovareti.domain.user.SectorRepository;
import br.dev.ctrls.inovareti.domain.user.dto.SectorResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Caso de uso: lista todos os setores cadastrados.
 */
@Component
@RequiredArgsConstructor
public class ListAllSectorsUseCase {

    private final SectorRepository sectorRepository;

    /**
     * Retorna todos os setores ordenados pelo nome.
     *
     * @return lista de DTOs com os dados dos setores
     */
    @Transactional(readOnly = true)
    public List<SectorResponseDTO> execute() {
        return sectorRepository.findAll()
                .stream()
                .map(SectorResponseDTO::from)
                .toList();
    }
}
