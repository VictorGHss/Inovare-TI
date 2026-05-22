package br.dev.ctrls.inovareti.modules.appointment.domain.port.output;

import java.util.List;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.NotificationGroup;

public interface NotificationGroupRepositoryPort {
    NotificationGroup save(NotificationGroup notificationGroup);
    List<NotificationGroup> saveAll(List<NotificationGroup> groups);
}
