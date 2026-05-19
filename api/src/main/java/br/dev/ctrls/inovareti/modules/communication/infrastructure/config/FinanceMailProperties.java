package br.dev.ctrls.inovareti.modules.communication.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.financeiro")
public class FinanceMailProperties {

    private String testMode;
    private String devEmail;
    private Smtp smtp = new Smtp();

    @Getter
    @Setter
    public static class Smtp {
        private String fromEmail;
        private String fromName;
    }
}
