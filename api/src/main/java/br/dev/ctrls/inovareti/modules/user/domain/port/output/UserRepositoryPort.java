package br.dev.ctrls.inovareti.modules.user.domain.port.output;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.dev.ctrls.inovareti.modules.user.domain.model.User;
import br.dev.ctrls.inovareti.modules.user.domain.model.UserRole;
import br.dev.ctrls.inovareti.modules.user.domain.model.Sector;

/**
 * Porta de saída pura Java definindo o contrato de persistência para Usuários.
 */
public interface UserRepositoryPort {
    List<User> findAll();
    List<User> findAllById(List<UUID> ids);
    Optional<User> findById(UUID id);
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByContaAzulId(String contaAzulId);
    List<User> findAllBySector(Sector sector);
    List<User> findAllByRole(UserRole role);
    List<User> findAllByRoleInAndReceivesItNotificationsTrue(List<UserRole> roles);
    List<User> findAllWithSector();
    Optional<User> findByDiscordUserId(String discordUserId);
    User save(User user);
    void deleteById(UUID id);
}
