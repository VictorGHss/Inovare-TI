package br.dev.ctrls.inovareti.modules.access.domain.port.output;

import br.dev.ctrls.inovareti.modules.access.domain.model.FeegowPatientAccessInfo;
import java.util.Optional;

/**
 * Porta de saída para buscar os dados de agendamento e prontuário do paciente no Feegow.
 * Comentários em PT-BR.
 */
public interface FeegowClientPort {

    /**
     * Busca as informações detalhadas de acesso e agendamento a partir do id_agendamento.
     *
     * @param appointmentId Identificador do agendamento.
     * @return Opcional com as informações de acesso caso encontradas.
     */
    Optional<FeegowPatientAccessInfo> fetchPatientAccessInfo(String appointmentId);
}
