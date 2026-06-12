package br.dev.ctrls.inovareti.modules.user.infrastructure.adapter.output;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import br.dev.ctrls.inovareti.modules.user.domain.model.Sector;
import br.dev.ctrls.inovareti.modules.user.domain.port.output.SectorRepositoryPort;
import br.dev.ctrls.inovareti.modules.user.infrastructure.adapter.output.jpa.repository.SpringDataSectorRepository;
import lombok.RequiredArgsConstructor;

/**
 * Adaptador de infraestrutura que implementa a porta de persistência de Setores
 * encapsulando as chamadas ao Spring Data JPA.
 */
@Component
@RequiredArgsConstructor
public class SectorRepositoryAdapter implements SectorRepositoryPort {

    private final SpringDataSectorRepository repository;

    @Override
    public List<Sector> findAll() {
        return repository.findAll();
    }

    @Override
    public List<Sector> findAll(String search) {
        if (search != null && !search.isBlank()) {
            return repository.findByNameContainingIgnoreCase(search, org.springframework.data.domain.PageRequest.of(0, 15));
        }
        return repository.findAll();
    }

    @Override
    public Optional<Sector> findById(UUID id) {
        return repository.findById(id);
    }

    @Override
    public Optional<Sector> findByName(String name) {
        return repository.findByName(name);
    }

    @Override
    public boolean existsByName(String name) {
        return repository.existsByName(name);
    }

    @Override
    public List<Sector> findByActiveTrue() {
        return repository.findByActiveTrue();
    }

    @Override
    public List<Sector> findByActiveTrue(String search) {
        if (search != null && !search.isBlank()) {
            return repository.findByActiveTrueAndNameContainingIgnoreCase(search, org.springframework.data.domain.PageRequest.of(0, 15));
        }
        return repository.findByActiveTrue();
    }

    @Override
    public Sector save(Sector sector) {
        return repository.save(sector);
    }

    @Override
    public void deleteById(UUID id) {
        repository.deleteById(id);
    }
}
