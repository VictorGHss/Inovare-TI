package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

import java.net.URI;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContaAzulTokenService {

    private final RestTemplate restTemplate;
    private final ContaAzulOAuthTokenRepository tokenRepository;

    @Value("${app.contaazul.client-id}")
    private String contaAzulClientId;

    @Value("${app.contaazul.client-secret}")
    private String contaAzulClientSecret;

    @Value("${app.contaazul.authorization-url}")
    private String contaAzulAuthorizationUrl;

    @Value("${app.contaazul.token-url}")
    private String contaAzulTokenUrl;

    public String buildAuthorizationUrl(String redirectUri) {
        return UriComponentsBuilder
            .fromUriString(contaAzulAuthorizationUrl)
                .queryParam("response_type", "code")
                .queryParam("client_id", contaAzulClientId)
                .queryParam("redirect_uri", redirectUri)
                .build(true)
                .toUriString();
    }

    public String resolveRedirectUri(HttpServletRequest request) {
        String forwardedHost = request.getHeader("X-Forwarded-Host");
        String host = StringUtils.hasText(forwardedHost) ? forwardedHost : request.getServerName();

        boolean isDevelopmentHost = host != null
                && (host.contains("localhost") || host.contains("127.0.0.1"));

        if (isDevelopmentHost) {
            return "http://localhost:8085/api/financeiro/contaazul/callback";
        }

        return "https://itsm-inovare.ctrls.dev.br/api/financeiro/contaazul/callback";
    }

    public void exchangeAuthorizationCode(String code, String redirectUri) {
        ContaAzulTokenResponse response = requestTokenByAuthorizationCode(code, redirectUri);
        persistToken(response);
        log.info("ContaAzul OAuth callback processed and token persisted successfully.");
    }

    public String getValidAccessToken() {
        ContaAzulOAuthToken token = tokenRepository.findTopByOrderByUpdatedAtDesc()
                .orElseThrow(() -> new IllegalStateException("ContaAzul token not initialized. Complete OAuth2 authorization first."));

        if (isExpiringSoon(token)) {
            token = refreshAndPersist(token);
        }

        return token.getAccessToken();
    }

    @Scheduled(fixedDelay = 3_000_000L, initialDelay = 300_000L)
    public void refreshTokenProactively() {
        tokenRepository.findTopByOrderByUpdatedAtDesc().ifPresentOrElse(
                this::refreshExistingToken,
                () -> log.debug("ContaAzul token refresh skipped: no token available yet."));
    }

    private void refreshExistingToken(ContaAzulOAuthToken token) {
        try {
            refreshAndPersist(token);
            log.info("ContaAzul token refreshed successfully.");
        } catch (RestClientException | IllegalStateException ex) {
            log.error("ContaAzul proactive token refresh failed.", ex);
        }
    }

    private ContaAzulOAuthToken refreshAndPersist(ContaAzulOAuthToken token) {
        ContaAzulTokenResponse response = requestTokenByRefreshToken(token.getRefreshToken());

        if (!StringUtils.hasText(response.refreshToken())) {
            throw new IllegalStateException("ContaAzul refresh response did not provide a new refresh_token.");
        }

        token.setAccessToken(response.accessToken());
        token.setRefreshToken(response.refreshToken());
        token.setTokenType(resolveTokenType(response.tokenType()));
        token.setScope(response.scope());
        token.setExpiresAt(LocalDateTime.now().plusSeconds(resolveExpiresIn(response.expiresIn())));
        token.setRefreshedAt(LocalDateTime.now());
        return tokenRepository.save(token);
    }

    private ContaAzulTokenResponse requestTokenByAuthorizationCode(String code, String redirectUri) {
        MultiValueMap<String, String> payload = new LinkedMultiValueMap<>();
        payload.add("grant_type", "authorization_code");
        payload.add("code", code);
        payload.add("client_id", contaAzulClientId);
        payload.add("client_secret", contaAzulClientSecret);
        payload.add("redirect_uri", redirectUri);

        return postTokenRequest(payload);
    }

    private ContaAzulTokenResponse requestTokenByRefreshToken(String refreshToken) {
        MultiValueMap<String, String> payload = new LinkedMultiValueMap<>();
        payload.add("grant_type", "refresh_token");
        payload.add("refresh_token", refreshToken);
        payload.add("client_id", contaAzulClientId);
        payload.add("client_secret", contaAzulClientSecret);

        return postTokenRequest(payload);
    }

    private ContaAzulTokenResponse postTokenRequest(MultiValueMap<String, String> payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        ContaAzulTokenResponse response = restTemplate.postForObject(
                URI.create(contaAzulTokenUrl),
                new HttpEntity<>(payload, headers),
                ContaAzulTokenResponse.class);

        if (response == null || !StringUtils.hasText(response.accessToken()) || !StringUtils.hasText(response.refreshToken())) {
            throw new IllegalStateException("Invalid ContaAzul token response.");
        }

        return response;
    }

    private void persistToken(ContaAzulTokenResponse response) {
        ContaAzulOAuthToken token = tokenRepository.findTopByOrderByUpdatedAtDesc()
                .orElseGet(ContaAzulOAuthToken::new);

        token.setAccessToken(response.accessToken());
        token.setRefreshToken(response.refreshToken());
        token.setTokenType(resolveTokenType(response.tokenType()));
        token.setScope(response.scope());
        token.setExpiresAt(LocalDateTime.now().plusSeconds(resolveExpiresIn(response.expiresIn())));
        token.setRefreshedAt(LocalDateTime.now());

        tokenRepository.save(token);
    }

    private long resolveExpiresIn(Long expiresIn) {
        return expiresIn != null && expiresIn > 0 ? expiresIn : 3600L;
    }

    private String resolveTokenType(String tokenType) {
        return StringUtils.hasText(tokenType) ? tokenType : "Bearer";
    }

    private boolean isExpiringSoon(ContaAzulOAuthToken token) {
        LocalDateTime expiresAt = token.getExpiresAt();
        if (expiresAt == null) {
            return true;
        }

        return expiresAt.isBefore(LocalDateTime.now().plusMinutes(5));
    }

    private record ContaAzulTokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("refresh_token") String refreshToken,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("scope") String scope,
            @JsonProperty("expires_in") Long expiresIn) {
    }
}
