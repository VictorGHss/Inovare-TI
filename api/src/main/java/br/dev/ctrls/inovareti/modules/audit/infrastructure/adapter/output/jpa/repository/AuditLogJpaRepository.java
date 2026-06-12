package br.dev.ctrls.inovareti.modules.audit.infrastructure.adapter.output.jpa.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import br.dev.ctrls.inovareti.modules.audit.domain.model.AuditLog;

public interface AuditLogJpaRepository extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {
}
