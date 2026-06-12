package br.dev.ctrls.inovareti.modules.user.infrastructure.adapter.output;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import br.dev.ctrls.inovareti.modules.user.domain.model.User;
import br.dev.ctrls.inovareti.modules.user.domain.model.UserRole;
import br.dev.ctrls.inovareti.modules.user.domain.model.Sector;
import br.dev.ctrls.inovareti.modules.user.domain.port.output.UserRepositoryPort;
import br.dev.ctrls.inovareti.modules.user.infrastructure.adapter.output.jpa.repository.SpringDataUserRepository;
import lombok.RequiredArgsConstructor;

/**
 * Adaptador de infraestrutura que implementa a porta de persistência de Usuários
 * encapsulando as chamadas ao Spring Data JPA.
 */
@Component
@RequiredArgsConstructor
public class UserRepositoryAdapter implements UserRepositoryPort {

    private final SpringDataUserRepository repository;

    @Override
    public List<User> findAll() {
        return repository.findAll();
    }

    @Override
    public List<User> findAllById(List<UUID> ids) {
        return repository.findAllById(ids);
    }

    @Override
    public Optional<User> findById(UUID id) {
        return repository.findById(id);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return repository.findByEmail(email);
    }

    @Override
    public boolean existsByEmail(String email) {
        return repository.existsByEmail(email);
    }

    @Override
    public boolean existsByContaAzulId(String contaAzulId) {
        return repository.existsByContaAzulId(contaAzulId);
    }

    @Override
    public List<User> findAllBySector(Sector sector) {
        return repository.findAllBySector(sector);
    }

    @Override
    public List<User> findAllByRole(UserRole role) {
        return repository.findAllByRole(role);
    }

    @Override
    public List<User> findAllByRoleInAndReceivesItNotificationsTrue(List<UserRole> roles) {
        return repository.findAllByRoleInAndReceivesItNotificationsTrue(roles);
    }

    @Override
    public List<User> findAllWithSector() {
        return repository.findAllWithSector();
    }

    @Override
    public List<User> findAllWithSector(String search) {
        if (search != null && !search.isBlank()) {
            return repository.findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                search, search, org.springframework.data.domain.PageRequest.of(0, 15)
            );
        }
        return repository.findAllWithSector();
    }

    @Override
    public Optional<User> findByDiscordUserId(String discordUserId) {
        return repository.findByDiscordUserId(discordUserId);
    }

    @Override
    public User save(User user) {
        return repository.save(user);
    }

    @Override
    public void deleteById(UUID id) {
        repository.deleteById(id);
    }
}
