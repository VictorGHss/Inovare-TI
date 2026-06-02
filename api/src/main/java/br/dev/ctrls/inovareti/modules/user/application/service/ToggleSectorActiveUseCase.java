package br.dev.ctrls.inovareti.modules.user.application.service;

import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.core.shared.domain.model.exception.NotFoundException;
import br.dev.ctrls.inovareti.domain.audit.AuditAction;
import br.dev.ctrls.inovareti.domain.audit.AuditEvent;
import br.dev.ctrls.inovareti.domain.audit.AuditLogService;
import br.dev.ctrls.inovareti.modules.user.domain.model.Sector;
import br.dev.ctrls.inovareti.modules.user.domain.port.output.SectorRepositoryPort;
import br.dev.ctrls.inovareti.modules.user.application.dto.SectorResponseDTO;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ToggleSectorActiveUseCase {

    private final SectorRepositoryPort sectorRepository;
    private final AuditLogService auditLogService;

    @Transactional
    public SectorResponseDTO execute(UUID id) {
        Sector sector = sectorRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Setor não encontrado com o ID: " + id));

        boolean oldStatus = sector.isActive();
        sector.setActive(!oldStatus);
        Sector saved = sectorRepository.save(sector);

        auditLogService.publish(AuditEvent.of(AuditAction.SECTOR_TOGGLE)
                .resourceType("Sector")
                .resourceId(saved.getId())
                .details("{\"oldStatus\": " + oldStatus + ", \"newStatus\": " + saved.isActive() + "}")
                .build());

        return SectorResponseDTO.from(saved);
    }
}
