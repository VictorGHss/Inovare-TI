package br.dev.ctrls.inovareti.domain.user;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Repositório de acesso a dados para a entidade {@link User}.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    List<User> findAllBySector(Sector sector);

    List<User> findAllByRole(UserRole role);

    /**
     * Busca todos os usuários com o setor carregado via JOIN FETCH,
     * evitando o problema N+1 ao serializar o UserResponseDTO.
     */
    @Query("SELECT u FROM User u JOIN FETCH u.sector")
    List<User> findAllWithSector();
}
