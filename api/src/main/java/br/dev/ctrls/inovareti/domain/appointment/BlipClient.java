package br.dev.ctrls.inovareti.domain.appointment;

import java.nio.charset.StandardCharsets;
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
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import br.dev.ctrls.inovareti.domain.appointment.dto.BlipTemplateDto;
import br.dev.ctrls.inovareti.domain.appointment.dto.BlipTemplateResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = "app.appointment.motor.enabled", havingValue = "true", matchIfMissing = true)
public class BlipClient {

    private static final String DEFAULT_ROUTER_IDENTITY = "postmaster@wa.gw.msging.net";

    private final RestTemplate restTemplate;
    private final AppointmentMotorProperties properties;
    private final AtomicLong lastRequestAt = new AtomicLong(0L);

    public void sendTemplateMessage(String destination, String templateId, List<String> variables) {
        // Antes de enviar o template, força o usuário para o sub-bot de agendamentos.
        pullUserToAgendamentoBot(destination);
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
     * Direciona o usuário para o sub-bot de agendamentos (Master-State).
     */
    public void pullUserToAgendamentoBot(String userEmail) {
        setMasterState(userEmail, properties.getBlipAgendamentoBotId(), "agendamentos");
    }

    /**
     * Retorna o usuário para o bot principal (Builder) via Master-State.
     */
    public void pushUserBackToBuilder(String userEmail) {
        setMasterState(userEmail, properties.getBlipBuilderBotId(), "builder");
    }

    /**
     * Busca templates de mensagens aprovados na API do Blip
     * Envia comando JSON-RPC para obter lista de templates
     * @return Lista de templates (id, nome) apenas dos aprovados
     */
    public List<BlipTemplateDto> fetchTemplatesFromBlip() {
        BlipTemplateResponse approvedResponse = fetchTemplatesByUri("/message-templates?status=Approved");

        try {
            if (approvedResponse == null
                || approvedResponse.resource() == null
                || approvedResponse.resource().documents() == null
                || approvedResponse.resource().documents().isEmpty()) {
            log.warn("Sem documentos em /message-templates?status=Approved. Tentando fallback sem filtro de status.");

            BlipTemplateResponse allStatusesResponse = fetchTemplatesByUri("/message-templates");
            if (allStatusesResponse == null
                || allStatusesResponse.resource() == null
                || allStatusesResponse.resource().documents() == null
                || allStatusesResponse.resource().documents().isEmpty()) {
                log.warn("Fallback /message-templates também retornou vazio.");
                return List.of();
            }

            logTemplateSummary(allStatusesResponse, "fallback-sem-filtro");

            return allStatusesResponse.resource().documents().stream()
                .map(template -> new BlipTemplateDto(template.id(), template.name()))
                .collect(Collectors.toList());
            }

            logTemplateSummary(approvedResponse, "status-approved");

            // Filtra apenas templates aprovados e converte para DTO
            return approvedResponse.resource().documents().stream()
                    .filter(template -> "Approved".equalsIgnoreCase(template.status()))
                    .map(template -> new BlipTemplateDto(template.id(), template.name()))
                    .collect(Collectors.toList());

        } catch (RestClientException ex) {
            if (ex instanceof HttpStatusCodeException httpEx) {
                log.error("Erro HTTP ao buscar templates do Blip. statusCode={}, responseBody={}",
                        httpEx.getStatusCode(), httpEx.getResponseBodyAsString(), ex);
                return List.of();
            }

            log.error("Erro inesperado ao buscar templates do Blip", ex);
            return List.of();
        }
    }

        private BlipTemplateResponse fetchTemplatesByUri(String commandUri) {
        rateLimit();

        String url = UriComponentsBuilder.fromUriString(properties.getBlipBaseUrl())
            .path(properties.getBlipSetContextPath())
            .build()
            .toUriString();

        Map<String, Object> command = Map.of(
            "id", UUID.randomUUID().toString(),
            "to", resolveRouterIdentity(),
            "method", "get",
            "uri", commandUri);

        log.info("Comando JSON-RPC enviado ao Blip: {}", command);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(command, buildHeaders());

        ResponseEntity<BlipTemplateResponse> response = restTemplate.exchange(
            url,
            HttpMethod.POST,
            request,
            new ParameterizedTypeReference<BlipTemplateResponse>() {
            });

        BlipTemplateResponse responseBody = response.getBody();
        log.info("Blip Raw Response [{}]: {}", commandUri, responseBody);
        return responseBody;
        }

        private void logTemplateSummary(BlipTemplateResponse responseBody, String source) {
        Integer totalFromResponse = responseBody.resource().total();
        int total = totalFromResponse != null
            ? totalFromResponse
            : responseBody.resource().documents().size();

        long approvedCount = responseBody.resource().documents().stream()
            .filter(template -> "Approved".equalsIgnoreCase(template.status()))
            .count();

        Map<String, Long> statusBreakdown = responseBody.resource().documents().stream()
            .collect(Collectors.groupingBy(template -> String.valueOf(template.status()), Collectors.counting()));

        log.info("Resumo de templates no Blip. source={}, total={}, approvedCount={}, statusBreakdown={}",
            source, total, approvedCount, statusBreakdown);
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

    private void setMasterState(String userIdentity, String botIdentity, String operation) {
        if (botIdentity == null || botIdentity.isBlank()) {
            log.warn("Master-State não executado para {}: botIdentity não configurado.", operation);
            return;
        }

        String normalizedIdentity = normalizeUserIdentity(userIdentity);
        String encodedIdentity = UriUtils.encodePathSegment(normalizedIdentity, StandardCharsets.UTF_8);

        String url = UriComponentsBuilder.fromUriString(properties.getBlipBaseUrl())
                .path(properties.getBlipSetContextPath())
                .build()
                .toUriString();

        Map<String, Object> command = Map.of(
                "id", UUID.randomUUID().toString(),
                "to", resolveRouterIdentity(),
                "method", "set",
                "uri", "/contexts/" + encodedIdentity + "/Master-State",
                "type", "text/plain",
                "resource", botIdentity);

        try {
            rateLimit();
            restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(command, buildHeaders()), Void.class);
            log.info("Master-State atualizado com sucesso. operation={}, userIdentity={}, targetBot={}",
                    operation, normalizedIdentity, botIdentity);
        } catch (RestClientException ex) {
            if (ex instanceof HttpStatusCodeException httpEx) {
                log.error("Erro HTTP ao atualizar Master-State. operation={}, statusCode={}, responseBody={}",
                        operation, httpEx.getStatusCode(), httpEx.getResponseBodyAsString(), ex);
                return;
            }

            log.error("Erro inesperado ao atualizar Master-State. operation={}", operation, ex);
        }
    }

    private String resolveRouterIdentity() {
        String configuredIdentity = properties.getBlipRouterIdentity();
        if (configuredIdentity == null || configuredIdentity.isBlank()) {
            return DEFAULT_ROUTER_IDENTITY;
        }
        return configuredIdentity;
    }

    private String normalizeUserIdentity(String userIdentity) {
        if (userIdentity == null || userIdentity.isBlank()) {
            return "unknown@wa.gw.msging.net";
        }

        String sanitized = userIdentity.trim();
        if (sanitized.contains("@")) {
            return sanitized;
        }

        String digitsOnly = sanitized.replaceAll("[^0-9]", "");
        if (digitsOnly.isBlank()) {
            return sanitized + "@wa.gw.msging.net";
        }

        return digitsOnly + "@wa.gw.msging.net";
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
