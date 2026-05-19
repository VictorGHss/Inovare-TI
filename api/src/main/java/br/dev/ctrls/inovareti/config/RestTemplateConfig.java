package br.dev.ctrls.inovareti.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.support.HttpRequestWrapper;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.AppointmentMotorProperties;
import lombok.extern.slf4j.Slf4j;

/**
 * Configuração do bean RestTemplate.
 * Utilizado para requisições HTTP externas (ex.: webhooks do Discord).
 */
@Slf4j
@Configuration
public class RestTemplateConfig {

    /**
     * Cria e configura o bean RestTemplate.
     *
     * @return instância configurada de RestTemplate
     */
    @Bean
    @Primary
    public RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplate();

        // Blindagem global de saída: impede prefixo fantasma /api para host da Conta Azul.
        restTemplate.getInterceptors().add(new ContaAzulPathSanitizerInterceptor());
        return restTemplate;
    }

    /**
     * RestTemplate dedicado para Feegow, sem interceptores de outras integrações.
     */
    @Bean
    @Qualifier("feegowRestTemplate")
    public RestTemplate feegowRestTemplate() {
        return new RestTemplate();
    }

    /**
     * {@link RestClient} dedicado à Feegow: {@code defaultStatusHandler} evita exceções automáticas em 4xx/5xx
     * (o chamador inspeciona o {@link org.springframework.http.ResponseEntity} ou o corpo).
     */
    @Bean
    @Qualifier("feegowRestClient")
    public RestClient feegowRestClient(AppointmentMotorProperties appointmentMotorProperties) {
        String base = appointmentMotorProperties.getFeegowBaseUrl();
        if (base == null || base.isBlank()) {
            base = "http://localhost";
        }
        String normalizedBase = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return RestClient.builder()
                .baseUrl(normalizedBase)
                .defaultStatusHandler(HttpStatusCode::is4xxClientError, RestTemplateConfig::drainFeegowErrorWithoutThrowing)
                .defaultStatusHandler(HttpStatusCode::is5xxServerError, RestTemplateConfig::drainFeegowErrorWithoutThrowing)
                .build();
    }

    private static void drainFeegowErrorWithoutThrowing(HttpRequest request, ClientHttpResponse response) throws IOException {
        try (InputStream in = response.getBody()) {
            in.transferTo(OutputStream.nullOutputStream());
        }
        log.warn("Feegow resposta HTTP {} em {} — corpo drenado (handler defaultStatusHandler, sem exceção automática)",
                response.getStatusCode(), request.getURI());
    }

    private static final class ContaAzulPathSanitizerInterceptor implements ClientHttpRequestInterceptor {

        @Override
        public ClientHttpResponse intercept(
                HttpRequest request,
                byte[] body,
                ClientHttpRequestExecution execution) throws java.io.IOException {
            URI originalUri = request.getURI();
            URI sanitizedUri = sanitizeContaAzulUri(originalUri);

            HttpRequest requestToExecute = request;
            if (!sanitizedUri.equals(originalUri)) {
                log.warn(
                        "URL de saída da Conta Azul foi sanitizada para remover prefixo /api indevido. original={} sanitized={}",
                        originalUri,
                        sanitizedUri);

                requestToExecute = new HttpRequestWrapper(request) {
                    @Override
                    public URI getURI() {
                        return sanitizedUri;
                    }
                };
            }

            return execution.execute(requestToExecute, body);
        }

        private URI sanitizeContaAzulUri(URI uri) {
            if (uri == null || uri.getHost() == null || !uri.getHost().toLowerCase().contains("contaazul.com")) {
                return uri;
            }

            String normalizedHost = "api.contaazul.com".equalsIgnoreCase(uri.getHost())
                    ? "api-v2.contaazul.com"
                    : uri.getHost();

            String rawPath = uri.getRawPath() != null ? uri.getRawPath() : "";
            String normalizedPath = rawPath
                    .replaceFirst("(?i)^/api/v1/", "/v1/")
                    .replaceFirst("(?i)^/api/", "/");

            try {
                return new URI(
                        uri.getScheme(),
                        uri.getUserInfo(),
                        normalizedHost,
                        uri.getPort(),
                        normalizedPath,
                        uri.getRawQuery(),
                        uri.getRawFragment());
            } catch (URISyntaxException ex) {
                log.warn("Falha ao sanitizar URI de saída da Conta Azul. Mantendo URI original: {}", uri, ex);
                return uri;
            }
        }
    }
}
