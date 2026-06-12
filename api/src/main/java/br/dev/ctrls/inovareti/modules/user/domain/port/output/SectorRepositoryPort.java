package br.dev.ctrls.inovareti.modules.user.domain.port.output;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.dev.ctrls.inovareti.modules.user.domain.model.Sector;

/**
 * Porta de saída pura Java definindo o contrato de persistência para Setores.
 */
public interface SectorRepositoryPort {
    List<Sector> findAll();
    List<Sector> findAll(String search);
    Optional<Sector> findById(UUID id);
    Optional<Sector> findByName(String name);
    boolean existsByName(String name);
    List<Sector> findByActiveTrue();
    List<Sector> findByActiveTrue(String search);
    Sector save(Sector sector);
    void deleteById(UUID id);
}
