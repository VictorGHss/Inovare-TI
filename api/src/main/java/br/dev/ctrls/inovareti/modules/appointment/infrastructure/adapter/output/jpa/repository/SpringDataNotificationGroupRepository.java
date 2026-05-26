package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.entity.NotificationGroupEntity;

public interface SpringDataNotificationGroupRepository extends JpaRepository<NotificationGroupEntity, UUID> {
    List<NotificationGroupEntity> findByGroupId(UUID groupId);
    List<NotificationGroupEntity> findBySessionId(UUID sessionId);
    long deleteByCreatedAtBefore(LocalDateTime threshold);
}


