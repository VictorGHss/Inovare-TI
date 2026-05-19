package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import br.dev.ctrls.inovareti.modules.appointment.domain.model.TemplateVariableMapping;

@Entity
@Table(name = "appointment_template_variable_mapping")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateVariableMappingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "config_id", nullable = false)
    private AppointmentConfigEntity config;

    @Column(name = "placeholder_index", nullable = false)
    private Integer placeholderIndex;

    @Column(name = "dictionary_key", nullable = false, length = 80)
    private String dictionaryKey;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public TemplateVariableMapping toDomain() {
        return TemplateVariableMapping.builder()
                .id(this.id)
                .config(this.config != null ? this.config.toDomain() : null)
                .placeholderIndex(this.placeholderIndex)
                .dictionaryKey(this.dictionaryKey)
                .createdAt(this.createdAt)
                .build();
    }

    public static TemplateVariableMappingEntity fromDomain(TemplateVariableMapping domain) {
        if (domain == null) return null;
        return TemplateVariableMappingEntity.builder()
                .id(domain.getId())
                .config(domain.getConfig() != null ? AppointmentConfigEntity.fromDomain(domain.getConfig()) : null)
                .placeholderIndex(domain.getPlaceholderIndex())
                .dictionaryKey(domain.getDictionaryKey())
                .createdAt(domain.getCreatedAt())
                .build();
    }
}
