package br.dev.ctrls.inovareti.modules.access.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entidade JPA para persistência de credenciais de acesso às catracas físicas.
 * Traduzida para o inglês seguindo as Regras de Nomenclatura Cruciais.
 * Comentários mantidos em PT-BR.
 */
@Entity
@Table(
    name = "access_credentials",
    indexes = {
        @Index(name = "idx_access_credentials_appointment_id", columnList = "appointment_id"),
        @Index(name = "idx_access_credentials_cpf", columnList = "cpf")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccessCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotNull
    @Column(name = "appointment_id", nullable = false)
    private String appointmentId;

    @NotNull
    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "cpf")
    private String cpf;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "user_type", nullable = false)
    private UserType userType;

    @NotNull
    @Column(name = "access_credential", nullable = false)
    private String accessCredential;

    @NotNull
    @Column(name = "locator", nullable = false)
    private String locator;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
