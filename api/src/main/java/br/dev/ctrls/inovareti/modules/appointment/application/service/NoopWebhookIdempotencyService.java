package br.dev.ctrls.inovareti.modules.appointment.application.service;

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

    public boolean tryAcquireLock(String appointmentId) {
        return true;
    }

    public void saveCachedResult(String appointmentId, String jsonResult) {
    }

    public String getCachedResult(String appointmentId) {
        return null;
    }
}
