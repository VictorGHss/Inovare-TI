package br.dev.ctrls.inovareti.modules.communication.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "discord")
public class DiscordProperties {

    private Bot bot = new Bot();
    private Webhook webhook = new Webhook();
    private Operational operational = new Operational();
    private Thumbnail thumbnail = new Thumbnail();

    @Getter
    @Setter
    public static class Bot {
        private boolean enabled;
        private String token;
    }

    @Getter
    @Setter
    public static class Webhook {
        private String url;
        private Retry retry = new Retry();
    }

    @Getter
    @Setter
    public static class Operational {
        private Webhook webhook = new Webhook();
        private String ticketUrlBase;
    }

    @Getter
    @Setter
    public static class Retry {
        private int maxAttempts;
        private long backoffMs;
    }

    @Getter
    @Setter
    public static class Thumbnail {
        private String url;
    }
}
