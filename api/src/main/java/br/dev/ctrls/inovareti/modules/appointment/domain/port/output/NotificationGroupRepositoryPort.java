package br.dev.ctrls.inovareti.modules.appointment.domain.port.output;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.NotificationGroup;

public interface NotificationGroupRepositoryPort {
    NotificationGroup save(NotificationGroup notificationGroup);
    List<NotificationGroup> saveAll(List<NotificationGroup> groups);
    List<NotificationGroup> findByGroupId(UUID groupId);
    long deleteByCreatedAtBefore(LocalDateTime threshold);
}


