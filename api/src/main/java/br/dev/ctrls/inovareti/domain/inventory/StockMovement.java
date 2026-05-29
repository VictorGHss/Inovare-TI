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

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getItemId() { return itemId; }
    public void setItemId(UUID itemId) { this.itemId = itemId; }

    public StockMovementType getType() { return type; }
    public void setType(StockMovementType type) { this.type = type; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }

    public LocalDateTime getDate() { return date; }
    public void setDate(LocalDateTime date) { this.date = date; }

    public BigDecimal getUnitPriceAtTime() { return unitPriceAtTime; }
    public void setUnitPriceAtTime(BigDecimal unitPriceAtTime) { this.unitPriceAtTime = unitPriceAtTime; }

    public UUID getRecipientUserId() { return recipientUserId; }
    public void setRecipientUserId(UUID recipientUserId) { this.recipientUserId = recipientUserId; }

    public static StockMovementBuilder builder() {
        return new StockMovementBuilder();
    }

    public static class StockMovementBuilder {
        private UUID itemId;
        private StockMovementType type;
        private Integer quantity;
        private String reference;
        private LocalDateTime date;
        private BigDecimal unitPriceAtTime;
        private UUID recipientUserId;

        public StockMovementBuilder itemId(UUID itemId) { this.itemId = itemId; return this; }
        public StockMovementBuilder type(StockMovementType type) { this.type = type; return this; }
        public StockMovementBuilder quantity(Integer quantity) { this.quantity = quantity; return this; }
        public StockMovementBuilder reference(String reference) { this.reference = reference; return this; }
        public StockMovementBuilder date(LocalDateTime date) { this.date = date; return this; }
        public StockMovementBuilder unitPriceAtTime(BigDecimal unitPriceAtTime) { this.unitPriceAtTime = unitPriceAtTime; return this; }
        public StockMovementBuilder recipientUserId(UUID recipientUserId) { this.recipientUserId = recipientUserId; return this; }

        public StockMovement build() {
            StockMovement movement = new StockMovement();
            movement.setItemId(itemId);
            movement.setType(type);
            movement.setQuantity(quantity);
            movement.setReference(reference);
            movement.setDate(date);
            movement.setUnitPriceAtTime(unitPriceAtTime);
            movement.setRecipientUserId(recipientUserId);
            return movement;
        }
    }
}
