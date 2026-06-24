package br.dev.ctrls.inovareti.modules.audit.infrastructure.adapter.output.jpa.repository;

import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import br.dev.ctrls.inovareti.modules.audit.domain.model.AuditLog;

public interface AuditLogJpaRepository extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {

    @Modifying
    @Query(value = "DELETE FROM audit_logs WHERE id IN (SELECT id FROM audit_logs WHERE created_at < :cutoffDate LIMIT 5000)", nativeQuery = true)
    int deleteOldLogsBatch(@Param("cutoffDate") LocalDateTime cutoffDate);
}
