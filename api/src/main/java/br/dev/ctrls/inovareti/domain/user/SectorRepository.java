package br.dev.ctrls.inovareti.domain.user;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositório de acesso a dados para a entidade {@link Sector}.
 */
@Repository
public interface SectorRepository extends JpaRepository<Sector, UUID> {

    Optional<Sector> findByName(String name);

    boolean existsByName(String name);
}
