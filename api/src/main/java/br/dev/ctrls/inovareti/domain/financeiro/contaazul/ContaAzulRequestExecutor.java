package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.github.resilience4j.retry.annotation.Retry;

/**
 * Executor HTTP centralizado para chamadas à Conta Azul.
 *
 * Centraliza:
 * - montagem de requisições JSON com Bearer token;
 * - refresh automático de token quando ocorrer 401;
 * - download de binários (com e sem autenticação);
 * - sanitização de URL e logs sensíveis de autorização.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContaAzulRequestExecutor {

    private final ContaAzulTokenService contaAzulTokenService;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    /**
     * Executa GET JSON com refresh automático e retorna apenas o corpo.
     */
    public String executeJsonGetWithRefresh(String uri) {
        return executeJsonGetResponseWithRefresh(uri).body();
    }

    /**
     * Executa GET JSON com refresh automático e retorna a resposta completa.
     */
    public HttpResponse<String> executeJsonGetResponseWithRefresh(String uri) {
        ContaAzulOAuthToken token = contaAzulTokenService.getValidTokenFromDatabase();

        try {
            return executeJsonGetResponse(uri, token);
        } catch (ContaAzulHttpException ex) {
            if (!ex.isStatus(401)) {
                throw ex;
            }
            log.warn("Token expirado ao consultar endpoint JSON da Conta Azul. Tentando refresh.");
            token = contaAzulTokenService.forceRefreshAndReloadFromDatabase();
            return executeJsonGetResponse(uri, token);
        }
    }

    /**
     * Executa GET JSON usando explicitamente o token informado.
     */
    @Retry(name = "contaAzulRetry", fallbackMethod = "fallbackExecuteJsonGetResponse")
    public HttpResponse<String> executeJsonGetResponse(String uri, ContaAzulOAuthToken token) {
        String url = sanitizeContaAzulUri(uri);
        String authorizationHeader = "Bearer " + token.getAccessToken();
        String sanitizedAuthorizationHeader = sanitizeAuthorizationHeader(authorizationHeader);

        log.info("ContaAzul external request URI (JSON): {}", url);
        log.debug(
                "Enviando requisição para {} com Token iniciado em {}...",
                url,
                sanitizeTokenPrefix(token.getAccessToken()));
        log.trace("Header Authorization sanitizado enviado: {}", sanitizedAuthorizationHeader);

        // Validação final em nível de URI para impedir envio real com prefixo /api.
        URI externalUri = buildContaAzulUri(url);
        log.info("ContaAzul external request path final: host={} path={}", externalUri.getHost(), externalUri.getPath());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(externalUri)
                .header("Authorization", authorizationHeader)
                .header("Accept", "application/json")
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                if (response.statusCode() == 403) {
                    log.warn(
                            "Conta Azul retornou {} ao consultar URI {}. Corpo do erro: {}",
                            response.statusCode(),
                            url,
                            response.body());
                } else {
                    log.error(
                            "Conta Azul retornou {} ao consultar URI {}. Corpo do erro: {}",
                            response.statusCode(),
                            url,
                            response.body());
                }
                throw new ContaAzulHttpException(response.statusCode(), response.body(), externalUri.toString());
            }
            return response;
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Falha ao consultar endpoint JSON da Conta Azul.", ex);
        }
    }

    /**
     * Fallback para falha ao consultar endpoint JSON da Conta Azul.
     * Retorna fallback seguro (resposta simulada) e registra a intenção de sincronização offline.
     */
    public HttpResponse<String> fallbackExecuteJsonGetResponse(String uri, ContaAzulOAuthToken token, Throwable t) {
        log.warn("[OFFLINE-SYNC-INTENT] [CONTAAZUL] Falha crítica de comunicação com a Conta Azul após retentativas. URI: {}. Erro: {}. Gravando intenção de sincronização offline posterior.", uri, t.getMessage());
        
        return new HttpResponse<String>() {
            @Override
            public int statusCode() { return 503; }
            @Override
            public HttpRequest request() { return null; }
            @Override
            public java.util.Optional<HttpResponse<String>> previousResponse() { return java.util.Optional.empty(); }
            @Override
            public java.net.http.HttpHeaders headers() { return java.net.http.HttpHeaders.of(java.util.Map.of(), (k, v) -> true); }
            @Override
            public String body() { return "{\"status\":\"offline-queued\",\"error\":\"" + t.getMessage() + "\"}"; }
            @Override
            public java.util.Optional<javax.net.ssl.SSLSession> sslSession() { return java.util.Optional.empty(); }
            @Override
            public URI uri() { return URI.create(uri); }
            @Override
            public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
        };
    }

    /**
     * Baixa um arquivo sem autenticação explícita.
     */
    public byte[] downloadFile(String url) {
        if (!StringUtils.hasText(url)) {
            return new byte[0];
        }

        String sanitizedUri = sanitizeContaAzulUri(url.trim());
        URI externalUri = buildContaAzulUri(sanitizedUri);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(externalUri)
                .GET()
                .build();

        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ContaAzulHttpException(response.statusCode(), toUtf8String(response.body()), externalUri.toString());
            }
            return response.body() != null ? response.body() : new byte[0];
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Falha ao baixar arquivo da Conta Azul.", ex);
        }
    }

    /**
     * Baixa um arquivo adicionando token Bearer quando informado.
     */
    public byte[] downloadFile(String url, String bearerToken) {
        if (!StringUtils.hasText(url)) {
            return new byte[0];
        }

        String sanitizedUri = sanitizeContaAzulUri(url.trim());
        URI externalUri = buildContaAzulUri(sanitizedUri);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(externalUri)
                .GET();

        if (StringUtils.hasText(bearerToken)) {
            builder.header("Authorization", "Bearer " + bearerToken.trim());
        }

        HttpRequest request = builder.build();

        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ContaAzulHttpException(response.statusCode(), toUtf8String(response.body()), externalUri.toString());
            }
            return response.body() != null ? response.body() : new byte[0];
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Falha ao baixar arquivo da Conta Azul.", ex);
        }
    }

    /**
     * Baixa arquivo público (sem autenticação).
     */
    public byte[] downloadPublicFile(String url) {
        return downloadFile(url);
    }

    private String toUtf8String(byte[] body) {
        if (body == null || body.length == 0) {
            return "";
        }
        return new String(body, StandardCharsets.UTF_8);
    }

    /**
     * Retorna prefixo curto do token para logs seguros.
     */
    private String sanitizeTokenPrefix(String accessToken) {
        if (!StringUtils.hasText(accessToken)) {
            return "n/a";
        }

        String normalized = accessToken.trim();
        return normalized.length() <= 4 ? normalized : normalized.substring(0, 4);
    }

    /**
     * Sanitiza header Authorization para evitar exposição de segredo em log.
     */
    private String sanitizeAuthorizationHeader(String authorizationHeader) {
        if (!StringUtils.hasText(authorizationHeader)) {
            return "Authorization: n/a";
        }

        String value = authorizationHeader.trim();
        if (!value.startsWith("Bearer ")) {
            return "Authorization: [formato inválido]";
        }

        String token = value.substring("Bearer ".length());
        if (!StringUtils.hasText(token)) {
            return "Authorization: Bearer [vazio]";
        }

        String normalizedToken = token.trim();
        if (normalizedToken.length() <= 8) {
            return "Authorization: Bearer " + normalizedToken;
        }

        String start = normalizedToken.substring(0, 4);
        String end = normalizedToken.substring(normalizedToken.length() - 4);
        return "Authorization: Bearer " + start + "..." + end;
    }

    /**
     * Normaliza URLs legadas para host/api suportados pela Conta Azul.
     */
    private String sanitizeContaAzulUri(String uri) {
        if (!StringUtils.hasText(uri)) {
            return uri;
        }

        String sanitized = uri.trim();

        // Garante host oficial atual da Conta Azul para as integrações da API v2.
        sanitized = sanitized.replace("https://api-v2.contaazul.com/api/v1/", "https://api-v2.contaazul.com/v1/");
        sanitized = sanitized.replace("https://api.contaazul.com/api/v1/", "https://api-v2.contaazul.com/v1/");
        sanitized = sanitized.replace("api.contaazul.com", "api-v2.contaazul.com");

        // Remove prefixo /api legado em qualquer variação de caixa, mantendo path final como /v1/...
        sanitized = sanitized.replaceAll("(?i)/api/v1/", "/v1/");

        // Corrige casos raros onde o endpoint venha como /api/... sem versão explícita.
        sanitized = sanitized.replaceAll("(?i)https://api-v2\\.contaazul\\.com/api/", "https://api-v2.contaazul.com/");
        return sanitized;
    }

    private URI buildContaAzulUri(String sanitizedUrl) {
        URI candidate = URI.create(sanitizedUrl);

        if (!isContaAzulHost(candidate.getHost()) || !hasLegacyApiPrefix(candidate.getPath())) {
            return candidate;
        }

        String normalizedPath = candidate.getPath()
                .replaceFirst("(?i)^/api/v1/", "/v1/")
                .replaceFirst("(?i)^/api/", "/");

        try {
            URI normalized = new URI(
                    candidate.getScheme(),
                    candidate.getUserInfo(),
                    candidate.getHost(),
                    candidate.getPort(),
                    normalizedPath,
                    candidate.getRawQuery(),
                    candidate.getRawFragment());
            log.warn("URI da Conta Azul foi corrigida em nível final para remover /api: original={} normalized={}", candidate, normalized);
            return normalized;
        } catch (URISyntaxException ex) {
            log.warn("Falha ao corrigir URI final da Conta Azul. Usando URI sanitizada por string: {}", sanitizedUrl, ex);
            return candidate;
        }
    }

    private boolean isContaAzulHost(String host) {
        return StringUtils.hasText(host) && host.toLowerCase().contains("contaazul.com");
    }

    private boolean hasLegacyApiPrefix(String path) {
        return StringUtils.hasText(path) && path.toLowerCase().startsWith("/api/");
    }
}
