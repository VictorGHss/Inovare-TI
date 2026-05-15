package br.dev.ctrls.inovareti.domain.appointment;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnMissingBean(WebhookIdempotencyService.class)
public class NoopWebhookIdempotencyService {

    public boolean registerIfFirstTime(String messageId) {
        return true;
    }

    public boolean registerActionIfFirstTime(String appointmentId, String action) {
        return true;
    }
}
