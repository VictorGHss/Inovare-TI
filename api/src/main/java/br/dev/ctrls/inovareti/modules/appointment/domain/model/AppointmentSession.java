package br.dev.ctrls.inovareti.modules.appointment.domain.model;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentSession {

    private UUID id;
    private String feegowAppointmentId;
    private String patientId;
    private String phoneNumber;
    private String doctorProfissionalId;
    private LocalDateTime appointmentAt;
    private AppointmentSessionStatus status;
    private LocalDateTime lastInteractionAt;
    private LocalDateTime lastNotificationSentAt;
    private LocalDateTime closedAt;
    private String statusDetails;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
