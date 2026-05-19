package br.dev.ctrls.inovareti.modules.communication.infrastructure.config;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * Propriedades de configuração SMTP do Spring Mail.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "spring.mail")
public class SpringMailProperties {

    private int port;
    private String username;
    private Map<String, String> properties = new HashMap<>();
}
