package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.adapter;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import br.dev.ctrls.inovareti.modules.appointment.domain.model.NotificationGroup;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.NotificationGroupRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.entity.NotificationGroupEntity;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.repository.SpringDataNotificationGroupRepository;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class NotificationGroupRepositoryAdapter implements NotificationGroupRepositoryPort {

    private final SpringDataNotificationGroupRepository springDataRepository;

    @Override
    public NotificationGroup save(NotificationGroup notificationGroup) {
        NotificationGroupEntity entity = NotificationGroupEntity.fromDomain(notificationGroup);
        NotificationGroupEntity saved = springDataRepository.save(entity);
        return saved.toDomain();
    }

    @Override
    public List<NotificationGroup> saveAll(List<NotificationGroup> groups) {
        List<NotificationGroupEntity> entities = groups.stream()
                .map(NotificationGroupEntity::fromDomain)
                .collect(Collectors.toList());
        List<NotificationGroupEntity> saved = springDataRepository.saveAll(entities);
        return saved.stream()
                .map(NotificationGroupEntity::toDomain)
                .collect(Collectors.toList());
    }
}
