package br.dev.ctrls.inovareti.modules.user.application.service;

import io.micrometer.observation.annotation.Observed;

import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.core.shared.domain.model.exception.ConflictException;
import br.dev.ctrls.inovareti.core.shared.domain.model.exception.NotFoundException;
import br.dev.ctrls.inovareti.domain.audit.AuditAction;
import br.dev.ctrls.inovareti.domain.audit.AuditEvent;
import br.dev.ctrls.inovareti.domain.audit.AuditLogService;
import br.dev.ctrls.inovareti.modules.user.domain.model.Sector;
import br.dev.ctrls.inovareti.modules.user.domain.port.output.SectorRepositoryPort;
import br.dev.ctrls.inovareti.modules.user.application.dto.SectorRequestDTO;
import br.dev.ctrls.inovareti.modules.user.application.dto.SectorResponseDTO;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@Observed
public class UpdateSectorUseCase {

    private final SectorRepositoryPort sectorRepository;
    private final AuditLogService auditLogService;

    @Transactional
    public SectorResponseDTO execute(UUID id, SectorRequestDTO request) {
        Sector sector = sectorRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Setor nÃ£o encontrado com o ID: " + id));

        String newName = request.name().trim();
        if (!sector.getName().equalsIgnoreCase(newName)) {
            sectorRepository.findByName(newName).ifPresent(other -> {
                if (!other.getId().equals(id)) {
                    throw new ConflictException("JÃ¡ existe um setor com o nome: " + newName);
                }
            });
        }

        String oldName = sector.getName();
        sector.setName(newName);
        Sector saved = sectorRepository.save(sector);

        auditLogService.publish(AuditEvent.of(AuditAction.SECTOR_UPDATE)
                .resourceType("Sector")
                .resourceId(saved.getId())
                .details("{\"oldName\": \"" + oldName + "\", \"newName\": \"" + saved.getName() + "\"}")
                .build());

        return SectorResponseDTO.from(saved);
    }
}


