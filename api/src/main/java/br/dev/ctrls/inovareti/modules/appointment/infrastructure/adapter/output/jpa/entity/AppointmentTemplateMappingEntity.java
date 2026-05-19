package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

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

import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentTemplateMapping;

@Entity
@Table(name = "appointment_template_mapping")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentTemplateMappingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "template_name", nullable = false, length = 120)
    private String templateName;

    @Column(name = "placeholder_index", nullable = false)
    private Integer placeholderIndex;

    @Column(name = "feegow_field_name", nullable = false, length = 120)
    private String feegowFieldName;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public AppointmentTemplateMapping toDomain() {
        return AppointmentTemplateMapping.builder()
                .id(this.id)
                .templateName(this.templateName)
                .placeholderIndex(this.placeholderIndex)
                .feegowFieldName(this.feegowFieldName)
                .createdAt(this.createdAt)
                .updatedAt(this.updatedAt)
                .build();
    }

    public static AppointmentTemplateMappingEntity fromDomain(AppointmentTemplateMapping domain) {
        if (domain == null) return null;
        return AppointmentTemplateMappingEntity.builder()
                .id(domain.getId())
                .templateName(domain.getTemplateName())
                .placeholderIndex(domain.getPlaceholderIndex())
                .feegowFieldName(domain.getFeegowFieldName())
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .build();
    }
}