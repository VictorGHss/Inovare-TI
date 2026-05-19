package br.dev.ctrls.inovareti.modules.appointment.infrastructure.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.client.FeegowPatientClient;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.client.FeegowProfessionalClient;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.client.FeegowAppointmentClient;

/**
 * Configuração: FeegowClientConfig.
 * Cria e expõe as instâncias dinâmicas das interfaces de cliente declarativas HTTP
 * associando-as ao RestClient corporativo 'feegowRestClient'.
 */
@Configuration
public class FeegowClientConfig {

    @Bean
    public FeegowPatientClient feegowPatientClient(@Qualifier("feegowRestClient") RestClient feegowRestClient) {
        HttpServiceProxyFactory factory = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(feegowRestClient))
                .build();
        return factory.createClient(FeegowPatientClient.class);
    }

    @Bean
    public FeegowProfessionalClient feegowProfessionalClient(@Qualifier("feegowRestClient") RestClient feegowRestClient) {
        HttpServiceProxyFactory factory = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(feegowRestClient))
                .build();
        return factory.createClient(FeegowProfessionalClient.class);
    }

    @Bean
    public FeegowAppointmentClient feegowAppointmentClient(@Qualifier("feegowRestClient") RestClient feegowRestClient) {
        HttpServiceProxyFactory factory = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(feegowRestClient))
                .build();
        return factory.createClient(FeegowAppointmentClient.class);
    }
}
