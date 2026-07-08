package br.dev.ctrls.inovareti.modules.access.infrastructure.adapter.output;

import br.dev.ctrls.inovareti.modules.access.domain.model.AcessoCredencial;
import br.dev.ctrls.inovareti.modules.access.domain.port.output.AcessoCredencialRepositoryPort;
import br.dev.ctrls.inovareti.modules.access.infrastructure.adapter.output.jpa.repository.AcessoCredencialJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador de infraestrutura de persistência para AcessoCredencial.
 */
@Component
@RequiredArgsConstructor
public class AcessoCredencialRepositoryAdapter implements AcessoCredencialRepositoryPort {

    private final AcessoCredencialJpaRepository repository;

    @Override
    public AcessoCredencial save(AcessoCredencial entity) {
        return repository.save(entity);
    }

    @Override
    public Optional<AcessoCredencial> findById(UUID id) {
        return repository.findById(id);
    }

    @Override
    public List<AcessoCredencial> findAll() {
        return repository.findAll();
    }

    @Override
    public List<AcessoCredencial> findByIdAgendamento(String idAgendamento) {
        return repository.findByIdAgendamento(idAgendamento);
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
