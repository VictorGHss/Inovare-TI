package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

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

    @Value("${contaazul.redirect-uri}")
    private String contaAzulRedirectUri;

    public String buildAuthorizationUrl(String redirectUri) {
        String resolvedRedirectUri = StringUtils.hasText(redirectUri) ? redirectUri : contaAzulRedirectUri;
        String state = UUID.randomUUID().toString();
        
        log.debug("Construindo URL de autorização da Conta Azul. Redirect URI: {}", resolvedRedirectUri);
        
        String authorizationUrl = UriComponentsBuilder
            .fromUriString(contaAzulAuthorizationUrl)
                .queryParam("response_type", "code")
                .queryParam("client_id", contaAzulClientId)
                .queryParam("redirect_uri", resolvedRedirectUri)
                .queryParam("state", state)
                .build()
                .encode()
                .toUriString();
        
        log.debug("Authorization URL constructed: {}", authorizationUrl);
        return authorizationUrl;
    }

    public void exchangeAuthorizationCode(String code, String redirectUri) {
        ContaAzulTokenResponse response = requestTokenByAuthorizationCode(code, redirectUri);
        persistToken(response);
        log.info("ContaAzul OAuth callback processed successfully. Token type: {}", response.tokenType());
    }

    public String getValidAccessToken() {
        ContaAzulOAuthToken token = getValidTokenFromDatabase();
        return token.getAccessToken();
    }

    public ContaAzulOAuthToken getValidTokenFromDatabase() {
        ContaAzulOAuthToken token = tokenRepository.findTopByOrderByUpdatedAtDesc()
                .orElseThrow(() -> new IllegalStateException("ContaAzul token not initialized. Complete OAuth2 authorization first."));

        if (isExpiringSoon(token)) {
            token = refreshAndPersist(token);
            token = reloadTokenFromDatabase(token.getId());
        }

        long minutesLeft = Duration.between(LocalDateTime.now(), token.getExpiresAt()).toMinutes();
        log.info("Token válido por mais {} minutos", minutesLeft);

        return token;
    }

    public String forceRefresh() {
        ContaAzulOAuthToken token = tokenRepository.findTopByOrderByUpdatedAtDesc()
                .orElseThrow(() -> new IllegalStateException("ContaAzul token not initialized. Complete OAuth2 authorization first."));

        ContaAzulOAuthToken refreshed = refreshAndPersist(token);
        long minutesLeft = Duration.between(LocalDateTime.now(), refreshed.getExpiresAt()).toMinutes();
        log.info("Token renovado manualmente. Token válido por mais {} minutos", minutesLeft);

        return refreshed.getAccessToken();
    }

    public ContaAzulOAuthToken forceRefreshAndReloadFromDatabase() {
        ContaAzulOAuthToken token = tokenRepository.findTopByOrderByUpdatedAtDesc()
                .orElseThrow(() -> new IllegalStateException("ContaAzul token not initialized. Complete OAuth2 authorization first."));

        ContaAzulOAuthToken refreshed = refreshAndPersist(token);
        ContaAzulOAuthToken reloaded = reloadTokenFromDatabase(refreshed.getId());
        long minutesLeft = Duration.between(LocalDateTime.now(), reloaded.getExpiresAt()).toMinutes();
        log.info("Token renovado manualmente com recarga no banco. Token válido por mais {} minutos", minutesLeft);

        return reloaded;
    }

    public boolean hasAuthorizedToken() {
        return tokenRepository.findTopByOrderByUpdatedAtDesc()
                .map(token -> StringUtils.hasText(token.getAccessToken()))
                .orElse(false);
    }

    public AuthorizationStatus getAuthorizationStatus() {
        return tokenRepository.findTopByOrderByUpdatedAtDesc()
                .map(token -> new AuthorizationStatus(
                        StringUtils.hasText(token.getAccessToken()),
                        token.getExpiresAt(),
                        token.getRefreshedAt()))
                .orElseGet(() -> new AuthorizationStatus(false, null, null));
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
        ContaAzulOAuthToken saved = tokenRepository.save(token);
        log.info(
            "Token salvo. expires_at={}, access_token_preview={}",
            saved.getExpiresAt(),
            previewToken(saved.getAccessToken()));
        return saved;
    }

    private ContaAzulTokenResponse requestTokenByAuthorizationCode(String code, String redirectUri) {
        String resolvedRedirectUri = StringUtils.hasText(redirectUri) ? redirectUri : contaAzulRedirectUri;
        MultiValueMap<String, String> payload = new LinkedMultiValueMap<>();
        payload.add("grant_type", "authorization_code");
        payload.add("code", code);
        payload.add("client_id", contaAzulClientId);
        payload.add("client_secret", contaAzulClientSecret);
        payload.add("redirect_uri", resolvedRedirectUri);

        try {
            return postTokenRequest(payload);
        } catch (IllegalStateException ex) {
            log.error("Erro ao obter token de acesso da Conta Azul. Verifique se o code está válido e se o app está corretamente configurado no portal do desenvolvedor.", ex);
            throw ex;
        }
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

        ContaAzulOAuthToken saved = tokenRepository.save(token);
        log.info(
            "Token salvo. expires_at={}, access_token_preview={}",
            saved.getExpiresAt(),
            previewToken(saved.getAccessToken()));
    }

    private long resolveExpiresIn(Long expiresIn) {
        if (expiresIn == null || expiresIn <= 0) {
            log.warn("ContaAzul token response returned expires_in inválido ({}). Aplicando fallback de 3600 segundos.",
                    expiresIn);
        }
        return expiresIn != null && expiresIn > 0 ? expiresIn : 3600L;
    }

    private String previewToken(String accessToken) {
        if (!StringUtils.hasText(accessToken)) {
            return "n/a";
        }

        return accessToken.length() <= 10 ? accessToken + "..." : accessToken.substring(0, 10) + "...";
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

    private ContaAzulOAuthToken reloadTokenFromDatabase(UUID tokenId) {
        return tokenRepository.findById(tokenId)
                .or(() -> tokenRepository.findTopByOrderByUpdatedAtDesc())
                .orElseThrow(() -> new IllegalStateException("ContaAzul token not found after refresh operation."));
    }

    private record ContaAzulTokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("refresh_token") String refreshToken,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("scope") String scope,
            @JsonProperty("expires_in") Long expiresIn) {
    }

    public record AuthorizationStatus(
            boolean authorized,
            LocalDateTime expiresAt,
            LocalDateTime refreshedAt) {
    }
}
