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

    private boolean enabled;
    private String ingestionCron;
    private String monitorCron;
    private boolean testMode;
    private String testDoctorId;
    private boolean feegowStartupProbeEnabled;
    private String feegowBaseUrl;
    private String feegowSearchPath;
    private String feegowPatientPath;
    private String feegowUpdateStatusPath;
    private String blipBaseUrl;
    private String blipAuthorizationKey;
    private String blipSendMessagePath;
    private String blipSetContextPath;
    private String blipRouterIdentity;
    private String blipWabaNamespace;
    private String blipBuilderBotId;
    private String blipAgendamentoBotId;
    private long blipRateLimitMs;
    private int nudge1WaitHours;
    private int nudgeFinalWaitHours;
    private long sendIdempotencyHours;
    private long webhookIdempotencyHours;
}
