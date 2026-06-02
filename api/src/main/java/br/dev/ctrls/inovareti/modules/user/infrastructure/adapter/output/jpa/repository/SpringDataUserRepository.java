package br.dev.ctrls.inovareti.modules.user.infrastructure.adapter.output.jpa.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import br.dev.ctrls.inovareti.modules.user.domain.model.User;
import br.dev.ctrls.inovareti.modules.user.domain.model.UserRole;
import br.dev.ctrls.inovareti.modules.user.domain.model.Sector;

/**
 * Interface física do Spring Data JPA para a entidade User.
 */
@Repository
public interface SpringDataUserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByContaAzulId(String contaAzulId);

    List<User> findAllBySector(Sector sector);

    List<User> findAllByRole(UserRole role);

    @EntityGraph(attributePaths = "sector")
    List<User> findAllByRoleInAndReceivesItNotificationsTrue(List<UserRole> roles);

    @Query("SELECT u FROM User u JOIN FETCH u.sector")
    List<User> findAllWithSector();

    Optional<User> findByDiscordUserId(String discordUserId);
}
