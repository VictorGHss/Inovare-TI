package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.entity.BlipUserIdentityReconciliationEntity;

/**
 * Repositório Spring Data JPA para a entidade BlipUserIdentityReconciliationEntity.
 * COMENTÁRIO EM PORTUGUÊS (PT-BR):
 * Provê interfaces prontas para o Spring Data gerar as queries de banco de dados
 * necessárias para consultar e gerenciar as correspondências de identidades.
 */
@Repository
public interface SpringDataBlipUserIdentityReconciliationRepository extends JpaRepository<BlipUserIdentityReconciliationEntity, UUID> {

    Optional<BlipUserIdentityReconciliationEntity> findByBlipGuid(String blipGuid);

    Optional<BlipUserIdentityReconciliationEntity> findByBsuid(String bsuid);

    List<BlipUserIdentityReconciliationEntity> findByPhoneNumber(String phoneNumber);
}
