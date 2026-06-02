package br.dev.ctrls.inovareti.modules.user.application.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.core.exception.ConflictException;
import br.dev.ctrls.inovareti.domain.audit.AuditAction;
import br.dev.ctrls.inovareti.domain.audit.AuditEvent;
import br.dev.ctrls.inovareti.domain.audit.AuditLogService;
import br.dev.ctrls.inovareti.modules.user.domain.model.Sector;
import br.dev.ctrls.inovareti.modules.user.domain.port.output.SectorRepositoryPort;
import br.dev.ctrls.inovareti.modules.user.application.dto.SectorRequestDTO;
import br.dev.ctrls.inovareti.modules.user.application.dto.SectorResponseDTO;
import lombok.RequiredArgsConstructor;

/**
 * Caso de uso: cria um novo setor.
 * Garante unicidade do nome antes de persistir.
 */
@Component
@RequiredArgsConstructor
public class CreateSectorUseCase {

    private final SectorRepositoryPort sectorRepository;
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
                .active(true)
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
