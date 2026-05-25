package br.dev.ctrls.inovareti.domain.settings;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@RestController
@CrossOrigin(origins = "http://localhost:5173")
@RequestMapping("/admin/config")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminConfigController {

    @Value("${app.financeiro.smtp.from-email:}")
    private String smtpFromEmail;

    @Value("${app.financeiro.smtp.from-name:}")
    private String smtpFromName;

    @Value("${discord.bot.enabled:false}")
    private boolean discordBotEnabled;

    @Value("${discord.webhook.url:}")
    private String discordWebhookUrl;

    @Value("${discord.bot.token:}")
    private String discordBotToken;

    @Value("${blip.webhook.secret:}")
    private String blipWebhookSecret;

    private final br.dev.ctrls.inovareti.domain.notification.discord.DiscordWebhookService discordWebhookService;
    private final br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.FeegowProperties feegowProperties;
    private final br.dev.ctrls.inovareti.modules.finance.infrastructure.config.ContaAzulProperties contaAzulProperties;
    private final br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.AppointmentMotorProperties appointmentMotorProperties;

    @GetMapping
    public ResponseEntity<AdminConfigResponse> getConfig() {
        AdminConfigResponse res = new AdminConfigResponse();
        res.setSmtpFromEmail(smtpFromEmail);
        res.setSmtpFromName(smtpFromName);
        res.setDiscordBotEnabled(discordBotEnabled);
        String status = discordWebhookService.getDefaultWebhookStatus();
        res.setDiscordWebhookStatus(status);
        res.setDiscordWebhookPresent("PRESENT".equals(status));
        res.setDiscordWebhookUrlPresent(StringUtils.hasText(discordWebhookUrl));
        res.setDiscordBotTokenPresent(StringUtils.hasText(discordBotToken));
        res.setContaAzulClientIdPresent(StringUtils.hasText(contaAzulProperties.getClientId()));
        res.setContaAzulClientSecretPresent(StringUtils.hasText(contaAzulProperties.getClientSecret()));
        res.setFeegowApiKeyPresent(StringUtils.hasText(feegowProperties.getApiKey()));
        res.setFeegowUnitIdPresent(StringUtils.hasText(feegowProperties.getUnidadeId()));
        res.setBlipApiKeyPresent(StringUtils.hasText(appointmentMotorProperties.getBot().getBlipBotKey()));
        res.setBlipBotIdPresent(StringUtils.hasText(appointmentMotorProperties.getBot().getBlipAgendamentoBotId()));
        res.setBlipWebhookTokenPresent(StringUtils.hasText(appointmentMotorProperties.getSecurity().getWebhookToken()));
        res.setBlipWebhookSecretPresent(StringUtils.hasText(blipWebhookSecret));

        return ResponseEntity.ok(res);
    }

    @Data
    public static class AdminConfigResponse {
        private String smtpFromEmail;
        private String smtpFromName;
        private boolean discordBotEnabled;
        private boolean discordWebhookPresent;
        private String discordWebhookStatus;
        private boolean discordWebhookUrlPresent;
        private boolean discordBotTokenPresent;
        private boolean contaAzulClientIdPresent;
        private boolean contaAzulClientSecretPresent;
        private boolean feegowApiKeyPresent;
        private boolean feegowUnitIdPresent;
        private boolean blipApiKeyPresent;
        private boolean blipBotIdPresent;
        private boolean blipWebhookTokenPresent;
        private boolean blipWebhookSecretPresent;
    }
}
