package br.dev.ctrls.inovareti.modules.appointment.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.appointment")
public class AppointmentWebhookProperties {

    private long webhookIdempotencyHours;
}
