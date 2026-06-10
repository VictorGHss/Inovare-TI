package br.dev.ctrls.inovareti.modules.finance.domain.model;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entidade que representa o token OAuth obtido da Conta Azul e suas metadatas.
 *
 * Persistimos `accessToken`, `refreshToken`, timestamps de expiração/refresh e
 * informações de auditoria. Usada pelo serviço de tokens para validação e
 * operações de refresh automatizado.
 */
@Entity
@Table(name = "contaazul_oauth_tokens")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContaAzulOAuthToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @jakarta.persistence.Convert(converter = br.dev.ctrls.inovareti.domain.security.CryptoConverter.class)
    @Column(name = "access_token", nullable = false, columnDefinition = "text")
    private String accessToken;

    @jakarta.persistence.Convert(converter = br.dev.ctrls.inovareti.domain.security.CryptoConverter.class)
    @Column(name = "refresh_token", nullable = false, columnDefinition = "text")
    private String refreshToken;

    @Column(name = "token_type", nullable = false, length = 20)
    private String tokenType;

    @Column(name = "scope", length = 255)
    private String scope;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "refreshed_at")
    private LocalDateTime refreshedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

