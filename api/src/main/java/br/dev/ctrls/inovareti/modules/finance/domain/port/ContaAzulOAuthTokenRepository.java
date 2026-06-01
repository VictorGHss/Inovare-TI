package br.dev.ctrls.inovareti.modules.finance.domain.port;

import br.dev.ctrls.inovareti.modules.finance.domain.model.ContaAzulOAuthToken;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * RepositÃƒÂ³rio JPA para persistÃƒÂªncia de `ContaAzulOAuthToken`.
 *
 * Fornece um mÃƒÂ©todo utilitÃƒÂ¡rio para recuperar o token mais recentemente
 * atualizado, usado pelos serviÃƒÂ§os para validaÃƒÂ§ÃƒÂ£o e refresh prÃƒÂ³-ativo.
 */
public interface ContaAzulOAuthTokenRepository extends JpaRepository<ContaAzulOAuthToken, UUID> {

    /**
     * Recupera o token mais recentemente atualizado (ÃƒÂºltimo salvo).
     *
     * @return Optional contendo o token mais recente quando presente
     */
    Optional<ContaAzulOAuthToken> findTopByOrderByUpdatedAtDesc();
}

