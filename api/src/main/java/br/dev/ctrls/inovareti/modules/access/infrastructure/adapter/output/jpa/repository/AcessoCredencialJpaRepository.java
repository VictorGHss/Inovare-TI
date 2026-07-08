package br.dev.ctrls.inovareti.modules.access.infrastructure.adapter.output.jpa.repository;

import br.dev.ctrls.inovareti.modules.access.domain.model.AcessoCredencial;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

/**
 * Repositório Spring Data JPA associado à entidade {@link AcessoCredencial}.
 */
public interface AcessoCredencialJpaRepository extends JpaRepository<AcessoCredencial, UUID> {

    /**
     * Busca credenciais de acesso pelo identificador do agendamento.
     *
     * @param idAgendamento Identificador do agendamento.
     * @return Lista de credenciais correspondentes.
     */
    List<AcessoCredencial> findByIdAgendamento(String idAgendamento);
}
