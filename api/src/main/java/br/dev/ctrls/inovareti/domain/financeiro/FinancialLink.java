package br.dev.ctrls.inovareti.domain.financeiro;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "financial_link")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialLink {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "contaazul_customer_id", nullable = false)
    private String contaAzulCustomerId;

    @Column(name = "contaazul_customer_name")
    private String contaAzulCustomerName;

    @Column(name = "contaazul_pessoa_uuid")
    private String contaAzulPessoaUuid;

    @Column(name = "email")
    private String email;

    @Column(name = "nome_cliente")
    private String nomeCliente;

    @Column(name = "email_synced_at")
    private OffsetDateTime emailSyncedAt;

    @Column(name = "linked_by_user_id")
    private UUID linkedByUserId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Canal canal;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public boolean isEmailStale(Duration maxAge) {
        return emailSyncedAt == null ||
                emailSyncedAt.isBefore(OffsetDateTime.now().minus(maxAge));
    }

    public enum Canal {
        EMAIL,
        DISCORD
    }
}
