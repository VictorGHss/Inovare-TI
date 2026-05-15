package br.dev.ctrls.inovareti.domain.appointment.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

import jakarta.annotation.PostConstruct;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.core.JsonProcessingException;

import br.dev.ctrls.inovareti.domain.appointment.AppointmentMotorProperties;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class BlipLIMEClient {

    public enum AuthorizationScope { ROUTER, DESK }

    public static final String MASTER_STATE_COMMAND_TO = "postmaster@msging.net";
    public static final String THREAD_TRANSFER_COMMAND_TO = "postmaster@desk.msging.net";

    private final RestTemplate injectedRestTemplate;
    private final AppointmentMotorProperties properties;
    private final AtomicLong lastRequestAt = new AtomicLong(0L);

    private RestTemplate blipRestTemplate;

    public record BlipQueue(String id, String name) {}

    public Map<String, Object> getBlipQueues() {
        Map<String, Object> command = Map.of(
            "id", UUID.randomUUID().toString(),
            "to", "postmaster@desk.msging.net",
            "method", "get",
            "uri", "/teams"
        );
        try {
            return executeCommand(command, AuthorizationScope.ROUTER).getBody();
        } catch (RestClientException ex) {
            log.error("Erro ao buscar filas no Blip", ex);
            return Map.of();
        }
    }

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
                        String id = String.valueOf(itemMap.get("id"));
                        String name = String.valueOf(itemMap.get("name"));
                        queues.add(new BlipQueue(id, name));
                    }
                }
                return queues;
            }
        }
        return List.of();
    }

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

    public void logSecurityBlock(String phoneNumber, String appMode) {
        log.warn("[SANDBOX BLOCK] Disparo para número não autorizado em ambiente {}: {}", appMode, phoneNumber);
    }

    public BlipLIMEClient(RestTemplate restTemplate, AppointmentMotorProperties properties) {
        this.injectedRestTemplate = restTemplate;
        this.properties = properties;
    }

    @PostConstruct
    public void setupClient() {
        try {
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(5000);
            requestFactory.setReadTimeout(5000);
            blipRestTemplate = new RestTemplate(requestFactory);
            blipRestTemplate.setMessageConverters(new ArrayList<>(injectedRestTemplate.getMessageConverters()));
            blipRestTemplate.setInterceptors(new ArrayList<>(injectedRestTemplate.getInterceptors()));
            blipRestTemplate.setErrorHandler(injectedRestTemplate.getErrorHandler());
            log.info("Blip RestTemplate configurado com timeout de 5s");
        } catch (Exception ex) {
            log.warn("Falha ao configurar Blip RestTemplate com timeout; usando RestTemplate injetado", ex);
            blipRestTemplate = injectedRestTemplate;
        }
    }

    public ResponseEntity<Map<String, Object>> executeCommand(Map<String, Object> payload, AuthorizationScope scope) {
        rateLimit();
        
        AuthorizationScope actualScope = scope;
        Map<String, Object> finalPayload = payload;
        
        if (payload != null && payload.containsKey("uri")) {
            String uri = String.valueOf(payload.get("uri"));
            finalPayload = new java.util.HashMap<>(payload);
            
            if (uri.contains("/teams") || uri.contains("/threads") || uri.contains("/attendance-queues") || uri.contains("/buckets")) {
                finalPayload.put("to", "postmaster@desk.msging.net");
                actualScope = AuthorizationScope.DESK;
                if (uri.contains("/teams")) {
                    finalPayload.put("uri", "/teams");
                } else if (uri.contains("/threads")) {
                    finalPayload.put("uri", "/threads");
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

        java.net.URI url = UriComponentsBuilder.fromUriString(resolveBlipBaseUrl())
                .path(properties.getBlipSetContextPath())
                .build().toUri();

        try {
            log.info("Enviando comando LIME (Scope: {}) para a URL: {} Payload completo: {}", actualScope, url, new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(finalPayload));
        } catch (JsonProcessingException ignored) {
            log.info("Enviando comando LIME (Scope: {}) para a URL: {} Payload completo: {}", actualScope, url, finalPayload);
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
            Object resource = body.get("resource");
            boolean isEmpty = resource == null;
            
            if (resource instanceof java.util.List<?> list && list.isEmpty()) {
                isEmpty = true;
            } else if (resource instanceof Map<?, ?> map) {
                Object items = map.get("items");
                if (items instanceof java.util.List<?> list && list.isEmpty()) {
                    isEmpty = true;
                }
            }
            
            if (isEmpty) {
                log.debug("Blip retornou recurso vazio ou nulo no executeCommand. Body completo: {}", body);
            }
        }

        return response;
    }

    public ResponseEntity<Map<String, Object>> executeMessage(Map<String, Object> payload, AuthorizationScope scope) {
        rateLimit();
        java.net.URI url = UriComponentsBuilder.fromUriString(resolveBlipBaseUrl())
                .path(properties.getBlipSendMessagePath())
                .build().toUri();

        return blipRestTemplate.exchange(
                url, HttpMethod.POST,
                new HttpEntity<>(payload, buildHeaders(scope)),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );
    }

    public String resolveBlipBaseUrl() {
        String configured = properties.getBlipBaseUrl();
        return (configured != null && !configured.isBlank()) ? configured.trim() : "";
    }

    public String normalizeUserIdentity(String userIdentity) {
        if (userIdentity == null || userIdentity.isBlank()) return "unknown@wa.gw.msging.net";
        String sanitized = userIdentity.trim();
        
        if (sanitized.contains("@")) {
            int separatorIndex = sanitized.indexOf('@');
            String localPart = separatorIndex >= 0 ? sanitized.substring(0, separatorIndex) : sanitized;
            String domainPart = separatorIndex >= 0 ? sanitized.substring(separatorIndex + 1) : "";
            if (!"wa.gw.msging.net".equalsIgnoreCase(domainPart)) return sanitized;
            return normalizePhone(localPart) + "@wa.gw.msging.net";
        }
        return normalizePhone(sanitized) + "@wa.gw.msging.net";
    }

    private String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) return "";
        String digitsOnly = phone.startsWith("+") 
            ? phone.substring(1).replaceAll("[^0-9]", "") 
            : phone.replaceAll("[^0-9]", "");
        return digitsOnly.isBlank() ? "" : digitsOnly;
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

    private void rateLimit() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastRequestAt.get();
        long delay = properties.getBlipRateLimitMs() - elapsed;

        if (delay > 0) {
            try { Thread.sleep(delay); } 
            catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                log.warn("Thread interrompida no rate limit", ex);
            }
        }
        lastRequestAt.set(System.currentTimeMillis());
    }
}