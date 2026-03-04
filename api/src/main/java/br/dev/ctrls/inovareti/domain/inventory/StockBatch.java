package br.dev.ctrls.inovareti.domain.inventory;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Lote de entrada de estoque para um item de inventário.
 * Cada lote registra a quantidade, o preço unitário e a data de entrada.
 * O {@code remainingQuantity} é decrementado conforme o lote é consumido.
 */
@Entity
@Table(name = "stock_batches")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @NotNull
    @Positive
    @Column(name = "original_quantity", nullable = false)
    private Integer originalQuantity;

    @NotNull
    @Positive
    @Column(name = "remaining_quantity", nullable = false)
    private Integer remainingQuantity;

    /** Preço unitário de compra do item neste lote. */
    @NotNull
    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    /** Marca do item neste lote (ex: Logitech, HP, Dell). */
    @Column(name = "brand", length = 100)
    private String brand;

    /** Fornecedor do item neste lote (ex: Kabum, Kalunga, Dell Store). */
    @Column(name = "supplier", length = 150)
    private String supplier;

    /** Motivo da compra deste lote (ex: Reposição mensal, Expansão, Substituição). */
    @Column(name = "purchase_reason", length = 200)
    private String purchaseReason;

    @NotNull
    @Column(name = "entry_date", nullable = false)
    private LocalDateTime entryDate;
}
