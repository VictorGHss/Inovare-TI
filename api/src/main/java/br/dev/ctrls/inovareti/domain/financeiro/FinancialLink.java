package br.dev.ctrls.inovareti.domain.financeiro;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import br.dev.ctrls.inovareti.domain.user.User;
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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "contaazul_customer_id", nullable = false, unique = true, length = 100)
    private String contaAzulCustomerId;

    @Column(name = "contaazul_customer_name", length = 160)
    private String contaAzulCustomerName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_by_user_id")
    private User linkedByUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_channel", nullable = false, length = 20)
    @Builder.Default
    private FinancialNotificationChannel notificationChannel = FinancialNotificationChannel.EMAIL;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
