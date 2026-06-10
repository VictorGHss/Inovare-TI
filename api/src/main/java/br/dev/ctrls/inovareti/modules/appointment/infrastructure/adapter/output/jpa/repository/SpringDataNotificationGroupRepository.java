package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.entity.NotificationGroupEntity;

public interface SpringDataNotificationGroupRepository extends JpaRepository<NotificationGroupEntity, UUID> {
    List<NotificationGroupEntity> findByGroupId(UUID groupId);

    @Query("SELECT ng FROM NotificationGroupEntity ng " +
        "JOIN AppointmentSessionEntity s ON s.id = ng.sessionId " +
        "WHERE ng.groupId = :groupId AND s.phoneNumber = :phone")
    List<NotificationGroupEntity> findByGroupIdAndPhoneNumber(
        @Param("groupId") UUID groupId,
        @Param("phone") String phone
    );
    List<NotificationGroupEntity> findBySessionId(UUID sessionId);

    @Query("SELECT ng FROM NotificationGroupEntity ng " +
        "JOIN AppointmentSessionEntity s ON s.id = ng.sessionId " +
        "WHERE s.phoneNumber = :phone " +
        "ORDER BY ng.createdAt DESC")
    List<NotificationGroupEntity> findByPhoneOrderByCreatedAtDesc(
        @Param("phone") String phone,
        Pageable pageable);
    long deleteByCreatedAtBefore(LocalDateTime threshold);
}


