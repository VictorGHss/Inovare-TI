package br.dev.ctrls.inovareti.domain.audit;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

        @Query(value = """
                                                SELECT *
                                                FROM audit_logs a
                                                WHERE (cast(:userId as uuid) IS NULL OR a.user_id = :userId)
                                                        AND (cast(:action as varchar) IS NULL OR a.action = :action)
                                                        AND (cast(:startDate as timestamp) IS NULL OR a.created_at >= cast(:startDate as timestamp))
                                                        AND (cast(:endDate as timestamp) IS NULL OR a.created_at <= cast(:endDate as timestamp))
                                                ORDER BY a.created_at DESC
                                                """,
                                                countQuery = """
                                                SELECT count(*)
                                                FROM audit_logs a
                                                WHERE (cast(:userId as uuid) IS NULL OR a.user_id = :userId)
                                                        AND (cast(:action as varchar) IS NULL OR a.action = :action)
                                                        AND (cast(:startDate as timestamp) IS NULL OR a.created_at >= cast(:startDate as timestamp))
                                                        AND (cast(:endDate as timestamp) IS NULL OR a.created_at <= cast(:endDate as timestamp))
                                                """,
                                                nativeQuery = true)
    Page<AuditLog> findWithFilters(
            @Param("userId")    UUID userId,
                                                @Param("action")    String action,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate")   LocalDateTime endDate,
            Pageable pageable);
}
