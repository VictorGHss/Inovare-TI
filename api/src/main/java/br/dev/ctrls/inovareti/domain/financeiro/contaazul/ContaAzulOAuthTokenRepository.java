package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ContaAzulOAuthTokenRepository extends JpaRepository<ContaAzulOAuthToken, UUID> {

    Optional<ContaAzulOAuthToken> findTopByOrderByUpdatedAtDesc();
}
