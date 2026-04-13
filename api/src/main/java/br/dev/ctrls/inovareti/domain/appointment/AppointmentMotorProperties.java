package br.dev.ctrls.inovareti.domain.appointment;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.appointment.motor")
public class AppointmentMotorProperties {

    private boolean enabled = true;
    private String ingestionCron = "0 0 8 * * *";
    private String monitorCron = "0 */30 * * * *";
    private String feegowBaseUrl;
    private String feegowToken;
    private String feegowSearchPath = "/api/appointments/search";
    private String feegowPatientPath = "/api/patients/{id}";
    private String feegowUpdateStatusPath = "/api/appointments/{id}/status";
    private String blipBaseUrl;
    private String blipAuthorizationKey;
    private String blipSendMessagePath = "/messages";
    private String blipSetContextPath = "/contexts";
    private long blipRateLimitMs = 200L;
    private int nudge1WaitHours = 4;
    private int nudgeFinalWaitHours = 4;
    private long webhookIdempotencyHours = 48L;
}
