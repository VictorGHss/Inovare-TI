package br.dev.ctrls.inovareti.domain.audit;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    @Query("""
            SELECT a FROM AuditLog a
            WHERE (:userId    IS NULL OR a.userId = :userId)
              AND (:action    IS NULL OR a.action = :action)
              AND (:startDate IS NULL OR a.createdAt >= :startDate)
              AND (:endDate   IS NULL OR a.createdAt <= :endDate)
            ORDER BY a.createdAt DESC
            """)
    Page<AuditLog> findWithFilters(
            @Param("userId")    UUID userId,
            @Param("action")    AuditAction action,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate")   LocalDateTime endDate,
            Pageable pageable);
}
