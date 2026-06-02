package br.dev.ctrls.inovareti.modules.appointment.application.service;

import io.micrometer.observation.annotation.Observed;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnMissingBean(WebhookIdempotencyService.class)
@Observed
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


