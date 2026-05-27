package br.dev.ctrls.inovareti.domain.ticket;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Categoria ITSM com prazo de SLA configurável em horas.
 * Mapeada para a tabela {@code itsm_categories} criada na migration V17.
 * Diferente de {@link TicketCategory} (com UUID), esta entidade usa
 * chave primária serial (int) e é usada para configuração de SLA por tipo de atendimento.
 */
@Entity
@Table(name = "itsm_categories")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItsmCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Integer id;

    @NotBlank
    @Column(name = "name", nullable = false, unique = true, length = 150)
    private String name;

    @NotNull
    @Min(1)
    @Column(name = "sla_hours", nullable = false)
    private Integer slaHours;
}
