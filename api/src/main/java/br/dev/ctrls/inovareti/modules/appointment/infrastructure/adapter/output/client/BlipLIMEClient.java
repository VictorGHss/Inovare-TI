package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.UUID;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import jakarta.annotation.PostConstruct;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.BlipClientPort;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.AppointmentMotorProperties;
import org.springframework.retry.annotation.Retryable;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import java.util.concurrent.locks.LockSupport; // Mantido, pois é usado
import lombok.extern.slf4j.Slf4j;

/**
 * Adaptador de saída que implementa a porta BlipClientPort.
 * Faz a ponte com a API LIME do Blip.
 */
@Slf4j
@Component
public class BlipLIMEClient implements BlipClientPort {

    public static final String MASTER_STATE_COMMAND_TO = "postmaster@msging.net";
    public static final String THREAD_TRANSFER_COMMAND_TO = "postmaster@desk.msging.net";

    private final RestTemplate injectedRestTemplate;
    private final AppointmentMotorProperties properties;
    private final AtomicLong lastRequestAt = new AtomicLong(0L);

    private RestTemplate blipRestTemplate;

    public BlipLIMEClient(RestTemplate restTemplate, AppointmentMotorProperties properties) {
        this.injectedRestTemplate = restTemplate;
        this.properties = properties;
    }

