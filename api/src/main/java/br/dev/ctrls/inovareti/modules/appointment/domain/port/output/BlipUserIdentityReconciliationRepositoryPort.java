package br.dev.ctrls.inovareti.modules.appointment.domain.port.output;

import java.util.List;
import java.util.Optional;

import br.dev.ctrls.inovareti.modules.appointment.domain.model.BlipUserIdentityReconciliation;

/**
 * Porta de Saída do Domínio: BlipUserIdentityReconciliationRepositoryPort.
 * COMENTÁRIO EM PORTUGUÊS (PT-BR):
 * Interface que define os contratos de persistência e consulta no banco de dados
 * para a reconciliação de identidades, isolando as regras de negócio da tecnologia de persistência.
 */
public interface BlipUserIdentityReconciliationRepositoryPort {

    BlipUserIdentityReconciliation save(BlipUserIdentityReconciliation reconciliation);

    Optional<BlipUserIdentityReconciliation> findByBlipGuid(String blipGuid);

    Optional<BlipUserIdentityReconciliation> findByBsuid(String bsuid);

    List<BlipUserIdentityReconciliation> findByPhoneNumber(String phoneNumber);
}
