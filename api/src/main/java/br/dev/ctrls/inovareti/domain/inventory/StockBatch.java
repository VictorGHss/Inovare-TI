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
import jakarta.validation.constraints.PositiveOrZero;
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
    @PositiveOrZero
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

    /** Nome do arquivo da nota fiscal (PDF ou imagem). */
    @Column(name = "invoice_file_name", length = 255)
    private String invoiceFileName;

    /** Tipo MIME do arquivo da nota fiscal (ex: application/pdf, image/png). */
    @Column(name = "invoice_content_type", length = 50)
    private String invoiceContentType;

    /** Caminho do arquivo da nota fiscal no disco (ex: uploads/invoices/batch-{id}-{timestamp}.pdf). */
    @Column(name = "invoice_file_path", length = 500)
    private String invoiceFilePath;

    @Builder.Default
    @jakarta.persistence.OneToMany(mappedBy = "stockBatch", cascade = jakarta.persistence.CascadeType.ALL, fetch = FetchType.LAZY)
    private java.util.List<StockBatchInstallment> installments = new java.util.ArrayList<>();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Item getItem() { return item; }
    public void setItem(Item item) { this.item = item; }

    public Integer getOriginalQuantity() { return originalQuantity; }
    public void setOriginalQuantity(Integer originalQuantity) { this.originalQuantity = originalQuantity; }

    public Integer getRemainingQuantity() { return remainingQuantity; }
    public void setRemainingQuantity(Integer remainingQuantity) { this.remainingQuantity = remainingQuantity; }

    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }

    public String getSupplier() { return supplier; }
    public void setSupplier(String supplier) { this.supplier = supplier; }

    public String getPurchaseReason() { return purchaseReason; }
    public void setPurchaseReason(String purchaseReason) { this.purchaseReason = purchaseReason; }

    public LocalDateTime getEntryDate() { return entryDate; }
    public void setEntryDate(LocalDateTime entryDate) { this.entryDate = entryDate; }

    public String getInvoiceFileName() { return invoiceFileName; }
    public void setInvoiceFileName(String invoiceFileName) { this.invoiceFileName = invoiceFileName; }

    public String getInvoiceContentType() { return invoiceContentType; }
    public void setInvoiceContentType(String invoiceContentType) { this.invoiceContentType = invoiceContentType; }

    public String getInvoiceFilePath() { return invoiceFilePath; }
    public void setInvoiceFilePath(String invoiceFilePath) { this.invoiceFilePath = invoiceFilePath; }

    public java.util.List<StockBatchInstallment> getInstallments() { return installments; }
    public void setInstallments(java.util.List<StockBatchInstallment> installments) { this.installments = installments; }

    public static StockBatchBuilder builder() {
        return new StockBatchBuilder();
    }

    public static class StockBatchBuilder {
        private Item item;
        private Integer originalQuantity;
        private Integer remainingQuantity;
        private BigDecimal unitPrice;
        private String brand;
        private String supplier;
        private String purchaseReason;
        private LocalDateTime entryDate;
        private java.util.List<StockBatchInstallment> installments = new java.util.ArrayList<>();

        public StockBatchBuilder item(Item item) { this.item = item; return this; }
        public StockBatchBuilder originalQuantity(Integer originalQuantity) { this.originalQuantity = originalQuantity; return this; }
        public StockBatchBuilder remainingQuantity(Integer remainingQuantity) { this.remainingQuantity = remainingQuantity; return this; }
        public StockBatchBuilder unitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; return this; }
        public StockBatchBuilder brand(String brand) { this.brand = brand; return this; }
        public StockBatchBuilder supplier(String supplier) { this.supplier = supplier; return this; }
        public StockBatchBuilder purchaseReason(String purchaseReason) { this.purchaseReason = purchaseReason; return this; }
        public StockBatchBuilder entryDate(LocalDateTime entryDate) { this.entryDate = entryDate; return this; }
        public StockBatchBuilder installments(java.util.List<StockBatchInstallment> installments) { this.installments = installments; return this; }

        public StockBatch build() {
            StockBatch batch = new StockBatch();
            batch.setItem(item);
            batch.setOriginalQuantity(originalQuantity);
            batch.setRemainingQuantity(remainingQuantity);
            batch.setUnitPrice(unitPrice);
            batch.setBrand(brand);
            batch.setSupplier(supplier);
            batch.setPurchaseReason(purchaseReason);
            batch.setEntryDate(entryDate);
            batch.setInstallments(installments);
            return batch;
        }
    }
}
