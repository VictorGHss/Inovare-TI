package br.dev.ctrls.inovareti.domain.audit.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import br.dev.ctrls.inovareti.domain.audit.AuditAction;
import br.dev.ctrls.inovareti.domain.audit.AuditLog;

public record AuditLogResponseDTO(
        UUID id,
        UUID userId,
        String userName,
        AuditAction action,
        String resourceType,
        UUID resourceId,
        String details,
        String ipAddress,
        LocalDateTime createdAt
) {
    /**
     * Converte a entidade para DTO.
     * userName é resolvido pelo serviço antes de chamar este método.
     */
    public static AuditLogResponseDTO from(AuditLog log, String userName) {
        return new AuditLogResponseDTO(
                log.getId(),
                log.getUserId(),
                userName,
                log.getAction(),
                log.getResourceType(),
                log.getResourceId(),
                log.getDetails(),
                log.getIpAddress(),
                log.getCreatedAt()
        );
    }
}
