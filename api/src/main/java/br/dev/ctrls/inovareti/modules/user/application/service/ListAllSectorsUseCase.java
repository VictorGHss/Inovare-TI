package br.dev.ctrls.inovareti.modules.user.application.service;

import io.micrometer.observation.annotation.Observed;

import br.dev.ctrls.inovareti.modules.user.domain.model.Sector;
import br.dev.ctrls.inovareti.modules.user.domain.port.output.SectorRepositoryPort;
import br.dev.ctrls.inovareti.modules.user.application.dto.SectorResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Caso de uso: lista todos os setores cadastrados.
 */
@Component
@RequiredArgsConstructor
@Observed
public class ListAllSectorsUseCase {

    private final SectorRepositoryPort sectorRepository;

    /**
     * Retorna todos os setores ordenados pelo nome.
     *
     * @return lista de DTOs com os dados dos setores
     */
    @Transactional(readOnly = true)
    public List<SectorResponseDTO> execute(Boolean activeOnly) {
        return execute(activeOnly, null);
    }

    @Transactional(readOnly = true)
    public List<SectorResponseDTO> execute(Boolean activeOnly, String search) {
        List<Sector> list;
        if (activeOnly != null && activeOnly) {
            list = sectorRepository.findByActiveTrue(search);
        } else {
            list = sectorRepository.findAll(search);
        }
        return list.stream()
                .map(SectorResponseDTO::from)
                .toList();
    }
}


