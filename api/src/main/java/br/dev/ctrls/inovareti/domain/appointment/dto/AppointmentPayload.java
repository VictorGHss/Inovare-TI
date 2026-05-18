package br.dev.ctrls.inovareti.domain.appointment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentPayload {
    private String action;
    private String doctorName;
    private String queue;
    private String patientName;
    private String patientCPF;
    private String patientBirthdate;
}
