package br.dev.ctrls.inovareti.modules.user.infrastructure.adapter.output.jpa.repository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import br.dev.ctrls.inovareti.modules.user.domain.model.Sector;

/**
 * Interface física do Spring Data JPA para a entidade Sector.
 */
public interface SpringDataSectorRepository extends JpaRepository<Sector, UUID> {

    Optional<Sector> findByName(String name);

    boolean existsByName(String name);

    List<Sector> findByActiveTrue();

    List<Sector> findByNameContainingIgnoreCase(String name, org.springframework.data.domain.Pageable pageable);

    List<Sector> findByActiveTrueAndNameContainingIgnoreCase(String name, org.springframework.data.domain.Pageable pageable);
}
