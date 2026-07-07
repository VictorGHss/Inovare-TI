package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.adapter;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import br.dev.ctrls.inovareti.modules.appointment.domain.model.BlipUserIdentityReconciliation;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.BlipUserIdentityReconciliationRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.entity.BlipUserIdentityReconciliationEntity;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.repository.SpringDataBlipUserIdentityReconciliationRepository;
import lombok.RequiredArgsConstructor;

/**
 * Adaptador de Infraestrutura: BlipUserIdentityReconciliationRepositoryAdapter.
 * COMENTÁRIO EM PORTUGUÊS (PT-BR):
 * Implementa a porta de saída do domínio para interagir fisicamente com a tabela
 * do PostgreSQL usando a interface Spring Data JPA, realizando conversões bidirecionais de domínio/entidade.
 */
@Component
@RequiredArgsConstructor
public class BlipUserIdentityReconciliationRepositoryAdapter implements BlipUserIdentityReconciliationRepositoryPort {

    private final SpringDataBlipUserIdentityReconciliationRepository springDataRepository;

    @Override
    public BlipUserIdentityReconciliation save(BlipUserIdentityReconciliation reconciliation) {
        if (reconciliation == null) return null;
        BlipUserIdentityReconciliationEntity entity = BlipUserIdentityReconciliationEntity.fromDomain(reconciliation);
        BlipUserIdentityReconciliationEntity saved = springDataRepository.save(entity);
        return saved.toDomain();
    }

    @Override
    public Optional<BlipUserIdentityReconciliation> findByBlipGuid(String blipGuid) {
        if (blipGuid == null || blipGuid.isBlank()) {
            return Optional.empty();
        }
        return springDataRepository.findByBlipGuid(blipGuid.trim())
                .map(entity -> entity.toDomain());
    }

    @Override
    public Optional<BlipUserIdentityReconciliation> findByBsuid(String bsuid) {
        if (bsuid == null || bsuid.isBlank()) {
            return Optional.empty();
        }
        return springDataRepository.findByBsuid(bsuid.trim())
                .map(entity -> entity.toDomain());
    }

    @Override
    public List<BlipUserIdentityReconciliation> findByPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return List.of();
        }
        return springDataRepository.findByPhoneNumber(phoneNumber.trim()).stream()
                .map(entity -> entity.toDomain())
                .collect(Collectors.toList());
    }
}
