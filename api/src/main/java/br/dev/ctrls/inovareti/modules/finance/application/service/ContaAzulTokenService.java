package br.dev.ctrls.inovareti.modules.finance.application.service;

import io.micrometer.observation.annotation.Observed;

import br.dev.ctrls.inovareti.modules.finance.domain.model.ContaAzulOAuthToken;
import br.dev.ctrls.inovareti.modules.finance.domain.port.ContaAzulOAuthTokenRepository;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import br.dev.ctrls.inovareti.modules.finance.infrastructure.config.ContaAzulProperties;
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

/**
 * ServiÃ§o de gerenciamento do token OAuth2 da integraÃ§Ã£o com Conta Azul.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Observed
public class ContaAzulTokenService {

    private final RestTemplate restTemplate;
    private final ContaAzulOAuthTokenRepository tokenRepository;
    private final ContaAzulProperties properties;
    private final java.util.concurrent.locks.ReentrantLock tokenLock = new java.util.concurrent.locks.ReentrantLock();

    public String buildAuthorizationUrl(String redirectUri) {
        String resolvedRedirectUri = StringUtils.hasText(redirectUri) ? redirectUri : properties.getRedirectUri();
        String state = UUID.randomUUID().toString();
        
        log.debug("Construindo URL de autorizaÃ§Ã£o da Conta Azul. Redirect URI: {}", resolvedRedirectUri);
        
        String authorizationUrl = UriComponentsBuilder
            .fromUriString(properties.getAuthorizationUrl())
                .queryParam("response_type", "code")
                .queryParam("client_id", properties.getClientId())
                .queryParam("redirect_uri", resolvedRedirectUri)
                .queryParam("state", state)
                .build()
                .encode()
                .toUriString();
        
        log.debug("URL de autorizaÃ§Ã£o construÃ­da: {}", authorizationUrl);
        return authorizationUrl;
    }

    public void exchangeAuthorizationCode(String code, String redirectUri) {
        ContaAzulTokenResponse response = requestTokenByAuthorizationCode(code, redirectUri);
        persistToken(response);
        log.info("Callback OAuth da ContaAzul processado com sucesso. Tipo de token: {}", response.tokenType());
    }

    public String getValidAccessToken() {
        ContaAzulOAuthToken token = getValidTokenFromDatabase();
        return token.getAccessToken();
    }

    public ContaAzulOAuthToken getValidTokenFromDatabase() {
        ContaAzulOAuthToken token = tokenRepository.findTopByOrderByUpdatedAtDesc()
            .orElseThrow(() -> new IllegalStateException("Token da ContaAzul nÃ£o inicializado. Complete a autorizaÃ§Ã£o OAuth2 primeiro."));

        if (isExpiringSoon(token)) {
            tokenLock.lock();
            try {
                token = tokenRepository.findTopByOrderByUpdatedAtDesc()
                    .orElseThrow(() -> new IllegalStateException("Token da ContaAzul nÃ£o inicializado."));

                if (isExpiringSoon(token)) {
                    log.info("Token expirando em breve. Iniciando renovaÃ§Ã£o via refresh_token da ContaAzul.");
                    token = refreshAndPersist(token);
                    token = reloadTokenFromDatabase(token.getId());
                } else {
                    log.info("Token jÃ¡ foi renovado por outra execuÃ§Ã£o paralela. Pulando refresh.");
                }
            } finally {
                tokenLock.unlock();
            }
        }

        long minutesLeft = Duration.between(LocalDateTime.now(), token.getExpiresAt()).toMinutes();
        log.info("Token vÃ¡lido por mais {} minutos", minutesLeft);
        return token;
    }

    public String forceRefresh() {
        ContaAzulOAuthToken token = tokenRepository.findTopByOrderByUpdatedAtDesc()
            .orElseThrow(() -> new IllegalStateException("Token da ContaAzul nÃ£o inicializado."));

        ContaAzulOAuthToken refreshed = refreshAndPersist(token);
        long minutesLeft = Duration.between(LocalDateTime.now(), refreshed.getExpiresAt()).toMinutes();
        log.info("Token renovado manualmente. Token vÃ¡lido por mais {} minutos", minutesLeft);
        return refreshed.getAccessToken();
    }

    public ContaAzulOAuthToken forceRefreshAndReloadFromDatabase() {
        ContaAzulOAuthToken token = tokenRepository.findTopByOrderByUpdatedAtDesc()
            .orElseThrow(() -> new IllegalStateException("Token da ContaAzul nÃ£o inicializado."));

        ContaAzulOAuthToken refreshed = refreshAndPersist(token);
        ContaAzulOAuthToken reloaded = reloadTokenFromDatabase(refreshed.getId());
        long minutesLeft = Duration.between(LocalDateTime.now(), reloaded.getExpiresAt()).toMinutes();
        log.info("Token renovado manualmente com recarga. Token vÃ¡lido por mais {} minutos", minutesLeft);
        return reloaded;
    }

    public boolean hasAuthorizedToken() {
        return tokenRepository.findTopByOrderByUpdatedAtDesc()
                .map(token -> StringUtils.hasText(token.getAccessToken()))
                .orElse(false);
    }

    public boolean hasPersistedTokenRecord() {
        return tokenRepository.count() > 0;
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
                () -> log.debug("Refresh de token ContaAzul ignorado: nenhum token disponÃ­vel ainda."));
    }

    private void refreshExistingToken(ContaAzulOAuthToken token) {
        try {
            refreshAndPersist(token);
            log.info("Token da ContaAzul renovado com sucesso.");
        } catch (RestClientException | IllegalStateException ex) {
            log.error("Falha ao renovar proativamente token da ContaAzul.", ex);
        }
    }

    private ContaAzulOAuthToken refreshAndPersist(ContaAzulOAuthToken token) {
        ContaAzulTokenResponse response = requestTokenByRefreshToken(token.getRefreshToken());
        if (!StringUtils.hasText(response.refreshToken())) {
            throw new IllegalStateException("Resposta de refresh da ContaAzul nÃ£o forneceu um novo refresh_token.");
        }
        updateTokenFromResponse(token, response);
        ContaAzulOAuthToken saved = tokenRepository.save(token);
        log.info("Token salvo. expires_at={}, access_token_preview={}", saved.getExpiresAt(), previewToken(saved.getAccessToken()));
        return saved;
    }

    private ContaAzulTokenResponse requestTokenByAuthorizationCode(String code, String redirectUri) {
        String resolvedRedirectUri = StringUtils.hasText(redirectUri) ? redirectUri : properties.getRedirectUri();
        MultiValueMap<String, String> payload = new LinkedMultiValueMap<>();
        payload.add("grant_type", "authorization_code");
        payload.add("code", code);
        payload.add("client_id", properties.getClientId());
        payload.add("client_secret", properties.getClientSecret());
        payload.add("redirect_uri", resolvedRedirectUri);

        try {
            return postTokenRequest(payload);
        } catch (IllegalStateException ex) {
            log.error("Erro ao obter token de acesso da Conta Azul. Verifique as configuraÃ§Ãµes.", ex);
            throw ex;
        }
    }

    private ContaAzulTokenResponse requestTokenByRefreshToken(String refreshToken) {
        MultiValueMap<String, String> payload = new LinkedMultiValueMap<>();
        payload.add("grant_type", "refresh_token");
        payload.add("refresh_token", refreshToken);
        payload.add("client_id", properties.getClientId());
        payload.add("client_secret", properties.getClientSecret());
        return postTokenRequest(payload);
    }

    private ContaAzulTokenResponse postTokenRequest(MultiValueMap<String, String> payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        ContaAzulTokenResponse response = restTemplate.postForObject(
                URI.create(properties.getTokenUrl()),
                new HttpEntity<>(payload, headers),
                ContaAzulTokenResponse.class);

        if (response == null || !StringUtils.hasText(response.accessToken()) || !StringUtils.hasText(response.refreshToken())) {
            throw new IllegalStateException("Resposta de token da ContaAzul invÃ¡lida.");
        }
        return response;
    }

    private void persistToken(ContaAzulTokenResponse response) {
        ContaAzulOAuthToken token = tokenRepository.findTopByOrderByUpdatedAtDesc()
                .orElseGet(ContaAzulOAuthToken::new);
        updateTokenFromResponse(token, response);
        ContaAzulOAuthToken saved = tokenRepository.save(token);
        log.info("Token salvo. expires_at={}, access_token_preview={}", saved.getExpiresAt(), previewToken(saved.getAccessToken()));
    }

    private void updateTokenFromResponse(ContaAzulOAuthToken token, ContaAzulTokenResponse response) {
        token.setAccessToken(response.accessToken());
        token.setRefreshToken(response.refreshToken());
        token.setTokenType(resolveTokenType(response.tokenType()));
        token.setScope(response.scope());
        token.setExpiresAt(LocalDateTime.now().plusSeconds(resolveExpiresIn(response.expiresIn())));
        token.setRefreshedAt(LocalDateTime.now());
    }

    private long resolveExpiresIn(Long expiresIn) {
        if (expiresIn == null || expiresIn <= 0) {
            log.warn("ContaAzul token response returned expires_in invÃ¡lido ({}). Aplicando fallback.", expiresIn);
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
                .orElseThrow(() -> new IllegalStateException("Token da ContaAzul nÃ£o encontrado apÃ³s refresh."));
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


