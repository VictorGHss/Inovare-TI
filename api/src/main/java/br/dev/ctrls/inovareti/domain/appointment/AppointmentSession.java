package br.dev.ctrls.inovareti.domain.appointment;

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

@Entity
@Table(name = "appointment_sessions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentSession {

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
}
