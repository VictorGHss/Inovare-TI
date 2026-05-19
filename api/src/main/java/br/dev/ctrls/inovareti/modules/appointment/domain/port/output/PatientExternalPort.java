package br.dev.ctrls.inovareti.modules.appointment.domain.port.output;

/**
 * Porta de Saída do Domínio: PatientExternalPort.
 * Interface Java pura que define os métodos focados em obter informações de pacientes a partir de sistemas externos.
 */
public interface PatientExternalPort {

    /**
     * Busca os detalhes cadastrais de um paciente na Feegow a partir de seu identificador.
     *
     * @param patientId ID do paciente
     * @return detalhes do paciente
     */
    FeegowPatient patientInfo(String patientId);
}
