package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

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

import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentCategory;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentVariableLog;

@Entity
@Table(name = "appointment_variable_logs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentVariableLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private AppointmentSessionEntity session;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AppointmentCategory category;

    @Column(name = "placeholder_index", nullable = false)
    private Integer placeholderIndex;

    @Column(name = "dictionary_key", nullable = false, length = 80)
    private String dictionaryKey;

    @Column(name = "resolved_value", nullable = false, columnDefinition = "text")
    private String resolvedValue;

    @CreationTimestamp
    @Column(name = "sent_at", nullable = false, updatable = false)
    private LocalDateTime sentAt;

    public AppointmentVariableLog toDomain() {
        return AppointmentVariableLog.builder()
                .id(this.id)
                .session(this.session != null ? this.session.toDomain() : null)
                .category(this.category)
                .placeholderIndex(this.placeholderIndex)
                .dictionaryKey(this.dictionaryKey)
                .resolvedValue(this.resolvedValue)
                .sentAt(this.sentAt)
                .build();
    }

    public static AppointmentVariableLogEntity fromDomain(AppointmentVariableLog domain) {
        if (domain == null) return null;
        return AppointmentVariableLogEntity.builder()
                .id(domain.getId())
                .session(domain.getSession() != null ? AppointmentSessionEntity.fromDomain(domain.getSession()) : null)
                .category(domain.getCategory())
                .placeholderIndex(domain.getPlaceholderIndex())
                .dictionaryKey(domain.getDictionaryKey())
                .resolvedValue(domain.getResolvedValue())
                .sentAt(domain.getSentAt())
                .build();
    }
}
