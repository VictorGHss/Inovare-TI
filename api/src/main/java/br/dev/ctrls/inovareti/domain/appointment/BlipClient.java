package br.dev.ctrls.inovareti.domain.appointment;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import br.dev.ctrls.inovareti.domain.appointment.dto.BlipTemplateDto;
import br.dev.ctrls.inovareti.domain.appointment.dto.BlipTemplateResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = "app.appointment.motor.enabled", havingValue = "true", matchIfMissing = true)
public class BlipClient {

    private final RestTemplate restTemplate;
    private final AppointmentMotorProperties properties;
    private final AtomicLong lastRequestAt = new AtomicLong(0L);

    public void sendTemplateMessage(String destination, String templateId, List<String> variables) {
        rateLimit();

        String url = UriComponentsBuilder.fromUriString(properties.getBlipBaseUrl())
                .path(properties.getBlipSendMessagePath())
                .build()
                .toUriString();

        Map<String, Object> payload = Map.of(
                "to", destination,
                "templateId", templateId,
                "variables", variables);

        restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(payload, buildHeaders()), Void.class);
        log.info("Template enviado ao Blip. destination={}, templateId={}", destination, templateId);
    }

    public void setHandoffContext(String destination, String queueId) {
        rateLimit();

        String url = UriComponentsBuilder.fromUriString(properties.getBlipBaseUrl())
                .path(properties.getBlipSetContextPath())
                .build()
                .toUriString();

        Map<String, Object> payload = Map.of(
                "to", destination,
                "queueId", queueId,
                "handoff", true);

        restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(payload, buildHeaders()), Void.class);
        log.info("Contexto de handoff enviado ao Blip. destination={}, queueId={}", destination, queueId);
    }

    /**
     * Busca templates de mensagens aprovados na API do Blip
     * Envia comando JSON-RPC para obter lista de templates
     * @return Lista de templates (id, nome) apenas dos aprovados
     */
    public List<BlipTemplateDto> fetchTemplatesFromBlip() {
        rateLimit();

        String url = UriComponentsBuilder.fromUriString(properties.getBlipBaseUrl())
                .path(properties.getBlipSetContextPath())
                .build()
                .toUriString();

        // Comando JSON-RPC conforme especificação do Blip
        Map<String, Object> command = Map.of(
                "id", UUID.randomUUID().toString(),
                "method", "get",
                "uri", "/message-templates");

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(command, buildHeaders());

        try {
            ResponseEntity<BlipTemplateResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    new ParameterizedTypeReference<BlipTemplateResponse>() {
                    });

            if (response.getBody() == null || response.getBody().resource() == null || 
                response.getBody().resource().documents() == null) {
                log.warn("Resposta vazia ao buscar templates do Blip");
                return List.of();
            }

            // Filtra apenas templates aprovados e converte para DTO
            return response.getBody().resource().documents().stream()
                    .filter(template -> "Approved".equalsIgnoreCase(template.status()))
                    .map(template -> new BlipTemplateDto(template.id(), template.name()))
                    .collect(Collectors.toList());

        } catch (HttpClientErrorException | HttpServerErrorException | org.springframework.web.client.ResourceAccessException ex) {
            log.error("Erro ao buscar templates do Blip", ex);
            return List.of();
        }
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("Authorization", properties.getBlipAuthorizationKey());
        return headers;
    }

    private void rateLimit() {
        long now = System.currentTimeMillis();
        long previous = lastRequestAt.get();
        long elapsed = now - previous;
        long delay = properties.getBlipRateLimitMs() - elapsed;

        if (delay > 0) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                log.warn("Thread interrompida durante rate limit do Blip", ex);
            }
        }

        lastRequestAt.set(System.currentTimeMillis());
    }
}
