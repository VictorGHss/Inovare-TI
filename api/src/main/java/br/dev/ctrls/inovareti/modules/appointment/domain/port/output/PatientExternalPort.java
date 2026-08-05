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

    /**
     * Cadastra um novo paciente na Feegow a partir de seu nome, CPF, data de nascimento e telefone.
     *
     * @param name Nome completo do paciente
     * @param cpf CPF (apenas números)
     * @param birthdate Data de nascimento no padrão ISO (YYYY-MM-DD)
     * @param phone Telefone/Celular do paciente
     * @return paciente cadastrado com o ID gerado pelo Feegow
     */
    FeegowPatient createPatient(String name, String cpf, String birthdate, String phone);

    /**
     * Atualiza o CPF de um paciente na Feegow a partir do seu identificador, nome e data de nascimento.
     *
     * @param patientId ID do paciente
     * @param cpf CPF a ser gravado (apenas números)
     * @param name Nome completo do paciente
     * @param birthdate Data de nascimento do paciente
     */
    void updatePatientCpf(String patientId, String cpf, String name, String birthdate);
}
