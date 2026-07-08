package br.dev.ctrls.inovareti.modules.access.infrastructure.adapter.output.jpa.repository;

import br.dev.ctrls.inovareti.modules.access.domain.model.AccessCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

/**
 * Repositório Spring Data JPA associado à entidade {@link AccessCredential}.
 * Traduzido para o inglês seguindo as Regras de Nomenclatura Cruciais.
 * Comentários mantidos em PT-BR.
 */
public interface AccessCredentialJpaRepository extends JpaRepository<AccessCredential, UUID> {

    /**
     * Busca credenciais de acesso pelo identificador do agendamento.
     *
     * @param appointmentId Identificador do agendamento.
     * @return Lista de credenciais correspondentes.
     */
    List<AccessCredential> findByAppointmentId(String appointmentId);

    /**
     * Busca credenciais de acesso pelo CPF do paciente.
     *
     * @param cpf CPF do paciente.
     * @return Lista de credenciais correspondentes.
     */
    List<AccessCredential> findByCpf(String cpf);
}