    @PostConstruct
    public void setupClient() {
        try {
            // Configura um pool de conexões HTTP persistentes com Keep-Alive.
            // Sem pool, cada Virtual Thread abriria/fecharia um socket TCP com handshake SSL completo
            // (~250ms por request). Com pool, reutiliza conexões existentes reduzindo para ~50ms.
            org.apache.hc.client5.http.config.ConnectionConfig connectionConfig =
                org.apache.hc.client5.http.config.ConnectionConfig.custom()
                    .setConnectTimeout(org.apache.hc.core5.util.Timeout.ofMilliseconds(3000))
                    .build();

            org.apache.hc.client5.http.impl.classic.CloseableHttpClient httpClient =
                org.apache.hc.client5.http.impl.classic.HttpClients.custom()
                    .setConnectionManager(
                        org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder.create()
                            // Máximo de conexões totais no pool — suporta até 100 Virtual Threads simultâneas
                            .setMaxConnTotal(100)
                            // Máximo de conexões por rota (mesmo host) — o Blip usa apenas 1 host
                            .setMaxConnPerRoute(100)
                            .setDefaultConnectionConfig(connectionConfig)
                            .build()
                    )
                    // Configura timeout de conexão e leitura por request
                    .setDefaultRequestConfig(
                        org.apache.hc.client5.http.config.RequestConfig.custom()
                            .setConnectionRequestTimeout(org.apache.hc.core5.util.Timeout.ofMilliseconds(3000))
                            .setResponseTimeout(org.apache.hc.core5.util.Timeout.ofMilliseconds(3000))
                            .build()
                    )
                    .build();

            HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);
            blipRestTemplate = new RestTemplate(requestFactory);
            blipRestTemplate.setMessageConverters(new ArrayList<>(injectedRestTemplate.getMessageConverters()));
            blipRestTemplate.setInterceptors(new ArrayList<>(injectedRestTemplate.getInterceptors()));
            blipRestTemplate.setErrorHandler(injectedRestTemplate.getErrorHandler());
            log.info("Blip RestTemplate configurado com pool de conexões HTTP (max=100, timeout=5s)");
        } catch (Exception ex) {
            log.warn("Falha ao configurar Blip RestTemplate com pool; usando RestTemplate injetado", ex);
            blipRestTemplate = injectedRestTemplate;
        }
    }

    @Override
    public Map<String, Object> getBlipQueues() {
        Map<String, Object> command = Map.of(
            "id", UUID.randomUUID().toString(),
            "to", "postmaster@desk.msging.net",
            "method", "get",
            "uri", "/attendance-queues"
        );
        try {
            return executeCommand(command, AuthorizationScope.ROUTER);
        } catch (RestClientException ex) {
            log.error("Erro ao buscar filas no Blip", ex);
            return Map.of();
        }
    }

    @Override
    public List<BlipQueue> listBlipQueues() {
        Map<String, Object> response = getBlipQueues();
        if (response == null || !response.containsKey("resource")) return List.of();
        
        Object resourceObj = response.get("resource");
        if (resourceObj instanceof Map<?, ?> resourceMap) {
            Object itemsObj = resourceMap.get("items");
            if (itemsObj instanceof List<?> items) {
                List<BlipQueue> queues = new ArrayList<>();
                for (Object item : items) {
                    if (item instanceof Map<?, ?> itemMap) {
                        Object identityObj = itemMap.get("identity");
                        if (identityObj == null) {
                            identityObj = itemMap.get("id");
                        }
                        if (identityObj == null) {
                            identityObj = itemMap.get("name");
                        }
                        String id = identityObj != null ? String.valueOf(identityObj) : "";
                        String name = itemMap.get("name") != null ? String.valueOf(itemMap.get("name")) : "";
                        if (!id.isBlank()) {
                            queues.add(new BlipQueue(id, name));
                        }
                    }
                }
                return queues;
            }
        }
        return List.of();
    }

    @Override
    public void mergeContactExtras(String phoneNumber, Map<String, String> extras) {
        if (phoneNumber == null || phoneNumber.isBlank() || extras == null || extras.isEmpty()) return;
        String normalizedIdentity = normalizeUserIdentity(phoneNumber);
        Map<String, Object> command = Map.of(
            "id", UUID.randomUUID().toString(),
            "to", "postmaster@contacts.msging.net",
            "method", "merge",
            "uri", "/contacts",
            "type", "application/vnd.lime.contact+json",
            "resource", Map.of(
                "identity", normalizedIdentity,
                "extras", extras
            )
        );
        try {
            executeCommand(command, AuthorizationScope.ROUTER);
            log.debug("Contact extras atualizados no Blip para {}", normalizedIdentity);
        } catch (RestClientException ex) {
            log.warn("Falha ao atualizar extras do contato no Blip para {}: {}", normalizedIdentity, ex.getMessage());
        }
    }

    @Override
    @Retryable(
        retryFor = { RestClientException.class, org.springframework.web.client.ResourceAccessException.class, org.springframework.dao.DataAccessException.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2.0)
    )
    public Map<String, Object> executeCommand(Map<String, Object> payload, AuthorizationScope scope) {
        rateLimit();
        
        AuthorizationScope actualScope = scope;
        Map<String, Object> finalPayload = payload;
        
        if (payload != null && payload.containsKey("uri")) {
            String uri = String.valueOf(payload.get("uri"));
            finalPayload = new java.util.HashMap<>(payload);
            
            if (uri.contains("/teams") || uri.contains("/threads") || uri.contains("/attendance-queues") || uri.contains("/buckets") || uri.contains("/tickets")) {
                finalPayload.put("to", "postmaster@desk.msging.net");
                actualScope = AuthorizationScope.DESK;
                if (uri.contains("/teams")) {
                    finalPayload.put("uri", "/teams");
                } else if (uri.contains("/threads")) {
                    finalPayload.put("uri", "/threads");
                } else if (uri.contains("/tickets")) {
                    finalPayload.put("uri", "/tickets");
                } else if (uri.contains("/attendance-queues")) {
                    finalPayload.put("uri", "/attendance-queues");
                }
            } else if (uri.contains("/message-templates")) {
                finalPayload.put("to", "postmaster@wa.gw.msging.net");
                finalPayload.put("uri", "/message-templates");
                actualScope = AuthorizationScope.ROUTER;
            } else {
                finalPayload.put("to", "postmaster@msging.net");
                actualScope = AuthorizationScope.ROUTER;
            }
        }

        URI url = UriComponentsBuilder.fromUriString(resolveBlipBaseUrl())
                .path(properties.getBlipSetContextPath())
                .build().toUri();

        log.debug("Enviando comando LIME (Scope: {}) para a URL: {}", actualScope, url);
        try {
            log.debug("Payload completo: {}", new ObjectMapper().writeValueAsString(finalPayload));
        } catch (JsonProcessingException ignored) {
            log.debug("Payload completo: {}", finalPayload);
        }

        ResponseEntity<Map<String, Object>> response = blipRestTemplate.exchange(
                url, HttpMethod.POST,
                new HttpEntity<>(finalPayload, buildHeaders(actualScope)),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        Map<String, Object> body = response.getBody();
        if (body == null) {
            log.debug("Blip retornou body null no executeCommand. Payload enviado: {}", finalPayload);
        } else {
            if (body.containsKey("status") && !"success".equalsIgnoreCase(String.valueOf(body.get("status")))) {
                log.warn("[LIME-FAILURE] Comando LIME retornou status de falha ou timeout da API Blip: {}. Payload enviado: {}", body, finalPayload);
            }
            Object resource = body.get("resource");
            boolean isEmpty = resource == null;
            
            if (resource instanceof List<?> list && list.isEmpty()) {
                isEmpty = true;
            } else if (resource instanceof Map<?, ?> map) {
                Object items = map.get("items");
                if (items instanceof List<?> list && list.isEmpty()) {
                    isEmpty = true;
                }
            }
            
            if (isEmpty) {
                log.debug("Blip retornou recurso vazio ou nulo no executeCommand. Body completo: {}", body);
            }
        }

        return body != null ? body : Map.of();
    }

    /**
     * Fallback para falha no envio de comando ao Blip.
     * Retorna fallback seguro e registra a intenção de sincronização offline para evitar estouro de erro 500.
     */
    @Recover
    public Map<String, Object> fallbackExecuteCommand(Map<String, Object> payload, AuthorizationScope scope, Throwable t) {
        log.warn("[OFFLINE-SYNC-INTENT] [BLIP] Falha ao executar comando no Blip após retentativas (Timeout/Erro). Erro: {}. Gravando payload para sincronização offline posterior: {}", t.getMessage(), payload);
        return Map.of("status", "offline-queued", "message", t.getMessage());
    }

    @Override
    @Retryable(
        retryFor = { RestClientException.class, org.springframework.web.client.ResourceAccessException.class, org.springframework.dao.DataAccessException.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2.0)
    )
    public Map<String, Object> executeMessage(Map<String, Object> payload, AuthorizationScope scope) {
        rateLimit();
        URI url = UriComponentsBuilder.fromUriString(resolveBlipBaseUrl())
                .path(properties.getBlipSendMessagePath())
                .build().toUri();

        ResponseEntity<Map<String, Object>> response = blipRestTemplate.exchange(
                url, HttpMethod.POST,
                new HttpEntity<>(payload, buildHeaders(scope)),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );
        if (response != null) {
            var body = response.getBody();
            if (body == null || body.isEmpty()) {
                log.debug("[API-BLIP-RESPONSE] status={}", response.getStatusCode());
            } else {
                log.debug("[API-BLIP-RESPONSE] status={}, body={}", response.getStatusCode(), body);
                if (body.containsKey("status") && !"success".equalsIgnoreCase(String.valueOf(body.get("status")))) {
                    log.warn("[LIME-FAILURE] Mensagem LIME retornou status de falha ou timeout da API Blip: {}. Payload enviado: {}", body, payload);
                }
            }
        }
        return (response != null && response.getBody() != null) ? response.getBody() : Map.of();
    }

    /**
     * Fallback para falha no envio de mensagem ao Blip.
     * Retorna fallback seguro e registra a intenção de sincronização offline para evitar estouro de erro 500.
     */
    @Recover
    public Map<String, Object> fallbackExecuteMessage(Map<String, Object> payload, AuthorizationScope scope, Throwable t) {
        log.warn("[OFFLINE-SYNC-INTENT] [BLIP] Falha ao enviar mensagem no Blip após retentativas (Timeout/Erro). Erro: {}. Gravando payload para sincronização offline posterior: {}", t.getMessage(), payload);
        return Map.of("status", "offline-queued", "message", t.getMessage());
    }

    public String resolveBlipBaseUrl() {
        String configured = properties.getBlipBaseUrl();
        return (configured != null && !configured.isBlank()) ? configured.trim() : "";
    }

    @Override
    public String normalizeUserIdentity(String userIdentity) {
        if (userIdentity == null || userIdentity.isBlank()) return "unknown@wa.gw.msging.net";
        String sanitized = userIdentity.trim();

        if (sanitized.contains("@desk.msging.net")) {
            int separatorIndex = sanitized.indexOf('@');
            String localPart = separatorIndex >= 0 ? sanitized.substring(0, separatorIndex) : sanitized;
            try {
                return java.net.URLDecoder.decode(localPart, java.nio.charset.StandardCharsets.UTF_8);
            } catch (Exception e) {
                // fallback
            }
        }
        
        String localPart = sanitized;
        String domainPart = "wa.gw.msging.net";
        
        if (sanitized.contains("@")) {
            int separatorIndex = sanitized.indexOf('@');
            localPart = separatorIndex >= 0 ? sanitized.substring(0, separatorIndex) : sanitized;
            domainPart = separatorIndex >= 0 ? sanitized.substring(separatorIndex + 1) : "wa.gw.msging.net";
        }
        
        if (!"wa.gw.msging.net".equalsIgnoreCase(domainPart)) {
            return sanitized;
        }
        
        if (isGuidOrUsername(localPart)) {
            return localPart + "@wa.gw.msging.net";
        }
        
        return normalizePhone(localPart) + "@wa.gw.msging.net";
    }

    private boolean isGuidOrUsername(String value) {
        if (value == null || value.isBlank()) return false;
        // COMENTÁRIO EM PORTUGUÊS (PT-BR):
        // Detecta se a parte local da identidade é um GUID mascarado ou Username do WhatsApp.
        // Se contiver qualquer caractere alfabético (a-z, A-Z) ou hifens, é tratado como GUID/Username.
        return value.matches(".*[a-zA-Z\\-].*");
    }

    private String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) return "";
        String digitsOnly = phone.startsWith("+") 
            ? phone.substring(1).replaceAll("[^0-9]", "") 
            : phone.replaceAll("[^0-9]", "");
        return digitsOnly.isBlank() ? "" : digitsOnly;
    }

    @Override
    public Map<String, Object> getContactProfile(String userIdentity) {
        if (userIdentity == null || userIdentity.isBlank()) return Map.of();
        String normalizedIdentity = normalizeUserIdentity(userIdentity);
        Map<String, Object> command = Map.of(
            "id", UUID.randomUUID().toString(),
            "to", "postmaster@contacts.msging.net",
            "method", "get",
            "uri", "/contacts/" + normalizedIdentity
        );
        try {
            log.debug("[LIME-CLIENT] [CONTACT-PROFILE] Consultando perfil do contato para identity={}", normalizedIdentity);
            return executeCommand(command, AuthorizationScope.ROUTER);
        } catch (RestClientException ex) {
            log.error("[LIME-CLIENT] [CONTACT-PROFILE] Falha ao buscar perfil do contato no Blip para {}", normalizedIdentity, ex);
            return Map.of();
        }
    }

    private HttpHeaders buildHeaders(AuthorizationScope scope) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("application", "json", StandardCharsets.UTF_8));
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        String authKey = normalizeAuthorizationKey(resolveAuthorizationKey(scope));
        if (authKey != null && !authKey.isBlank()) {
            headers.set("Authorization", "Key " + authKey);
        }
        return headers;
    }

    private String resolveAuthorizationKey(AuthorizationScope scope) {
        if (scope == AuthorizationScope.ROUTER) {
            String env = System.getenv("APP_APPOINTMENT_BLIP_ROUTER_KEY");
            return env != null && !env.isBlank() ? env : properties.getBot().getBlipRouterKey();
        } else {
            String env = System.getenv("APP_APPOINTMENT_BLIP_DESK_KEY");
            return env != null && !env.isBlank() ? env : properties.getBot().getBlipDeskKey();
        }
    }

    private String normalizeAuthorizationKey(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        if (normalized.regionMatches(true, 0, "Key ", 0, 4)) return normalized.substring(4).trim();
        if (normalized.regionMatches(true, 0, "Bearer ", 0, 7)) return normalized.substring(7).trim();
        return normalized;
    }

    /**
     * Registra no log um bloqueio de segurança aplicado pelo motor de agendamento.
     * Em ambientes que nao sao PRODUCTION, apenas o numero de telefone de teste e autorizado a receber mensagens.
     *
     * @param phoneNumber numero bloqueado
     * @param appMode     modo de execucao atual da aplicacao (ex: "STAGING", "DEV")
     */
    public void logSecurityBlock(String phoneNumber, String appMode) {
        log.warn("[SECURITY BLOCK] Envio bloqueado para numero {} no modo {}. Apenas o numero de teste autorizado em ambiente nao-producao.", phoneNumber, appMode);
    }

    private void rateLimit() {
        long currentLastRequest = lastRequestAt.get();
        long delay = properties.getBlipRateLimitMs() - (System.currentTimeMillis() - currentLastRequest);
        if (delay > 0) { // Se o delay for positivo, significa que precisamos esperar
            try { // LockSupport.parkNanos é preferível a Thread.sleep para Virtual Threads, pois não as "pina"
                LockSupport.parkNanos(java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(delay));
            } catch (Exception ex) {
                Thread.currentThread().interrupt();
                log.warn("Thread interrompida no rate limit", ex);
            }
        }
        lastRequestAt.set(System.currentTimeMillis());
    }

    /**
     * Recuperador gracioso do Spring Retry para falhas definitivas no executeCommand.
     */
    @Recover
    public Map<String, Object> recoverExecuteCommand(RestClientException ex, Map<String, Object> payload, AuthorizationScope scope) {
        log.error("[RECOVERY-BLIP] Falha definitiva após 3 tentativas de execução de comando LIME na Blip. Erro: {}", 
            ex.getMessage(), ex);
        return Map.of("status", "offline-queued", "message", ex.getMessage());
    }

    /**
     * Recuperador gracioso do Spring Retry para falhas definitivas no executeMessage.
     */
    @Recover
    public Map<String, Object> recoverExecuteMessage(RestClientException ex, Map<String, Object> payload, AuthorizationScope scope) {
        log.error("[RECOVERY-BLIP] Falha definitiva após 3 tentativas de envio de mensagem na Blip. Erro: {}", 
            ex.getMessage(), ex);
        return Map.of("status", "offline-queued", "message", ex.getMessage());
    }
}