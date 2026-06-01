package br.dev.ctrls.inovareti.modules.finance.domain.model;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "processed_receipts")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessedReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "financial_link_id", nullable = false)
    private FinancialLink financialLink;

    @Column(name = "parcela_id", nullable = false, length = 120)
    private String parcelaId;

    @Column(name = "receipt_hash", nullable = false, length = 128)
    private String receiptHash;

    @Column(name = "original_recipient_email", nullable = false, length = 255)
    private String originalRecipientEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProcessedReceiptStatus status;

    @Column(name = "brevo_message_id", length = 120)
    private String brevoMessageId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> payload = new HashMap<>();

    @Column(name = "processed_at", nullable = false)
    @Builder.Default
    private LocalDateTime processedAt = LocalDateTime.now();

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;

    // Métodos Getter e Setter explícitos para blindar a IDE contra falhas do Lombok
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public FinancialLink getFinancialLink() {
        return financialLink;
    }

    public void setFinancialLink(FinancialLink financialLink) {
        this.financialLink = financialLink;
    }

    public String getParcelaId() {
        return parcelaId;
    }

    public void setParcelaId(String parcelaId) {
        this.parcelaId = parcelaId;
    }

    public String getReceiptHash() {
        return receiptHash;
    }

    public void setReceiptHash(String receiptHash) {
        this.receiptHash = receiptHash;
    }

    public String getOriginalRecipientEmail() {
        return originalRecipientEmail;
    }

    public void setOriginalRecipientEmail(String originalRecipientEmail) {
        this.originalRecipientEmail = originalRecipientEmail;
    }

    public ProcessedReceiptStatus getStatus() {
        return status;
    }

    public void setStatus(ProcessedReceiptStatus status) {
        this.status = status;
    }

    public String getBrevoMessageId() {
        return brevoMessageId;
    }

    public void setBrevoMessageId(String brevoMessageId) {
        this.brevoMessageId = brevoMessageId;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }
}
