package br.dev.ctrls.inovareti.modules.appointment.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.feegow")
public class FeegowProperties {

    private String apiKey;
    private String unidadeId;
    private String statusUpdateUrl = "https://api.feegow.com/v1/api/appoints/statusUpdate";
    private String cancelUrl = "https://api.feegow.com/v1/api/appoints/cancel-appoint";
    private Integer startupProbeStatusId;
}
