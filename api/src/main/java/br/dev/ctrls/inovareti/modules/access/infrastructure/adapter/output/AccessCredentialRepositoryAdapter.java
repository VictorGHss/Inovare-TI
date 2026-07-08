package br.dev.ctrls.inovareti.modules.access.infrastructure.adapter.output;

import br.dev.ctrls.inovareti.modules.access.domain.model.AccessCredential;
import br.dev.ctrls.inovareti.modules.access.domain.port.output.AccessCredentialRepositoryPort;
import br.dev.ctrls.inovareti.modules.access.infrastructure.adapter.output.jpa.repository.AccessCredentialJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador de infraestrutura de persistência para AccessCredential.
 * Traduzido para o inglês seguindo as Regras de Nomenclatura Cruciais.
 * Comentários mantidos em PT-BR.
 *
 * <p>Resiliência de Idempotência: O método {@link #save} captura
 * {@link DataIntegrityViolationException} silenciosamente, garantindo que
 * webhooks disparados em duplo clique não estourem Erro 500 para o Blip.
 * Em vez disso, registra um WARN informativo e retorna o objeto de entrada.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccessCredentialRepositoryAdapter implements AccessCredentialRepositoryPort {

    private final AccessCredentialJpaRepository repository;

    /**
     * Persiste a credencial de acesso no banco de dados.
     *
     * <p>Proteção Contra Duplo Clique: caso um webhook paralelo idêntico já tenha
     * inserido o mesmo par (appointment_id, user_type) anteriormente, a constraint
     * de unicidade do banco lançará {@link DataIntegrityViolationException}.
     * Esse erro é capturado de forma amigável — nenhum Erro 500 é propagado —
     * e o fluxo prossegue normalmente.</p>
     *
     * @param entity Credencial a ser persistida.
     * @return A entidade salva ou o objeto original em caso de inserção duplicada.
     */
    @Override
    public AccessCredential save(AccessCredential entity) {
        try {
            return repository.save(entity);
        } catch (DataIntegrityViolationException ex) {
            // Captura idempotente: registro já existe (constraint de unicidade violada).
            // O duplo clique do paciente no WhatsApp disparou um webhook paralelo que
            // chegou primeiro. Engolimos o erro de forma limpa e registramos o aviso.
            log.warn("[AccessCredential] Inserção duplicada ignorada por idempotência: appointmentId={}, name={}, causa={}",
                    entity.getAppointmentId(), entity.getName(), ex.getMostSpecificCause().getMessage());
            return entity;
        }
    }

    @Override
    public Optional<AccessCredential> findById(UUID id) {
        return repository.findById(id);
    }

    @Override
    public List<AccessCredential> findAll() {
        return repository.findAll();
    }

    @Override
    public List<AccessCredential> findByAppointmentId(String appointmentId) {
        return repository.findByAppointmentId(appointmentId);
    }

    @Override
    public List<AccessCredential> findByCpf(String cpf) {
        return repository.findByCpf(cpf);
    }

    @Override
    public void deleteById(UUID id) {
        repository.deleteById(id);
    }

    @Override
    public boolean existsById(UUID id) {
        return repository.existsById(id);
    }
}
