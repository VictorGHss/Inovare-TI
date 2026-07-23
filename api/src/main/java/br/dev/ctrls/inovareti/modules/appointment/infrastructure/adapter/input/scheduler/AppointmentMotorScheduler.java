package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.input.scheduler;

import io.micrometer.observation.annotation.Observed;

import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.AppointmentMotorProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import br.dev.ctrls.inovareti.modules.appointment.application.usecase.IngestAppointmentsUseCase;
import br.dev.ctrls.inovareti.modules.appointment.application.usecase.MonitorAppointmentNudgesUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
@Observed
public class AppointmentMotorScheduler {

    private final AppointmentMotorProperties properties;
    private final IngestAppointmentsUseCase ingestAppointmentsUseCase;
    private final MonitorAppointmentNudgesUseCase monitorAppointmentNudgesUseCase;

    @Scheduled(cron = "${app.appointment.motor.ingestion-cron}")
    public void ingestDPlusOneAppointments() {
        if (!properties.isEnabled()) {
            return;
        }

        log.info("Scheduler de ingestão de agendamentos iniciado");

        java.util.List<String> targetDoctorIds = new java.util.ArrayList<>();
        if (properties.getActiveDoctorIds() != null && !properties.getActiveDoctorIds().isEmpty()) {
            targetDoctorIds.addAll(properties.getActiveDoctorIds());
        }
        if (properties.getTestDoctorIds() != null && !properties.getTestDoctorIds().isEmpty()) {
            targetDoctorIds.addAll(properties.getTestDoctorIds());
        }

        if (!targetDoctorIds.isEmpty()) {
            log.info("Scheduler direcionando ingestão para os médicos selecionados: {}", targetDoctorIds);
            ingestAppointmentsUseCase.execute(targetDoctorIds);
        } else {
            log.info("Nenhum médico configurado especificamente. Iniciando ingestão genérica.");
            ingestAppointmentsUseCase.execute();
        }
    }

    @Scheduled(cron = "${app.appointment.motor.monitor-cron:0 0 9,11,13,15,17 * * MON-FRI}")
    public void monitorNudges() {
        log.info("[NUDGE-SCHEDULER] Iniciando ciclo de monitoramento de nudges às {}...",
                java.time.LocalDateTime.now(java.time.ZoneId.of("America/Sao_Paulo")));

        if (!properties.isEnabled()) {
            return;
        }

        monitorAppointmentNudgesUseCase.execute();
    }
}


