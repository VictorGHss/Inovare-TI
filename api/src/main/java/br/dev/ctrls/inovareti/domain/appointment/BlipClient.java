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
     * Envia mensagem textual direta para o usuário no Blip.
     * Usado quando o redirecionamento precisa ocorrer por link externo.
     */
    public void sendTextMessage(String destination, String text) {
        sendPlainText(destination, text);
    }

    /**
     * Envia texto puro para o destino informado.
     */
    public void sendPlainText(String destination, String text) {
        rateLimit();

        String url = UriComponentsBuilder.fromUriString(properties.getBlipBaseUrl())
                .path(properties.getBlipSendMessagePath())
                .build()
                .toUriString();

        Map<String, Object> payload = Map.of(
                "to", destination,
                "type", "text/plain",
                "content", text);

        restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(payload, buildHeaders()), Void.class);
        log.info("Mensagem textual enviada ao Blip. destination={}", destination);
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
            "to", "postmaster@wa.gw.msging.net",
                "method", "get",
            "uri", "/message-templates?status=Approved");

        log.info("Comando JSON-RPC enviado ao Blip: {}", command);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(command, buildHeaders());

        try {
            ResponseEntity<BlipTemplateResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    new ParameterizedTypeReference<BlipTemplateResponse>() {
                    });

            BlipTemplateResponse responseBody = response.getBody();
            log.info("Blip Raw Response: {}", responseBody);

            if (responseBody == null || responseBody.resource() == null || 
                responseBody.resource().documents() == null) {
                log.warn("Resposta vazia ao buscar templates do Blip");
                return List.of();
            }

            int total = responseBody.resource().total() == null
                    ? responseBody.resource().documents().size()
                    : responseBody.resource().total();

            long approvedCount = responseBody.resource().documents().stream()
                    .filter(template -> "Approved".equalsIgnoreCase(template.status()))
                    .count();

            log.info("Resumo de templates no Blip. total={}, approvedCount={}", total, approvedCount);

            // Filtra apenas templates aprovados e converte para DTO
            return responseBody.resource().documents().stream()
                    .filter(template -> "Approved".equalsIgnoreCase(template.status()))
                    .map(template -> new BlipTemplateDto(template.id(), template.name()))
                    .collect(Collectors.toList());

        } catch (HttpClientErrorException | HttpServerErrorException ex) {
            log.error("Erro HTTP ao buscar templates do Blip. statusCode={}, responseBody={}",
                    ex.getStatusCode(), ex.getResponseBodyAsString(), ex);
            return List.of();
        } catch (Exception ex) {
            log.error("Erro inesperado ao buscar templates do Blip", ex);
            return List.of();
        }
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (properties.getBlipAuthorizationKey() == null || properties.getBlipAuthorizationKey().isBlank()) {
            log.warn("Chave de autorização do Blip está vazia. Verifique APP_APPOINTMENT_BLIP_BOT_KEY (Router Key).");
        }
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
