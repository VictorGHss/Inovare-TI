package br.dev.ctrls.inovareti.domain.appointment;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import br.dev.ctrls.inovareti.domain.appointment.usecase.IngestAppointmentsUseCase;
import br.dev.ctrls.inovareti.domain.appointment.usecase.MonitorAppointmentNudgesUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class AppointmentMotorScheduler {

    private final AppointmentMotorProperties properties;
    private final IngestAppointmentsUseCase ingestAppointmentsUseCase;
    private final MonitorAppointmentNudgesUseCase monitorAppointmentNudgesUseCase;

    @Scheduled(cron = "${app.appointment.motor.ingestion-cron:0 0 8 * * *}")
    public void ingestDPlusOneAppointments() {
        if (!properties.isEnabled()) {
            return;
        }

        log.info("Scheduler de ingestão de agendamentos iniciado");
        ingestAppointmentsUseCase.execute();
    }

    @Scheduled(cron = "${app.appointment.motor.monitor-cron:0 */30 * * * *}")
    public void monitorNudges() {
        if (!properties.isEnabled()) {
            return;
        }

        log.info("Scheduler de monitoramento de nudge iniciado");
        monitorAppointmentNudgesUseCase.execute();
    }
}
