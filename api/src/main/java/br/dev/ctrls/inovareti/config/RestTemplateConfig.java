package br.dev.ctrls.inovareti.config;

import java.net.URI;
import java.net.URISyntaxException;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.support.HttpRequestWrapper;
import org.springframework.web.client.RestTemplate;

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
