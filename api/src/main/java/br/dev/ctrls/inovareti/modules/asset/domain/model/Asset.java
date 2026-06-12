package br.dev.ctrls.inovareti.modules.asset.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import br.dev.ctrls.inovareti.modules.user.domain.model.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "assets")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * Relacionamento N:N com usuários do sistema.
     * Permite ativos compartilhados (ex.: impressoras, servidores).
     * Tabela intermediária: asset_users (criada em V9__allow_multiusers_per_asset.sql).
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "asset_users",
            joinColumns = @JoinColumn(name = "asset_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    @Builder.Default
    private Set<User> users = new HashSet<>();

    @NotBlank
    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @NotBlank
    @Column(name = "patrimony_code", nullable = false, length = 80)
    private String patrimonyCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private AssetCategory category;

    @Column(name = "specifications", columnDefinition = "text")
    private String specifications;

    /** Nome do arquivo da nota fiscal (PDF ou imagem). */
    @Column(name = "invoice_file_name", length = 255)
    private String invoiceFileName;

    /** Tipo MIME do arquivo da nota fiscal (ex: application/pdf, image/png). */
    @Column(name = "invoice_content_type", length = 50)
    private String invoiceContentType;

    /** Caminho do arquivo da nota fiscal no disco (ex: uploads/invoices/asset-{id}-{timestamp}.pdf). */
    @Column(name = "invoice_file_path", length = 500)
    private String invoiceFilePath;

    /**
     * Valor de aquisição do ativo, conforme nota fiscal.
     * Usado para controle contábil e custo do ativo.
     */
    @Column(name = "acquisition_value", precision = 19, scale = 2)
    private BigDecimal acquisitionValue;

    @Column(name = "is_critical", nullable = false)
    @Builder.Default
    private boolean isCritical = false;

    /** Indica se este equipamento foi adquirido recentemente, devendo aparecer no relatório de saídas. */
    @Column(name = "is_new_acquisition", nullable = false)
    @Builder.Default
    private boolean isNewAcquisition = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
