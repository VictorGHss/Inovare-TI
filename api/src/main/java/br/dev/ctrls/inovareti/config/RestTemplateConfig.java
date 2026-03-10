package br.dev.ctrls.inovareti.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Configuração do bean RestTemplate.
 * Utilizado para requisições HTTP externas (ex.: webhooks do Discord).
 */
@Configuration
public class RestTemplateConfig {

    /**
     * Cria e configura o bean RestTemplate.
     *
     * @return instância configurada de RestTemplate
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
