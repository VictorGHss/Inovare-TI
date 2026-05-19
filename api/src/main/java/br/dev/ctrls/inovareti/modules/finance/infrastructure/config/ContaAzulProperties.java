package br.dev.ctrls.inovareti.modules.finance.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.contaazul")
public class ContaAzulProperties {

    private String apiV2BaseUrl;
    private String legacyBaseUrl;
    private String paymentsUrl;
    private String customersV1Url;
    private String customerByIdV1UrlTemplate;
    private String baixaDetailsUrl;
    private String parcelaByIdUrlTemplate;
    private String receiptPdfUrlTemplate;
    private String salesPdfV1UrlTemplate;
    private String salesV2Url;
    private String salesV2StableUrl;
    private String salesV2StableFallbackUrl;
    private String authorizationUrl;
    private String tokenUrl;
    private String clientId;
    private String clientSecret;
    private String redirectUri;
    private String parcelBaixaFallbackUrlTemplate;

    private Automation automation = new Automation();

    @Getter
    @Setter
    public static class Automation {
        private boolean enabled;
        private String cron;
    }
}
