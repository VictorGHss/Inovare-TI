package br.dev.ctrls.inovareti.modules.appointment.domain.port.output;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.dev.ctrls.inovareti.modules.appointment.domain.model.NotificationGroup;

public interface NotificationGroupRepositoryPort {
    NotificationGroup save(NotificationGroup notificationGroup);
    List<NotificationGroup> saveAll(List<NotificationGroup> groups);
    List<NotificationGroup> findByGroupId(UUID groupId);
    List<NotificationGroup> findBySessionId(UUID sessionId);
    Optional<NotificationGroup> findLatestByPhone(String phone);
    long deleteByCreatedAtBefore(LocalDateTime threshold);
}


