package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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

        URI externalUri = URI.create(url);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(externalUri)
                .header("Authorization", authorizationHeader)
                .header("Accept", "application/json")
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error(
                        "Conta Azul retornou {} ao consultar URI {}. Corpo do erro: {}",
                        response.statusCode(),
                        url,
                        response.body());
                throw new ContaAzulHttpException(response.statusCode(), response.body());
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
     * Baixa um arquivo sem autenticação explícita.
     */
    public byte[] downloadFile(String url) {
        if (!StringUtils.hasText(url)) {
            return new byte[0];
        }

        String sanitizedUri = sanitizeContaAzulUri(url.trim());
        URI externalUri = URI.create(sanitizedUri);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(externalUri)
                .GET()
                .build();

        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ContaAzulHttpException(response.statusCode(), toUtf8String(response.body()));
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
        URI externalUri = URI.create(sanitizedUri);

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
                throw new ContaAzulHttpException(response.statusCode(), toUtf8String(response.body()));
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
        sanitized = sanitized.replace("https://api-v2.contaazul.com/api/v1/", "https://api-v2.contaazul.com/v1/");
        sanitized = sanitized.replace("https://api.contaazul.com/api/v1/", "https://api-v2.contaazul.com/v1/");
        sanitized = sanitized.replace("api.contaazul.com", "api-v2.contaazul.com");
        return sanitized;
    }
}
