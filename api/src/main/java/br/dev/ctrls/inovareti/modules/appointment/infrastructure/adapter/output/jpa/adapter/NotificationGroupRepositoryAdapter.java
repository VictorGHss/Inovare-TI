package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.adapter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
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

    @Override
    public List<NotificationGroup> findByGroupId(UUID groupId) {
        return springDataRepository.findByGroupId(groupId).stream()
                .map(NotificationGroupEntity::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<NotificationGroup> findByGroupIdAndPhoneNumber(UUID groupId, String phone) {
        return springDataRepository.findByGroupIdAndPhoneNumber(groupId, phone).stream()
                .map(NotificationGroupEntity::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<NotificationGroup> findBySessionId(UUID sessionId) {
        return springDataRepository.findBySessionId(sessionId).stream()
                .map(NotificationGroupEntity::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<NotificationGroup> findLatestByPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return Optional.empty();
        }
        return springDataRepository.findByPhoneOrderByCreatedAtDesc(phone, PageRequest.of(0, 1)).stream()
                .findFirst()
                .map(NotificationGroupEntity::toDomain);
    }

    @Override
    public long deleteByCreatedAtBefore(LocalDateTime threshold) {
        return springDataRepository.deleteByCreatedAtBefore(threshold);
    }
}


