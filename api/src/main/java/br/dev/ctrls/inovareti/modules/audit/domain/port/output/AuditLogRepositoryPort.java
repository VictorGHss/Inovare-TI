package br.dev.ctrls.inovareti.modules.audit.domain.port.output;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import br.dev.ctrls.inovareti.modules.audit.domain.model.AuditLog;

/**
 * Porta de saída que define os métodos de persistência e consulta para registros de auditoria (AuditLog).
 */
public interface AuditLogRepositoryPort {
    /**
     * Salva um log de auditoria.
     */
    AuditLog save(AuditLog auditLog);

    /**
     * Busca os logs de auditoria de forma paginada com critérios de filtragem (Specification).
     */
    Page<AuditLog> findAll(Specification<AuditLog> spec, Pageable pageable);

    /**
     * Busca um log de auditoria por seu identificador.
     */
    Optional<AuditLog> findById(UUID id);

    /**
     * Remove um lote de logs de auditoria anteriores à data de corte.
     */
    int deleteOldLogsBatch(java.time.LocalDateTime cutoffDate);
}
