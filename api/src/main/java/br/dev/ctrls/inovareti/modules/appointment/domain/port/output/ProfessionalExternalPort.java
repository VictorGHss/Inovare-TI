package br.dev.ctrls.inovareti.modules.appointment.domain.port.output;

import java.util.List;

/**
 * Porta de Saída do Domínio: ProfessionalExternalPort.
 * Interface Java pura que define os métodos focados em obter informações de profissionais médicos a partir de sistemas externos.
 */
public interface ProfessionalExternalPort {

    /**
     * Busca o nome de um profissional médico na Feegow a partir de seu identificador.
     *
     * @param professionalId ID do profissional
     * @return nome do profissional
     */
    String getProfessionalName(String professionalId);

    /**
     * Lista todos os profissionais médicos ativos cadastrados na Feegow.
     *
     * @return lista de profissionais
     */
    List<FeegowProfessional> listProfessionals();
}
