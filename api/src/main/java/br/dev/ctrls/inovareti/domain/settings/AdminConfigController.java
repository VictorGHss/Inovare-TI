package br.dev.ctrls.inovareti.domain.settings;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@RestController
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

    private final br.dev.ctrls.inovareti.domain.notification.discord.DiscordWebhookService discordWebhookService;

    @GetMapping
    public ResponseEntity<AdminConfigResponse> getConfig() {
        AdminConfigResponse res = new AdminConfigResponse();
        res.setSmtpFromEmail(smtpFromEmail);
        res.setSmtpFromName(smtpFromName);
        res.setDiscordBotEnabled(discordBotEnabled);
        String status = discordWebhookService.getDefaultWebhookStatus();
        res.setDiscordWebhookStatus(status);
        res.setDiscordWebhookPresent("PRESENT".equals(status));

        return ResponseEntity.ok(res);
    }

    @Data
    public static class AdminConfigResponse {
        private String smtpFromEmail;
        private String smtpFromName;
        private boolean discordBotEnabled;
        private boolean discordWebhookPresent;
        private String discordWebhookStatus;
    }
}
