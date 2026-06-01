package br.dev.ctrls.inovareti.modules.finance.application.service;

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
 * ServiÃƒÂ§o de gerenciamento do token OAuth2 da integraÃƒÂ§ÃƒÂ£o com Conta Azul.
 *
 * Fornece helpers para construir a URL de autorizaÃƒÂ§ÃƒÂ£o, trocar o cÃƒÂ³digo de
 * autorizaÃƒÂ§ÃƒÂ£o por token, recuperar token vÃƒÂ¡lido do banco, forÃƒÂ§ar refresh
 * manual e expor status de autorizaÃƒÂ§ÃƒÂ£o. Possui um agendador que tenta
 * renovar o token proativamente.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContaAzulTokenService {

    private final RestTemplate restTemplate;
    private final ContaAzulOAuthTokenRepository tokenRepository;
    private final ContaAzulProperties properties;
    private final java.util.concurrent.locks.ReentrantLock tokenLock = new java.util.concurrent.locks.ReentrantLock();











    /**
     * ConstrÃƒÂ³i a URL de autorizaÃƒÂ§ÃƒÂ£o onde o usuÃƒÂ¡rio deve ser redirecionado
     * para conceder permissÃƒÂµes ao aplicativo Conta Azul.
     *
     * @param redirectUri URL de redirecionamento opcional; se vazia, usa a
     *                    configuraÃƒÂ§ÃƒÂ£o padrÃƒÂ£o {@code contaAzulRedirectUri}
     * @return URL completa de autorizaÃƒÂ§ÃƒÂ£o
     */
    public String buildAuthorizationUrl(String redirectUri) {
        String resolvedRedirectUri = StringUtils.hasText(redirectUri) ? redirectUri : properties.getRedirectUri();
        String state = UUID.randomUUID().toString();
        
        log.debug("Construindo URL de autorizaÃƒÂ§ÃƒÂ£o da Conta Azul. Redirect URI: {}", resolvedRedirectUri);
        
        String authorizationUrl = UriComponentsBuilder
            .fromUriString(properties.getAuthorizationUrl())
                .queryParam("response_type", "code")
                .queryParam("client_id", properties.getClientId())
                .queryParam("redirect_uri", resolvedRedirectUri)
                .queryParam("state", state)
                .build()
                .encode()
                .toUriString();
        
        log.debug("URL de autorizaÃƒÂ§ÃƒÂ£o construÃƒÂ­da: {}", authorizationUrl);
        return authorizationUrl;
    }

    /**
     * Troca o authorization code recebido no callback OAuth por tokens e
     * persiste o resultado no repositÃƒÂ³rio.
     *
     * @param code cÃƒÂ³digo de autorizaÃƒÂ§ÃƒÂ£o recebido do provedor
     * @param redirectUri redirect URI utilizado na autorizaÃƒÂ§ÃƒÂ£o
     */
    public void exchangeAuthorizationCode(String code, String redirectUri) {
        ContaAzulTokenResponse response = requestTokenByAuthorizationCode(code, redirectUri);
        persistToken(response);
        log.info("Callback OAuth da ContaAzul processado com sucesso. Tipo de token: {}", response.tokenType());
    }

    /**
     * Recupera um access token vÃƒÂ¡lido do banco (faz refresh se necessÃƒÂ¡rio).
     *
     * @return access token pronto para uso em chamadas ÃƒÂ  API externa
     */
    public String getValidAccessToken() {
        ContaAzulOAuthToken token = getValidTokenFromDatabase();
        return token.getAccessToken();
    }

    /**
     * ObtÃƒÂ©m do repositÃƒÂ³rio o registro de token mais recente e garante que
     * seu valor esteja vÃƒÂ¡lido para uso. Se o token estiver expirando em breve
     * serÃƒÂ¡ executado um refresh e o token recarregado do banco.
     *
     * @return entidade {@link ContaAzulOAuthToken} atualizada e vÃƒÂ¡lida
     * @throws IllegalStateException quando nenhum token estiver persistido
     */
    public ContaAzulOAuthToken getValidTokenFromDatabase() {
        ContaAzulOAuthToken token = tokenRepository.findTopByOrderByUpdatedAtDesc()
            .orElseThrow(() -> new IllegalStateException("Token da ContaAzul nÃƒÂ£o inicializado. Complete a autorizaÃƒÂ§ÃƒÂ£o OAuth2 primeiro."));

        if (isExpiringSoon(token)) {
            tokenLock.lock();
            try {
                // Checagem Dupla (Double-Checked Locking): recarrega o token do banco apÃƒÂ³s obter o lock
                token = tokenRepository.findTopByOrderByUpdatedAtDesc()
                    .orElseThrow(() -> new IllegalStateException("Token da ContaAzul nÃƒÂ£o inicializado. Complete a autorizaÃƒÂ§ÃƒÂ£o OAuth2 primeiro."));

                if (isExpiringSoon(token)) {
                    log.info("Token expirando em breve. Iniciando renovaÃƒÂ§ÃƒÂ£o via refresh_token da ContaAzul.");
                    token = refreshAndPersist(token);
                    token = reloadTokenFromDatabase(token.getId());
                } else {
                    log.info("Token jÃƒÂ¡ foi renovado por outra execuÃƒÂ§ÃƒÂ£o paralela. Pulando refresh redundante.");
                }
            } finally {
                tokenLock.unlock();
            }
        }

        long minutesLeft = Duration.between(LocalDateTime.now(), token.getExpiresAt()).toMinutes();
        log.info("Token vÃƒÂ¡lido por mais {} minutos", minutesLeft);

        return token;
    }
    /**
     * ForÃƒÂ§a a renovaÃƒÂ§ÃƒÂ£o do token armazenado e retorna o novo access token.
     * Utilizado por endpoints administrativos para refresh manual.
     *
     * @return novo access token
     */
    public String forceRefresh() {
        ContaAzulOAuthToken token = tokenRepository.findTopByOrderByUpdatedAtDesc()
            .orElseThrow(() -> new IllegalStateException("Token da ContaAzul nÃƒÂ£o inicializado. Complete a autorizaÃƒÂ§ÃƒÂ£o OAuth2 primeiro."));

        ContaAzulOAuthToken refreshed = refreshAndPersist(token);
        long minutesLeft = Duration.between(LocalDateTime.now(), refreshed.getExpiresAt()).toMinutes();
        log.info("Token renovado manualmente. Token vÃƒÂ¡lido por mais {} minutos", minutesLeft);

        return refreshed.getAccessToken();
    }
    /**
     * ForÃƒÂ§a renovaÃƒÂ§ÃƒÂ£o do token e recarrega a entidade atualizada do banco.
     * ÃƒÅ¡til quando o chamador precisa do objeto persistido atualizado (com
     * campos calculados) em vez de apenas do valor do token.
     *
     * @return token reloaded do banco apÃƒÂ³s operaÃƒÂ§ÃƒÂ£o de refresh
     */
    public ContaAzulOAuthToken forceRefreshAndReloadFromDatabase() {
        ContaAzulOAuthToken token = tokenRepository.findTopByOrderByUpdatedAtDesc()
            .orElseThrow(() -> new IllegalStateException("Token da ContaAzul nÃƒÂ£o inicializado. Complete a autorizaÃƒÂ§ÃƒÂ£o OAuth2 primeiro."));

        ContaAzulOAuthToken refreshed = refreshAndPersist(token);
        ContaAzulOAuthToken reloaded = reloadTokenFromDatabase(refreshed.getId());
        long minutesLeft = Duration.between(LocalDateTime.now(), reloaded.getExpiresAt()).toMinutes();
        log.info("Token renovado manualmente com recarga no banco. Token vÃƒÂ¡lido por mais {} minutos", minutesLeft);

        return reloaded;
    }
    /**
     * Verifica se jÃƒÂ¡ existe um token autorizado persistido e com access token
     * nÃƒÂ£o vazio.
     *
     * @return {@code true} se existir token autorizado armazenado, {@code false} caso contrÃƒÂ¡rio
     */
    public boolean hasAuthorizedToken() {
        return tokenRepository.findTopByOrderByUpdatedAtDesc()
                .map(token -> StringUtils.hasText(token.getAccessToken()))
                .orElse(false);
    }

    /**
     * Verifica se existe ao menos um registro persistido na tabela de tokens OAuth.
     *
     * @return {@code true} quando hÃƒÂ¡ token persistido, {@code false} quando a tabela estÃƒÂ¡ vazia
     */
    public boolean hasPersistedTokenRecord() {
        return tokenRepository.count() > 0;
    }

    /**
     * Retorna o status de autorizaÃƒÂ§ÃƒÂ£o atual contendo indicador se existe um
     * access token, data de expiraÃƒÂ§ÃƒÂ£o e data do ÃƒÂºltimo refresh.
     *
     * @return {@link AuthorizationStatus} descrevendo o estado atual da autorizaÃƒÂ§ÃƒÂ£o
     */
    public AuthorizationStatus getAuthorizationStatus() {
        return tokenRepository.findTopByOrderByUpdatedAtDesc()
                .map(token -> new AuthorizationStatus(
                        StringUtils.hasText(token.getAccessToken()),
                        token.getExpiresAt(),
                        token.getRefreshedAt()))
                .orElseGet(() -> new AuthorizationStatus(false, null, null));
    }
    /**
     * Agendador que executa periodicamente uma verificaÃƒÂ§ÃƒÂ£o para renovar o
     * token caso necessÃƒÂ¡rio. Executado em intervalo configurado para evitar
     * expiraÃƒÂ§ÃƒÂ£o inesperada.
     */
    @Scheduled(fixedDelay = 3_000_000L, initialDelay = 300_000L)
    public void refreshTokenProactively() {
        tokenRepository.findTopByOrderByUpdatedAtDesc().ifPresentOrElse(
                this::refreshExistingToken,
                () -> log.debug("Refresh de token ContaAzul ignorado: nenhum token disponÃƒÂ­vel ainda."));
    }
    /**
     * Tenta renovar o token fornecido e registra logs em caso de falha.
     *
     * @param token token atual a ser renovado
     */
    private void refreshExistingToken(ContaAzulOAuthToken token) {
        try {
            refreshAndPersist(token);
            log.info("Token da ContaAzul renovado com sucesso.");
        } catch (RestClientException | IllegalStateException ex) {
            log.error("Falha ao renovar proativamente token da ContaAzul.", ex);
        }
    }
    /**
     * Executa o fluxo de refresh usando o refresh_token atual, atualiza os
     * campos da entidade e persiste no repositÃƒÂ³rio.
     *
     * @param token entidade contendo o refresh token atual
     * @return entidade salva atualizada
     */
    private ContaAzulOAuthToken refreshAndPersist(ContaAzulOAuthToken token) {
        ContaAzulTokenResponse response = requestTokenByRefreshToken(token.getRefreshToken());

        if (!StringUtils.hasText(response.refreshToken())) {
            throw new IllegalStateException("Resposta de refresh da ContaAzul nÃƒÂ£o forneceu um novo refresh_token.");
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
            log.error("Erro ao obter token de acesso da Conta Azul. Verifique se o code estÃƒÂ¡ vÃƒÂ¡lido e se o app estÃƒÂ¡ corretamente configurado no portal do desenvolvedor.", ex);
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
            throw new IllegalStateException("Resposta de token da ContaAzul invÃƒÂ¡lida.");
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
            log.warn("ContaAzul token response returned expires_in invÃƒÂ¡lido ({}). Aplicando fallback de 3600 segundos.",
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
                .orElseThrow(() -> new IllegalStateException("Token da ContaAzul nÃƒÂ£o encontrado apÃƒÂ³s operaÃƒÂ§ÃƒÂ£o de refresh."));
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

