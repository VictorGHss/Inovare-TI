package br.dev.ctrls.inovareti.domain.inventory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Item do inventário de TI.
 * O campo {@code specifications} armazena atributos livres
 * (ex.: marca, modelo, número de série) como JSON no banco.
 */
@Entity
@Table(name = "items")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Item {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_category_id", nullable = false)
    private ItemCategory itemCategory;

    @NotBlank
    @Column(name = "name", nullable = false, length = 150)
    private String name;

    /** Estoque atual. Sempre inicia em 0 e é atualizado via lotes. */
    @NotNull
    @PositiveOrZero
    @Column(name = "current_stock", nullable = false)
    private Integer currentStock;

    /**
     * Especificações técnicas livres armazenadas como JSONB no PostgreSQL.
     * Exemplos de chaves: "marca", "modelo", "serialNumber", "voltagem".
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "specifications", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> specifications = new HashMap<>();
}
