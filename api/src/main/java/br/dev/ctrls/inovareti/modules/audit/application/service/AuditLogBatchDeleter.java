package br.dev.ctrls.inovareti.modules.audit.application.service;

import java.time.LocalDateTime;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import br.dev.ctrls.inovareti.modules.audit.infrastructure.adapter.output.jpa.repository.AuditLogJpaRepository;
import lombok.RequiredArgsConstructor;

/**
 * Auxiliar transacional para remoção de logs de auditoria por lote.
 */
@Component
@RequiredArgsConstructor
public class AuditLogBatchDeleter {

    private final AuditLogJpaRepository repository;

    /**
     * Remove um lote de registros em uma transação isolada e rápida.
     */
    @Transactional
    public int deleteBatch(LocalDateTime cutoffDate) {
        return repository.deleteOldLogsBatch(cutoffDate);
    }
}
