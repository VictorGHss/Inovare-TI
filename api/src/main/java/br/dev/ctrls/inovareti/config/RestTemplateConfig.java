package br.dev.ctrls.inovareti.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration for RestTemplate bean.
 * Used for external HTTP requests (e.g., Discord webhooks).
 */
@Configuration
public class RestTemplateConfig {

    /**
     * Creates and configures a RestTemplate bean.
     *
     * @param builder the RestTemplateBuilder provided by Spring Boot
     * @return configured RestTemplate instance
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder.build();
    }
}
