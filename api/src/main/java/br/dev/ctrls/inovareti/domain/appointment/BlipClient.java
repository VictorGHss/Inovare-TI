package br.dev.ctrls.inovareti.domain.appointment;

import java.nio.charset.StandardCharsets;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

import br.dev.ctrls.inovareti.domain.appointment.dto.AppointmentTemplateData;
import br.dev.ctrls.inovareti.domain.appointment.dto.BlipTemplateDto;
import br.dev.ctrls.inovareti.domain.appointment.dto.BlipTemplateResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = "app.appointment.motor.enabled", havingValue = "true", matchIfMissing = true)
public class BlipClient {

    private static final String FIXED_WABA_NAMESPACE = "63a9b11b_7f32_4ca2_8da1_60b6a41de39e";
    private static final String DEFAULT_ROUTER_IDENTITY = "postmaster@wa.gw.msging.net";
    private static final String DEFAULT_BUILDER_BOT_IDENTITY = "fluxov1@msging.net";

    private final RestTemplate restTemplate;
    private final AppointmentMotorProperties properties;
    private final AppointmentTemplateMappingRepository appointmentTemplateMappingRepository;
    private final AtomicLong lastRequestAt = new AtomicLong(0L);

    public void sendTemplateMessage(String destination, String templateName, AppointmentTemplateData appointmentData) {
        // Antes de enviar o template, força o usuário para o sub-bot de agendamentos.
        pullUserToAgendamentoBot(destination);
        rateLimit();

        String url = UriComponentsBuilder.fromUriString(properties.getBlipBaseUrl())
                .path(properties.getBlipSendMessagePath())
                .build()
                .toUriString();

        List<Map<String, String>> parameters = buildDynamicParameters(templateName, appointmentData);
        List<Map<String, Object>> components = parameters.isEmpty()
                ? List.of()
                : List.of(Map.of("type", "body", "parameters", parameters));
        String appointmentId = appointmentData != null && appointmentData.appointmentId() != null
                ? appointmentData.appointmentId()
                : "";

        Map<String, Object> payload = Map.of(
            "to", destination,
            "templateName", templateName,
            "namespace", resolveWabaNamespace(),
            "components", components,
            "metadata", Map.of("appointmentId", appointmentId));

        restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(payload, buildHeaders()), Void.class);
        log.info("Template enviado ao Blip. destination={}, templateName={}, namespace={}, components={}, appointmentId={}",
            destination, templateName, resolveWabaNamespace(), components.size(), appointmentId);
    }

    private List<Map<String, String>> buildDynamicParameters(String templateName, AppointmentTemplateData appointmentData) {
        List<AppointmentTemplateMapping> mappings = appointmentTemplateMappingRepository
                .findByTemplateNameIgnoreCaseOrderByPlaceholderIndexAsc(templateName);

        if (mappings.isEmpty()) {
            log.warn("Nenhum mapeamento dinâmico encontrado para templateName={}", templateName);
            return List.of();
        }

        List<Map<String, String>> parameters = new ArrayList<>();
        for (AppointmentTemplateMapping mapping : mappings) {
            String value = resolveDynamicFieldValue(appointmentData, mapping.getFeegowFieldName());
            parameters.add(Map.of(
                    "type", "text",
                    "text", value));
        }

        return parameters;
    }

    private String resolveDynamicFieldValue(AppointmentTemplateData appointmentData, String fieldName) {
        if (appointmentData == null || fieldName == null || fieldName.isBlank()) {
            return "";
        }

        try {
            Method accessor = AppointmentTemplateData.class.getMethod(fieldName.trim());
            Object value = accessor.invoke(appointmentData);
            return value == null ? "" : String.valueOf(value);
        } catch (ReflectiveOperationException ex) {
            log.warn("Campo dinâmico inválido no mapeamento. fieldName={}", fieldName, ex);
            return "";
        }
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
        List<String> candidateIdentities = buildTemplateFetchIdentities();
        log.info("Iniciando diagnóstico de templates com identidades candidatas: {}", candidateIdentities);

        try {
            for (String identity : candidateIdentities) {
                BlipTemplateResponse approvedResponse = fetchTemplatesByUri("/message-templates?status=Approved", identity);

                if (hasDocuments(approvedResponse)) {
                    logTemplateSummary(approvedResponse, "status-approved:" + identity);
                    return approvedResponse.resource().documents().stream()
                            .filter(template -> "Approved".equalsIgnoreCase(template.status()))
                            .map(template -> new BlipTemplateDto(template.id(), template.name()))
                            .collect(Collectors.toList());
                }

                log.warn("Sem documentos em /message-templates?status=Approved para identity={}. Tentando fallback sem filtro.",
                        identity);

                BlipTemplateResponse allStatusesResponse = fetchTemplatesByUri("/message-templates", identity);
                if (hasDocuments(allStatusesResponse)) {
                    logTemplateSummary(allStatusesResponse, "fallback-sem-filtro:" + identity);
                    return allStatusesResponse.resource().documents().stream()
                            .map(template -> new BlipTemplateDto(template.id(), template.name()))
                            .collect(Collectors.toList());
                }
            }

            log.warn("Nenhuma identidade retornou templates. Aplicando fallback estático.");
            return staticTemplateFallback();

        } catch (RestClientException ex) {
            if (ex instanceof HttpStatusCodeException httpEx) {
                log.error("Erro HTTP ao buscar templates do Blip. statusCode={}, responseBody={}",
                        httpEx.getStatusCode(), httpEx.getResponseBodyAsString(), ex);
                return staticTemplateFallback();
            }

            log.error("Erro inesperado ao buscar templates do Blip", ex);
            return staticTemplateFallback();
        }
    }

    private BlipTemplateResponse fetchTemplatesByUri(String commandUri, String toIdentity) {
        rateLimit();

        String url = UriComponentsBuilder.fromUriString(properties.getBlipBaseUrl())
                .path(properties.getBlipSetContextPath())
                .build()
                .toUriString();

        Map<String, Object> command = Map.of(
                "id", UUID.randomUUID().toString(),
                "to", toIdentity,
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

        logNamespaceHeaders(response.getHeaders(), commandUri, toIdentity);

        BlipTemplateResponse responseBody = response.getBody();
        log.info("Blip Raw Response [uri={}, to={}]: {}", commandUri, toIdentity, responseBody);
        return responseBody;
    }

    private List<BlipTemplateDto> staticTemplateFallback() {
        return List.of(
                new BlipTemplateDto("confirmacao_consulta_v5", "confirmacao_consulta_v5"),
                new BlipTemplateDto("aviso_interacao_necessariav1", "aviso_interacao_necessariav1"),
                new BlipTemplateDto("aviso_final_cancelamento", "aviso_final_cancelamento"));
    }

    private List<String> buildTemplateFetchIdentities() {
        Set<String> identities = new LinkedHashSet<>();
        // Em arquitetura Router + Shared WABA, consultar como sub-bot pode expor templates do namespace.
        identities.add(DEFAULT_BUILDER_BOT_IDENTITY);
        addIfNotBlank(identities, properties.getBlipBuilderBotId());
        identities.add(resolveRouterIdentity());
        addIfNotBlank(identities, properties.getBlipAgendamentoBotId());
        return new ArrayList<>(identities);
    }

    private void addIfNotBlank(Set<String> identities, String candidate) {
        if (candidate != null && !candidate.isBlank()) {
            identities.add(candidate.trim());
        }
    }

    private boolean hasDocuments(BlipTemplateResponse response) {
        return response != null
                && response.resource() != null
                && response.resource().documents() != null
                && !response.resource().documents().isEmpty();
    }

    private void logNamespaceHeaders(HttpHeaders headers, String commandUri, String toIdentity) {
        if (headers == null || headers.isEmpty()) {
            return;
        }

        headers.forEach((headerName, values) -> {
            if (headerName != null && headerName.toLowerCase().contains("namespace")) {
                log.info("Namespace header detectado. uri={}, to={}, header={}, values={}",
                        commandUri, toIdentity, headerName, values);
            }
        });
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

    private String resolveWabaNamespace() {
        String configuredNamespace = properties.getBlipWabaNamespace();
        if (configuredNamespace == null || configuredNamespace.isBlank()) {
            return FIXED_WABA_NAMESPACE;
        }
        return configuredNamespace;
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
