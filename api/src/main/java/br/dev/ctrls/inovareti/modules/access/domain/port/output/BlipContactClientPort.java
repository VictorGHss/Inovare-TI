package br.dev.ctrls.inovareti.modules.access.domain.port.output;

/**
 * Porta de saída para sincronização ativa de contatos com a API do Blip.
 * Comentários em PT-BR conforme as Regras de Ouro.
 */
public interface BlipContactClientPort {

    /**
     * Sincroniza ativamente as informações do contato do paciente na API do Blip (LIME).
     *
     * @param phoneNumber Telefone/identidade do paciente.
     * @param name Nome limpo do paciente.
     * @param cpf CPF limpo do paciente.
     * @param queueName Fila de triagem correta resolvida para o médico/agendamento.
     * @return true se o Blip retornou sucesso no recebimento do contato atualizado.
     */
    boolean syncContact(String phoneNumber, String name, String cpf, String queueName, String doctorId);
}
