package br.dev.ctrls.inovareti.modules.audit.infrastructure.adapter.output;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import br.dev.ctrls.inovareti.modules.audit.domain.model.AuditLog;
import br.dev.ctrls.inovareti.modules.audit.domain.port.output.AuditLogRepositoryPort;
import br.dev.ctrls.inovareti.modules.audit.infrastructure.adapter.output.jpa.repository.AuditLogJpaRepository;

/**
 * Adaptador de saída para persistência de logs de auditoria delegando para o repositório JPA.
 */
@Component
@RequiredArgsConstructor
public class AuditLogRepositoryAdapter implements AuditLogRepositoryPort {

    private final AuditLogJpaRepository repository;

    @Override
    public AuditLog save(AuditLog auditLog) {
        return repository.save(auditLog);
    }

    @Override
    public Page<AuditLog> findAll(Specification<AuditLog> spec, Pageable pageable) {
        return repository.findAll(spec, pageable);
    }

    @Override
    public Optional<AuditLog> findById(UUID id) {
        return repository.findById(id);
    }

    @Override
    public int deleteOldLogsBatch(java.time.LocalDateTime cutoffDate) {
        return repository.deleteOldLogsBatch(cutoffDate);
    }
}
