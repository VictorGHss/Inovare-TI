package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.entity;

import java.time.LocalDateTime;
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

import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSession;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSessionStatus;

@Entity
@Table(name = "appointment_sessions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "feegow_appointment_id", nullable = false, length = 64, unique = true)
    private String feegowAppointmentId;

    @Column(name = "patient_id", nullable = false, length = 64)
    private String patientId;

    @Column(name = "patient_phone", length = 40)
    private String phoneNumber;

    @Column(name = "doctor_profissional_id", length = 64)
    private String doctorProfissionalId;

    @Column(name = "appointment_at", nullable = false)
    private LocalDateTime appointmentAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AppointmentSessionStatus status;

    @Column(name = "last_interaction_at", nullable = false)
    private LocalDateTime lastInteractionAt;

    @Column(name = "last_notification_sent_at")
    private LocalDateTime lastNotificationSentAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "status_details", length = 500)
    private String statusDetails;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public AppointmentSession toDomain() {
        return AppointmentSession.builder()
                .id(this.id)
                .feegowAppointmentId(this.feegowAppointmentId)
                .patientId(this.patientId)
                .phoneNumber(this.phoneNumber)
                .doctorProfissionalId(this.doctorProfissionalId)
                .appointmentAt(this.appointmentAt)
                .status(this.status)
                .lastInteractionAt(this.lastInteractionAt)
                .lastNotificationSentAt(this.lastNotificationSentAt)
                .closedAt(this.closedAt)
                .statusDetails(this.statusDetails)
                .createdAt(this.createdAt)
                .updatedAt(this.updatedAt)
                .build();
    }

    public static AppointmentSessionEntity fromDomain(AppointmentSession domain) {
        if (domain == null) return null;
        return AppointmentSessionEntity.builder()
                .id(domain.getId())
                .feegowAppointmentId(domain.getFeegowAppointmentId())
                .patientId(domain.getPatientId())
                .phoneNumber(domain.getPhoneNumber())
                .doctorProfissionalId(domain.getDoctorProfissionalId())
                .appointmentAt(domain.getAppointmentAt())
                .status(domain.getStatus())
                .lastInteractionAt(domain.getLastInteractionAt())
                .lastNotificationSentAt(domain.getLastNotificationSentAt())
                .closedAt(domain.getClosedAt())
                .statusDetails(domain.getStatusDetails())
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .build();
    }
}
