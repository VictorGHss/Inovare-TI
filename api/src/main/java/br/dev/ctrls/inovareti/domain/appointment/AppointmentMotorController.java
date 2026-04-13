package br.dev.ctrls.inovareti.domain.appointment;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.dev.ctrls.inovareti.domain.appointment.usecase.IngestAppointmentsUseCase;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/appointments")
@RequiredArgsConstructor
public class AppointmentMotorController {

    private final AppointmentMotorProperties appointmentMotorProperties;
    private final IngestAppointmentsUseCase ingestAppointmentsUseCase;

    @GetMapping("/motor-config")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> motorConfig() {
        String mode = appointmentMotorProperties.isTestMode() ? "TEST" : "PROD";
        return ResponseEntity.ok(Map.of(
                "enabled", appointmentMotorProperties.isEnabled(),
                "testMode", appointmentMotorProperties.isTestMode(),
                "testDoctorId", appointmentMotorProperties.getTestDoctorId(),
                "mode", mode));
    }

    @PostMapping("/trigger-manual")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> triggerManual() {
        IngestAppointmentsUseCase.IngestionSummary summary = ingestAppointmentsUseCase.execute();

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "messages_sent", summary.messagesSent(),
                "mode", summary.mode()));
    }
}
