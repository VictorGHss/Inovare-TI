package br.dev.ctrls.inovareti.domain.user.usecase;

import br.dev.ctrls.inovareti.core.exception.ConflictException;
import br.dev.ctrls.inovareti.domain.audit.AuditAction;
import br.dev.ctrls.inovareti.domain.audit.AuditEvent;
import br.dev.ctrls.inovareti.domain.audit.AuditLogService;
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
    private final AuditLogService auditLogService;

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

        Sector savedSector = sectorRepository.save(sector);
        auditLogService.publish(AuditEvent.of(AuditAction.SECTOR_CREATE)
            .resourceType("Sector")
            .resourceId(savedSector.getId())
            .details("{\"name\": \"" + savedSector.getName() + "\"}")
            .build());

        return SectorResponseDTO.from(savedSector);
    }
}
