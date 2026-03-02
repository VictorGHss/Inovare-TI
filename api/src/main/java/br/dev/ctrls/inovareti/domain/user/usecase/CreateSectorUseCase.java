package br.dev.ctrls.inovareti.domain.user.usecase;

import br.dev.ctrls.inovareti.core.exception.ConflictException;
import br.dev.ctrls.inovareti.domain.user.Sector;
import br.dev.ctrls.inovareti.domain.user.SectorRepository;
import br.dev.ctrls.inovareti.domain.user.dto.SectorRequestDTO;
import br.dev.ctrls.inovareti.domain.user.dto.SectorResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso: cria um novo setor.
 * Garante unicidade do nome antes de persistir.
 */
@Component
@RequiredArgsConstructor
public class CreateSectorUseCase {

    private final SectorRepository sectorRepository;

    /**
     * Executa a criação do setor.
     *
     * @param request DTO com o nome do setor
     * @return DTO com os dados do setor criado
     * @throws ConflictException se já existir um setor com o mesmo nome
     */
    @Transactional
    public SectorResponseDTO execute(SectorRequestDTO request) {
        if (sectorRepository.existsByName(request.name())) {
            throw new ConflictException(
                    "Já existe um setor com o nome: " + request.name()
            );
        }

        Sector sector = Sector.builder()
                .name(request.name())
                .build();

        return SectorResponseDTO.from(sectorRepository.save(sector));
    }
}
