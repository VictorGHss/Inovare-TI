package br.dev.ctrls.inovareti.modules.access.domain.port.output;

import br.dev.ctrls.inovareti.modules.access.domain.model.AccessCredential;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Interface de porta de saída para operações de persistência do AccessCredential.
 * Traduzida para o inglês seguindo as Regras de Nomenclatura Cruciais.
 * Comentários mantidos em PT-BR.
 */
public interface AccessCredentialRepositoryPort {

    /**
     * Salva ou atualiza uma credencial de acesso.
     *
     * @param entity Entidade a ser persistida.
     * @return Entidade persistida.
     */
    AccessCredential save(AccessCredential entity);

    /**
     * Busca uma credencial de acesso pelo ID.
     *
     * @param id Identificador da credencial.
     * @return Opcional contendo a entidade se encontrada.
     */
    Optional<AccessCredential> findById(UUID id);

    /**
     * Busca todas as credenciais de acesso salvas no sistema.
     *
     * @return Lista com todas as credenciais de acesso.
     */
    List<AccessCredential> findAll();

    /**
     * Busca credenciais de acesso associadas a um ID de agendamento específico.
     *
     * @param appointmentId Identificador do agendamento.
     * @return Lista contendo as credenciais de acesso correspondentes.
     */
    List<AccessCredential> findByAppointmentId(String appointmentId);

    /**
     * Busca credenciais de acesso associadas a um CPF específico.
     *
     * @param cpf CPF do paciente.
     * @return Lista contendo as credenciais de acesso correspondentes.
     */
    List<AccessCredential> findByCpf(String cpf);

    /**
     * Remove uma credencial de acesso pelo ID.
     *
     * @param id Identificador da credencial.
     */
    void deleteById(UUID id);

    /**
     * Verifica se uma credencial existe com o respectivo ID.
     *
     * @param id Identificador da credencial.
     * @return true se existir, false caso contrário.
     */
    boolean existsById(UUID id);
}
