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

/**
 * Serviço de gerenciamento do token OAuth2 da integração com Conta Azul.
 *
 * Fornece helpers para construir a URL de autorização, trocar o código de
 * autorização por token, recuperar token válido do banco, forçar refresh
 * manual e expor status de autorização. Possui um agendador que tenta
 * renovar o token proativamente.
 */
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

    /**
     * Constrói a URL de autorização onde o usuário deve ser redirecionado
     * para conceder permissões ao aplicativo Conta Azul.
     *
     * @param redirectUri URL de redirecionamento opcional; se vazia, usa a
     *                    configuração padrão {@code contaAzulRedirectUri}
     * @return URL completa de autorização
     */
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
        
        log.debug("URL de autorização construída: {}", authorizationUrl);
        return authorizationUrl;
    }

    /**
     * Troca o authorization code recebido no callback OAuth por tokens e
     * persiste o resultado no repositório.
     *
     * @param code código de autorização recebido do provedor
     * @param redirectUri redirect URI utilizado na autorização
     */
    public void exchangeAuthorizationCode(String code, String redirectUri) {
        ContaAzulTokenResponse response = requestTokenByAuthorizationCode(code, redirectUri);
        persistToken(response);
        log.info("Callback OAuth da ContaAzul processado com sucesso. Tipo de token: {}", response.tokenType());
    }

    /**
     * Recupera um access token válido do banco (faz refresh se necessário).
     *
     * @return access token pronto para uso em chamadas à API externa
     */
    public String getValidAccessToken() {
        ContaAzulOAuthToken token = getValidTokenFromDatabase();
        return token.getAccessToken();
    }

    /**
     * Obtém do repositório o registro de token mais recente e garante que
     * seu valor esteja válido para uso. Se o token estiver expirando em breve
     * será executado um refresh e o token recarregado do banco.
     *
     * @return entidade {@link ContaAzulOAuthToken} atualizada e válida
     * @throws IllegalStateException quando nenhum token estiver persistido
     */
    public ContaAzulOAuthToken getValidTokenFromDatabase() {
        ContaAzulOAuthToken token = tokenRepository.findTopByOrderByUpdatedAtDesc()
            .orElseThrow(() -> new IllegalStateException("Token da ContaAzul não inicializado. Complete a autorização OAuth2 primeiro."));

        if (isExpiringSoon(token)) {
            token = refreshAndPersist(token);
            token = reloadTokenFromDatabase(token.getId());
        }

        long minutesLeft = Duration.between(LocalDateTime.now(), token.getExpiresAt()).toMinutes();
        log.info("Token válido por mais {} minutos", minutesLeft);

        return token;
    }
    /**
     * Força a renovação do token armazenado e retorna o novo access token.
     * Utilizado por endpoints administrativos para refresh manual.
     *
     * @return novo access token
     */
    public String forceRefresh() {
        ContaAzulOAuthToken token = tokenRepository.findTopByOrderByUpdatedAtDesc()
            .orElseThrow(() -> new IllegalStateException("Token da ContaAzul não inicializado. Complete a autorização OAuth2 primeiro."));

        ContaAzulOAuthToken refreshed = refreshAndPersist(token);
        long minutesLeft = Duration.between(LocalDateTime.now(), refreshed.getExpiresAt()).toMinutes();
        log.info("Token renovado manualmente. Token válido por mais {} minutos", minutesLeft);

        return refreshed.getAccessToken();
    }
    /**
     * Força renovação do token e recarrega a entidade atualizada do banco.
     * Útil quando o chamador precisa do objeto persistido atualizado (com
     * campos calculados) em vez de apenas do valor do token.
     *
     * @return token reloaded do banco após operação de refresh
     */
    public ContaAzulOAuthToken forceRefreshAndReloadFromDatabase() {
        ContaAzulOAuthToken token = tokenRepository.findTopByOrderByUpdatedAtDesc()
            .orElseThrow(() -> new IllegalStateException("Token da ContaAzul não inicializado. Complete a autorização OAuth2 primeiro."));

        ContaAzulOAuthToken refreshed = refreshAndPersist(token);
        ContaAzulOAuthToken reloaded = reloadTokenFromDatabase(refreshed.getId());
        long minutesLeft = Duration.between(LocalDateTime.now(), reloaded.getExpiresAt()).toMinutes();
        log.info("Token renovado manualmente com recarga no banco. Token válido por mais {} minutos", minutesLeft);

        return reloaded;
    }
    /**
     * Verifica se já existe um token autorizado persistido e com access token
     * não vazio.
     *
     * @return {@code true} se existir token autorizado armazenado, {@code false} caso contrário
     */
    public boolean hasAuthorizedToken() {
        return tokenRepository.findTopByOrderByUpdatedAtDesc()
                .map(token -> StringUtils.hasText(token.getAccessToken()))
                .orElse(false);
    }

    /**
     * Verifica se existe ao menos um registro persistido na tabela de tokens OAuth.
     *
     * @return {@code true} quando há token persistido, {@code false} quando a tabela está vazia
     */
    public boolean hasPersistedTokenRecord() {
        return tokenRepository.count() > 0;
    }

    /**
     * Retorna o status de autorização atual contendo indicador se existe um
     * access token, data de expiração e data do último refresh.
     *
     * @return {@link AuthorizationStatus} descrevendo o estado atual da autorização
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
     * Agendador que executa periodicamente uma verificação para renovar o
     * token caso necessário. Executado em intervalo configurado para evitar
     * expiração inesperada.
     */
    @Scheduled(fixedDelay = 3_000_000L, initialDelay = 300_000L)
    public void refreshTokenProactively() {
        tokenRepository.findTopByOrderByUpdatedAtDesc().ifPresentOrElse(
                this::refreshExistingToken,
                () -> log.debug("Refresh de token ContaAzul ignorado: nenhum token disponível ainda."));
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
     * campos da entidade e persiste no repositório.
     *
     * @param token entidade contendo o refresh token atual
     * @return entidade salva atualizada
     */
    private ContaAzulOAuthToken refreshAndPersist(ContaAzulOAuthToken token) {
        ContaAzulTokenResponse response = requestTokenByRefreshToken(token.getRefreshToken());

        if (!StringUtils.hasText(response.refreshToken())) {
            throw new IllegalStateException("Resposta de refresh da ContaAzul não forneceu um novo refresh_token.");
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
            throw new IllegalStateException("Resposta de token da ContaAzul inválida.");
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
                .orElseThrow(() -> new IllegalStateException("Token da ContaAzul não encontrado após operação de refresh."));
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
