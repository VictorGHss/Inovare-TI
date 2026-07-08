package br.dev.ctrls.inovareti.modules.access.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Getter;
import lombok.Setter;

/**
 * Propriedades de configuração da integração com a GerAcesso.
 * Registra o prefixo "inovare.geracesso" para sanar os avisos de propriedade desconhecida da IDE.
 * Comentários em PT-BR conforme as Regras de Ouro.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "inovare.geracesso")
public class GerAcessoProperties {

    /**
     * URL do endpoint local da GerAcesso.
     */
    private String url;

    /**
     * Token de autorização para as chamadas à API da GerAcesso.
     */
    private String token;
}
