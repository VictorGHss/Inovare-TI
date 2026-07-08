package br.dev.ctrls.inovareti.modules.access.domain.port.output;

import br.dev.ctrls.inovareti.modules.access.domain.model.AcessoCredencial;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Interface de porta de saída para operações de persistência do AcessoCredencial.
 */
public interface AcessoCredencialRepositoryPort {

    /**
     * Salva ou atualiza uma credencial de acesso.
     *
     * @param entity Entidade a ser persistida.
     * @return Entidade persistida.
     */
    AcessoCredencial save(AcessoCredencial entity);

    /**
     * Busca uma credencial de acesso pelo ID.
     *
     * @param id Identificador da credencial.
     * @return Opcional contendo a entidade se encontrada.
     */
    Optional<AcessoCredencial> findById(UUID id);

    /**
     * Busca todas as credenciais de acesso salvas no sistema.
     *
     * @return Lista com todas as credenciais de acesso.
     */
    List<AcessoCredencial> findAll();

    /**
     * Busca credenciais de acesso associadas a um ID de agendamento específico.
     *
     * @param idAgendamento Identificador do agendamento.
     * @return Lista contendo as credenciais de acesso correspondentes.
     */
    List<AcessoCredencial> findByIdAgendamento(String idAgendamento);

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
