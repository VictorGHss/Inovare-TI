package br.dev.ctrls.inovareti.modules.access.infrastructure.adapter.output;

import br.dev.ctrls.inovareti.modules.access.domain.model.AccessCredential;
import br.dev.ctrls.inovareti.modules.access.domain.port.output.AccessCredentialRepositoryPort;
import br.dev.ctrls.inovareti.modules.access.infrastructure.adapter.output.jpa.repository.AccessCredentialJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador de infraestrutura de persistência para AccessCredential.
 * Traduzido para o inglês seguindo as Regras de Nomenclatura Cruciais.
 * Comentários mantidos em PT-BR.
 */
@Component
@RequiredArgsConstructor
public class AccessCredentialRepositoryAdapter implements AccessCredentialRepositoryPort {

    private final AccessCredentialJpaRepository repository;

    @Override
    public AccessCredential save(AccessCredential entity) {
        return repository.save(entity);
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
