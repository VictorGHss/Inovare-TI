package br.dev.ctrls.inovareti.modules.appointment.application.service;

import io.micrometer.observation.annotation.Observed;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnMissingBean(AppointmentSendIdempotencyService.class)
@Observed
public class NoopAppointmentSendIdempotencyService {

    public boolean registerIfFirstSend(String appointmentId) {
        return true;
    }
}


