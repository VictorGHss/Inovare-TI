package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.adapter;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import br.dev.ctrls.inovareti.modules.appointment.domain.model.BlipDeliveryFailure;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.BlipErrorClassifier;
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
        return repository.findById(id).map(entity -> entity.toDomain());
    }

    @Override
    public List<BlipDeliveryFailure> findByMessageId(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return List.of();
        }
        return repository.findByMessageId(messageId.trim()).stream()
                .map(entity -> entity.toDomain())
                .collect(Collectors.toList());
    }

    @Override
    public List<BlipDeliveryFailure> findByAppointmentId(String appointmentId) {
        if (appointmentId == null || appointmentId.isBlank()) {
            return List.of();
        }
        return repository.findByAppointmentId(appointmentId.trim()).stream()
                .map(entity -> entity.toDomain())
                .collect(Collectors.toList());
    }

    @Override
    public org.springframework.data.domain.Page<BlipDeliveryFailure> findAllFiltered(
            String appointmentId,
            String category,
            org.springframework.data.domain.Pageable pageable
    ) {
        String queryApptId = (appointmentId != null && !appointmentId.isBlank()) ? appointmentId.trim() : null;

        java.util.List<Integer> errorCodes = null;
        java.util.List<Integer> notInErrorCodes = null;
        boolean filterByCodes = false;
        boolean filterByNotInCodes = false;

        if (category != null && !category.isBlank()) {
            if ("FALHA_DESCONHECIDA".equalsIgnoreCase(category.trim())) {
                // Coleta todos os códigos conhecidos para exclusão
                notInErrorCodes = java.util.Arrays.asList(1, 2, 51, 81, 86, 1601, 38, 429, 100, 1505, 131026, 1602, 130472, 131031, 131051, 131052, 131053);
                filterByNotInCodes = true;
            } else {
                // Busca os códigos correspondentes à categoria fornecida
                errorCodes = BlipErrorClassifier.getErrorCodesByCategory(category.trim());
                if (errorCodes != null && !errorCodes.isEmpty()) {
                    filterByCodes = true;
                }
            }
        }

        return repository.findByFilters(
                queryApptId,
                filterByCodes,
                errorCodes,
                filterByNotInCodes,
                notInErrorCodes,
                pageable
        ).map(entity -> entity.toDomain());
    }
}
