package br.dev.ctrls.inovareti.domain.asset;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "asset_maintenances")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetMaintenance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @NotNull
    @Column(name = "maintenance_date", nullable = false)
    private LocalDate maintenanceDate;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private MaintenanceType type;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "cost", precision = 10, scale = 2)
    private BigDecimal cost;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "technician_id", nullable = false)
    private User technician;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public enum MaintenanceType {
        PREVENTIVE,
        CORRECTIVE,
        UPGRADE
    }
}
