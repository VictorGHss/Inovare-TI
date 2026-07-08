package br.dev.ctrls.inovareti.modules.appointment.infrastructure.config;

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
    private boolean billingEnabled;
    private String ingestionCron;
    private String monitorCron;
    private boolean testMode;
    private String testDoctorId;
    private String testModeDoctorIds;
    private java.util.List<String> testDoctorIds = new java.util.ArrayList<>();
    private java.util.List<String> activeDoctorIds = new java.util.ArrayList<>();

    public boolean isGlobalTestMode() {
        return this.testMode;
    }

    public boolean isTestMode(String doctorId) {
        String docId = doctorId != null ? doctorId.trim() : "";
        return testDoctorIds.contains(docId);
    }
    private String blipTemplateConfirmation;
    private String blipTemplateNudgePending;
    private String blipTemplateGroup;
    private String eligibleProcedureIds;
    private String blipBlocksConfirmSuccess;
    private boolean feegowStartupProbeEnabled;
    private String feegowBaseUrl;
    private String feegowUnidadeId;
    private String feegowSearchPath;
    private String feegowPatientPath;
    private String feegowProfessionalPath;
    private String feegowStatusPath;
    private String feegowConfirmedStatusId;
    private String feegowUpdateStatusPath;
    private String blipBaseUrl;
    private String blipSendMessagePath;
    private String blipSetContextPath;
    private String blipWabaNamespace;
    private String blipBuilderBotId;
    private long blipRateLimitMs;
    private int nudge1WaitHours;
    private int nudgeFinalWaitHours;
    private long sendIdempotencyHours;
    private long webhookIdempotencyHours;

    private final Bot bot = new Bot();
    private final State state = new State();
    private final Template template = new Template();
    private final Security security = new Security();

    // Mapeamento flat para as chaves do application.properties

    public void setBlipRouterKey(String blipRouterKey) { this.bot.setBlipRouterKey(blipRouterKey); }
    
    // Mapeia a chave app.appointment.motor.blip-authorization-key para a variável blipBotKey
    public void setBlipAuthorizationKey(String blipAuthorizationKey) { this.bot.setBlipBotKey(blipAuthorizationKey); }
    
    public void setBlipDeskKey(String blipDeskKey) { this.bot.setBlipDeskKey(blipDeskKey); }
    public void setBlipRouterIdentity(String blipRouterIdentity) { this.bot.setBlipRouterIdentity(blipRouterIdentity); }
    public void setBlipAgendamentoBotId(String blipAgendamentoBotId) { this.bot.setBlipAgendamentoBotId(blipAgendamentoBotId); }

    public void setBlipLandingConfirmacaoItsmStateId(String blipLandingConfirmacaoItsmStateId) { this.state.setBlipLandingConfirmacaoItsmStateId(blipLandingConfirmacaoItsmStateId); }
    public void setBlipItsmFlowId(String blipItsmFlowId) { this.state.setBlipItsmFlowId(blipItsmFlowId); }
    public void setBlipFluxov1FlowId(String blipFluxov1FlowId) { this.state.setBlipFluxov1FlowId(blipFluxov1FlowId); }
    public void setBlipLandingBlockId(String blipLandingBlockId) { this.state.setBlipLandingBlockId(blipLandingBlockId); }

    public void setBlipFallbackTemplates(String blipFallbackTemplates) { this.template.setBlipFallbackTemplates(blipFallbackTemplates); }

    public String getFeegowConfirmedStatusId() {
        return this.feegowConfirmedStatusId;
    }

    public void setWebhookToken(String webhookToken) { this.security.setWebhookToken(webhookToken); }

    @Getter
    @Setter
    public static class Bot {
        private String blipRouterKey;
        private String blipBotKey;
        private String blipDeskKey;
        private String blipRouterIdentity;
        private String blipAgendamentoBotId;
    }

    @Getter
    @Setter
    public static class State {
        private String blipLandingConfirmacaoItsmStateId;
        private String blipItsmFlowId;
        private String blipFluxov1FlowId;
        private String blipLandingBlockId;
    }

    @Getter
    @Setter
    public static class Template {
        private String blipFallbackTemplates;
    }

    @Getter
    @Setter
    public static class Security {
        private String webhookToken;
    }
}