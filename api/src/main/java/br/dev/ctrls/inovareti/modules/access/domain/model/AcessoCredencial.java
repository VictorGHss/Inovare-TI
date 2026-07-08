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
 */
@Entity
@Table(
    name = "acesso_credencial",
    indexes = {
        @Index(name = "idx_acesso_credencial_id_agendamento", columnList = "id_agendamento"),
        @Index(name = "idx_acesso_credencial_cpf", columnList = "cpf")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AcessoCredencial {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotNull
    @Column(name = "id_agendamento", nullable = false)
    private String idAgendamento;

    @NotNull
    @Column(name = "nome", nullable = false)
    private String nome;

    @Column(name = "cpf")
    private String cpf;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_usuario", nullable = false)
    private TipoUsuario tipoUsuario;

    @NotNull
    @Column(name = "credencial_ger_acesso", nullable = false)
    private String credencialGerAcesso;

    @NotNull
    @Column(name = "localizador", nullable = false)
    private String localizador;

    @NotNull
    @Column(name = "data_criacao", nullable = false)
    private LocalDateTime dataCriacao;
}
