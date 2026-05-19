package br.dev.ctrls.inovareti.modules.communication.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.notification")
public class NotificationProperties {

    private String discordWebhook;
    private String itsmBotApiUrl;
    private String itsmBotApiToken;
}
