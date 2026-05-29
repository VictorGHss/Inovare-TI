package br.dev.ctrls.inovareti.domain.inventory;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "stock_movements")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotNull
    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10)
    private StockMovementType type;

    @NotNull
    @Positive
    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @NotNull
    @Column(name = "reference", nullable = false, length = 255)
    private String reference;

    @NotNull
    @Column(name = "date", nullable = false)
    private LocalDateTime date;

    /**
     * Valor total (soma dos preços das quantidades consumidas dos lotes) registrado
     * no momento da saída. Este campo guarda a "verdade financeira" do custo do
     * movimento, permitindo auditoria posterior por centro de custo.
     */
    @Column(name = "unit_price_at_time", precision = 19, scale = 2)
    private BigDecimal unitPriceAtTime;

    @Column(name = "recipient_user_id")
    private UUID recipientUserId;
}
