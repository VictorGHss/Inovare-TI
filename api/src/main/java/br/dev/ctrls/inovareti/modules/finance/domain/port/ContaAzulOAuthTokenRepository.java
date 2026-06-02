package br.dev.ctrls.inovareti.modules.finance.domain.port;

import br.dev.ctrls.inovareti.modules.finance.domain.model.ContaAzulOAuthToken;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositório JPA para persistência de `ContaAzulOAuthToken`.
 *
 * Fornece um método utilitário para recuperar o token mais recentemente
 * atualizado, usado pelos serviços para validação e refresh pró-ativo.
 */
public interface ContaAzulOAuthTokenRepository extends JpaRepository<ContaAzulOAuthToken, UUID> {

    /**
     * Recupera o token mais recentemente atualizado (último salvo).
     *
     * @return Optional contendo o token mais recente quando presente
     */
    Optional<ContaAzulOAuthToken> findTopByOrderByUpdatedAtDesc();
}

