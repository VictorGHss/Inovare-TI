package br.dev.ctrls.inovareti.domain.appointment;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnMissingBean(AppointmentSendIdempotencyService.class)
public class NoopAppointmentSendIdempotencyService {

    public boolean registerIfFirstSend(String appointmentId) {
        return true;
    }
}
