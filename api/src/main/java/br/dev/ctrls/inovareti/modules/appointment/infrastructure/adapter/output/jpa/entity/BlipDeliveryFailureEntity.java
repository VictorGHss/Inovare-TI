package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

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

import br.dev.ctrls.inovareti.modules.appointment.domain.model.BlipDeliveryFailure;

/**
 * Entidade JPA que mapeia a tabela blip_delivery_failures do banco de dados.
 * Utilizada para persistência na camada de infraestrutura.
 */
@Entity
@Table(
    name = "blip_delivery_failures",
    indexes = {
        @jakarta.persistence.Index(name = "idx_blip_delivery_failures_message_id", columnList = "message_id"),
        @jakarta.persistence.Index(name = "idx_blip_delivery_failures_appointment_id", columnList = "appointment_id")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlipDeliveryFailureEntity {

    /**
     * Nome do campo usado para ordenação e consultas baseadas em tempo.
     */
    public static final String FIELD_CREATED_AT = "createdAt";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "message_id", nullable = false, length = 128)
    private String messageId;

    @Column(name = "appointment_id", length = 64)
    private String appointmentId;

    @Column(name = "error_code", nullable = false)
    private Integer errorCode;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Converte esta entidade JPA para a entidade pura de domínio BlipDeliveryFailure.
     *
     * @return O objeto de domínio BlipDeliveryFailure correspondente.
     */
    public BlipDeliveryFailure toDomain() {
        return BlipDeliveryFailure.builder()
                .id(this.id)
                .messageId(this.messageId)
                .appointmentId(this.appointmentId)
                .errorCode(this.errorCode)
                .errorMessage(this.errorMessage)
                .traceId(this.traceId)
                .createdAt(this.createdAt)
                .build();
    }

    /**
     * Cria uma entidade JPA a partir de um objeto de domínio puro.
     *
     * @param domain O objeto de domínio BlipDeliveryFailure.
     * @return A entidade JPA BlipDeliveryFailureEntity ou null se o domínio for nulo.
     */
    public static BlipDeliveryFailureEntity fromDomain(BlipDeliveryFailure domain) {
        if (domain == null) return null;
        return BlipDeliveryFailureEntity.builder()
                .id(domain.getId())
                .messageId(domain.getMessageId())
                .appointmentId(domain.getAppointmentId())
                .errorCode(domain.getErrorCode())
                .errorMessage(domain.getErrorMessage())
                .traceId(domain.getTraceId())
                .createdAt(domain.getCreatedAt())
                .build();
    }
}
