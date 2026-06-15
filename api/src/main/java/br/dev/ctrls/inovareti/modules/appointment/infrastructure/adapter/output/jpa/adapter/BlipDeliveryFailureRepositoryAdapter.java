package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.adapter;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import br.dev.ctrls.inovareti.modules.appointment.domain.model.BlipDeliveryFailure;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.BlipDeliveryFailureRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.entity.BlipDeliveryFailureEntity;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.repository.SpringDataBlipDeliveryFailureRepository;
import lombok.RequiredArgsConstructor;

/**
 * Adaptador de infraestrutura que implementa a porta de saída do domínio BlipDeliveryFailureRepositoryPort,
 * fazendo a integração com o banco de dados via Spring Data JPA.
 */
@Component
@RequiredArgsConstructor
public class BlipDeliveryFailureRepositoryAdapter implements BlipDeliveryFailureRepositoryPort {

    private final SpringDataBlipDeliveryFailureRepository repository;

    @Override
    public BlipDeliveryFailure save(BlipDeliveryFailure failure) {
        if (failure == null) {
            return null;
        }
        BlipDeliveryFailureEntity entity = BlipDeliveryFailureEntity.fromDomain(failure);
        BlipDeliveryFailureEntity saved = repository.save(entity);
        return saved.toDomain();
    }

    @Override
    public Optional<BlipDeliveryFailure> findById(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return repository.findById(id).map(BlipDeliveryFailureEntity::toDomain);
    }

    @Override
    public List<BlipDeliveryFailure> findByMessageId(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return List.of();
        }
        return repository.findByMessageId(messageId.trim()).stream()
                .map(BlipDeliveryFailureEntity::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<BlipDeliveryFailure> findByAppointmentId(String appointmentId) {
        if (appointmentId == null || appointmentId.isBlank()) {
            return List.of();
        }
        return repository.findByAppointmentId(appointmentId.trim()).stream()
                .map(BlipDeliveryFailureEntity::toDomain)
                .collect(Collectors.toList());
    }
}
