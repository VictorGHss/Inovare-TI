package br.dev.ctrls.inovareti.domain.user;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import br.dev.ctrls.inovareti.domain.security.CryptoConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Usuário do sistema Inovare TI.
 * Cada usuário pertence a um setor e possui um papel (role) que
 * determina suas permissões de acesso.
 * Implementa {@link UserDetails} para integração com o Spring Security.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotBlank
    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @NotBlank
    @Email
    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @NotBlank
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private UserRole role;

    /**
     * Setor ao qual o usuário pertence.
     * O campo sector_id é a FK na tabela users.
     */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sector_id", nullable = false)
    private Sector sector;

    @NotBlank
    @Column(name = "location", nullable = false, length = 150)
    private String location;

    /** ID do usuário no servidor Discord corporativo. Pode ser nulo. */
    @Column(name = "discord_user_id", length = 50)
    private String discordUserId;

    /** Segredo TOTP para autenticação 2FA. Armazenado criptografado. */
    @Convert(converter = CryptoConverter.class)
    @Column(name = "totp_secret", length = 500)
    private String totpSecret;

    // -------------------------------------------------------------------------
    // UserDetails contract
    // -------------------------------------------------------------------------

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + this.role.name()));
    }

    /** Retorna o hash BCrypt armazenado — usado pelo Spring Security. */
    @Override
    public String getPassword() {
        return this.passwordHash;
    }

    /** O e-mail é o identificador único utilizado na autenticação. */
    @Override
    public String getUsername() {
        return this.email;
    }
}
