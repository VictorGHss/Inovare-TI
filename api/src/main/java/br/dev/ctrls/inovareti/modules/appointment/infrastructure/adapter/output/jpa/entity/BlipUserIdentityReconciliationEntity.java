package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import br.dev.ctrls.inovareti.modules.appointment.domain.model.BlipUserIdentityReconciliation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entidade JPA para mapeamento da tabela 'blip_user_identities'.
 * COMENTÁRIO EM PORTUGUÊS (PT-BR):
 * Realiza o mapeamento físico da tabela que associa os GUIDs mascarados gerados
 * pela Meta/Blip aos BSUIDs e aos números de telefone físicos reais correspondentes.
 */
@Entity
@Table(
    name = "blip_user_identities",
    indexes = {
        @jakarta.persistence.Index(name = "idx_blip_user_identities_guid", columnList = "blip_guid"),
        @jakarta.persistence.Index(name = "idx_blip_user_identities_bsuid", columnList = "bsuid"),
        @jakarta.persistence.Index(name = "idx_blip_user_identities_phone", columnList = "phone_number")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlipUserIdentityReconciliationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "blip_guid", nullable = false, unique = true, length = 255)
    private String blipGuid;

    @Column(name = "bsuid", length = 255)
    private String bsuid;

    @Column(name = "phone_number", nullable = false, length = 40)
    private String phoneNumber;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public BlipUserIdentityReconciliation toDomain() {
        return BlipUserIdentityReconciliation.builder()
                .id(this.id)
                .blipGuid(this.blipGuid)
                .bsuid(this.bsuid)
                .phoneNumber(this.phoneNumber)
                .createdAt(this.createdAt)
                .updatedAt(this.updatedAt)
                .build();
    }

    public static BlipUserIdentityReconciliationEntity fromDomain(BlipUserIdentityReconciliation domain) {
        if (domain == null) return null;
        return BlipUserIdentityReconciliationEntity.builder()
                .id(domain.getId())
                .blipGuid(domain.getBlipGuid())
                .bsuid(domain.getBsuid())
                .phoneNumber(domain.getPhoneNumber())
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .build();
    }
}
