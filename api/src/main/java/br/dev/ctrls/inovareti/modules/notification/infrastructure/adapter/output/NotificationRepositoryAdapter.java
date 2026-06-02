package br.dev.ctrls.inovareti.modules.notification.infrastructure.adapter.output;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import br.dev.ctrls.inovareti.modules.notification.domain.model.Notification;
import br.dev.ctrls.inovareti.modules.notification.domain.port.output.NotificationRepositoryPort;
import br.dev.ctrls.inovareti.modules.notification.infrastructure.adapter.output.jpa.repository.NotificationJpaRepository;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class NotificationRepositoryAdapter implements NotificationRepositoryPort {

    private final NotificationJpaRepository repository;

    @Override
    public Notification save(Notification notification) {
        return repository.save(notification);
    }

    @Override
    public Optional<Notification> findById(UUID id) {
        return repository.findById(id);
    }

    @Override
    public List<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Override
    public List<Notification> findByUserIdAndIsReadFalseOrderByCreatedAtDesc(UUID userId) {
        return repository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId);
    }
}
