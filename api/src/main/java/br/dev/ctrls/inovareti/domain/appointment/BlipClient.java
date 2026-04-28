package br.dev.ctrls.inovareti.domain.appointment;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.dev.ctrls.inovareti.domain.appointment.dto.AppointmentTemplateData;
import br.dev.ctrls.inovareti.domain.appointment.dto.BlipTemplateDto;
import br.dev.ctrls.inovareti.domain.appointment.dto.BlipTemplateResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@ConditionalOnProperty(value = "app.appointment.motor.enabled", havingValue = "true", matchIfMissing = true)
public class BlipClient {

    /**
     * Realiza merge de extras no contato do Blip via JSON-RPC (/contacts).
     * identity: número do usuário (ex: 554199999999@wa.gw.msging.net)
     * extras: mapa de chaves e valores a serem persistidos no contato
     */
    public void mergeContactExtras(String identity, Map<String, String> extras) {
        if (identity == null || identity.isBlank() || extras == null || extras.isEmpty()) {
            log.warn("mergeContactExtras chamado com parâmetros inválidos. identity={}, extras={}", identity, extras);
            return;
        }

        String normalizedIdentity = normalizeUserIdentity(identity);
        String url = UriComponentsBuilder.fromUriString(properties.getBlipBaseUrl())
                .path(properties.getBlipSetContextPath())
                .build()
                .toUriString();

        Map<String, Object> resource = Map.of(
            "identity", normalizedIdentity,
            "extras", extras
        );

        Map<String, Object> payload = Map.of(
            "id", UUID.randomUUID().toString(),
            "to", MASTER_STATE_COMMAND_TO,
            "method", "merge",
            "uri", "/contacts",
            "type", "application/vnd.lime.contact+json",
            "resource", resource
        );

        try {
            ResponseEntity<Map<String, Object>> response = blipRestTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(payload, buildHeaders(AuthorizationScope.ROUTER)),
                new ParameterizedTypeReference<>() {}
            );
            log.info("Merge de extras enviado ao Blip. identity={}, extras={}, status={}, body={}",
                normalizedIdentity, extras, response.getStatusCode(), response.getBody());
        } catch (RestClientException ex) {
            log.error("Erro ao enviar mergeContactExtras para Blip. identity={}, extras={}", normalizedIdentity, extras, ex);
        }
    }

    /**
     * Loga bloqueio de envio por trava de segurança física.
     */
    public void logSecurityBlock(String phoneNumber, String appMode) {
        log.warn("BLOQUEIO DE SEGURANÇA: Tentativa de envio para {} ignorada (modo: {})", phoneNumber, appMode);
    }

    private enum AuthorizationScope {
        ROUTER,
        DESK
    }

    private static final String MASTER_STATE_COMMAND_TO = "postmaster@msging.net";
    private static final String THREAD_TRANSFER_COMMAND_TO = "postmaster@desk.msging.net";
    private static final String DEFAULT_ROUTER_IDENTITY = "postmaster@wa.gw.msging.net";
    private static final String DEFAULT_BUILDER_BOT_IDENTITY = "fluxov1@msging.net";
    private static final String DEFAULT_TEMPLATE_PARAMETER_VALUE = "Informação não disponível";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final AppointmentMotorProperties properties;
    private final AppointmentTemplateMappingRepository appointmentTemplateMappingRepository;
    private final AppointmentDoctorMappingRepository appointmentDoctorMappingRepository;
    private final AtomicLong lastRequestAt = new AtomicLong(0L);
    private RestTemplate blipRestTemplate;

    // Explicit constructor to avoid Lombok constructor dependency in IDEs without annotation processing
    public BlipClient(RestTemplate restTemplate,
                      ObjectMapper objectMapper,
                      AppointmentMotorProperties properties,
                      AppointmentTemplateMappingRepository appointmentTemplateMappingRepository,
                      AppointmentDoctorMappingRepository appointmentDoctorMappingRepository) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.appointmentTemplateMappingRepository = appointmentTemplateMappingRepository;
        this.appointmentDoctorMappingRepository = appointmentDoctorMappingRepository;
    }

    @Value("${APP_BLIP_APPOINTMENT_ID:itsminovare@msging.net}")
    private String blipAppointmentId;

    @Value("${app.appointment.motor.blip-waba-namespace}")
    private String blipWabaNamespace;

    @Value("${app.appointment.motor.blip-fallback-templates:}")
    private String blipFallbackTemplates;

    @Value("${app.appointment.motor.blip-router-key:}")
    private String routerKey;

    @Value("${app.appointment.motor.blip-desk-key:}")
    private String deskKey;

    @PostConstruct
    public void logBlipAuthKeyStatus() {
        logAuthorizationKeyStatus(AuthorizationScope.ROUTER);
        logAuthorizationKeyStatus(AuthorizationScope.DESK);
        try {
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(5000);
            requestFactory.setReadTimeout(5000);
            blipRestTemplate = new RestTemplate(requestFactory);
            // copy converters/interceptors/error handler from injected RestTemplate where possible
            blipRestTemplate.setMessageConverters(new ArrayList<>(restTemplate.getMessageConverters()));
            blipRestTemplate.setInterceptors(new ArrayList<>(restTemplate.getInterceptors()));
            blipRestTemplate.setErrorHandler(restTemplate.getErrorHandler());
            log.info("Blip RestTemplate configurado com timeout de 5s");
        } catch (Exception ex) {
            log.warn("Falha ao configurar Blip RestTemplate com timeout; usando RestTemplate injetado", ex);
            blipRestTemplate = restTemplate;
        }
    }

    public void sendTemplateMessage(String destination, String templateName, AppointmentTemplateData appointmentData) {
        String normalizedDestination = normalizeUserIdentity(destination);

        // Antes de enviar o template, força o usuário para o sub-bot de agendamentos.
        pullUserToAgendamentoBot(normalizedDestination);
        rateLimit();

        String url = UriComponentsBuilder.fromUriString(properties.getBlipBaseUrl())
                .path(properties.getBlipSendMessagePath())
                .build()
                .toUriString();


        List<Map<String, String>> parameters = buildDynamicParameters(templateName, appointmentData);
        String appointmentId = appointmentData == null
            ? ""
            : Objects.toString(appointmentData.appointmentId(), "");

        // Adiciona botão com payload
        Map<String, Object> button = Map.of(
            "type", "button",
            "sub_type", "quick_reply",
            "index", 0,
            "parameters", List.of(
                Map.of(
                    "type", "payload",
                    "payload", "confirm_" + appointmentId
                )
            )
        );

        List<Map<String, Object>> components = new ArrayList<>();
        components.add(Map.of(
            "type", "body",
            "parameters", parameters));
        components.add(button);

        Map<String, Object> content = Map.of(
            "type", "template",
            "template", Map.of(
                "name", templateName,
                "namespace", resolveWabaNamespace(),
                "language", Map.of(
                    "code", "pt_BR",
                    "policy", "deterministic"),
                "components", components));

        Map<String, Object> payload = Map.of(
            "id", UUID.randomUUID().toString(),
            "to", normalizedDestination,
            "type", "application/json",
            "content", content,
            "metadata", Map.of("appointmentId", appointmentId));

        HttpHeaders headers = buildHeaders(AuthorizationScope.ROUTER);
        if (log.isDebugEnabled()) {
            log.debug("Template payload enviado ao Blip. url={}, headers={}, body={}",
                    url,
                    sanitizeHeadersForDebug(headers),
                    toDebugJson(payload));
        }

        ResponseEntity<Map<String, Object>> response = blipRestTemplate.exchange(
            url,
            HttpMethod.POST,
            new HttpEntity<>(payload, headers),
            new ParameterizedTypeReference<>() {
            });

        Map<String, Object> responseBody = response.getBody();
        log.info("Resposta do Blip: Status={}, Body={}", response.getStatusCode(), responseBody);

        String messageId = extractBlipMessageId(responseBody);
        log.info("Mensagem enviada com sucesso. destination={}, templateName={}, messageId={}, appointmentId={}",
            normalizedDestination,
            templateName,
            messageId != null ? messageId : "[indisponivel]",
            appointmentId);
    }

    private List<Map<String, String>> buildDynamicParameters(String templateName, AppointmentTemplateData appointmentData) {
        List<AppointmentTemplateMapping> mappings = appointmentTemplateMappingRepository
            .findByTemplateNameIgnoreCaseOrderByPlaceholderIndexAsc(templateName);

        if (mappings.isEmpty()) {
            log.warn("Nenhum mapeamento dinâmico encontrado para templateName={}", templateName);
            return List.of();
        }

        List<Map<String, String>> parameters = new ArrayList<>();
        List<AppointmentTemplateMapping> orderedMappings = mappings.stream()
            .sorted(Comparator.comparing(AppointmentTemplateMapping::getPlaceholderIndex))
            .toList();

        for (AppointmentTemplateMapping mapping : orderedMappings) {
            String value = resolveDynamicFieldValue(appointmentData, mapping.getFeegowFieldName());
            String safeValue = applyTemplateParameterFallback(value, mapping.getFeegowFieldName(),
                mapping.getPlaceholderIndex());
            parameters.add(Map.of(
                "type", "text",
                "text", safeValue));
        }

        String feegowAppointmentId = appointmentData == null ? null : Objects.toString(appointmentData.appointmentId(), null);
        log.debug("Parâmetros do template enviados ao Blip (templateName={}, feegow_appointment_id={}): {}", templateName, feegowAppointmentId, parameters);
        return parameters;
    }

    private String resolveDynamicFieldValue(AppointmentTemplateData appointmentData, String fieldName) {
        if (appointmentData == null || fieldName == null || fieldName.isBlank()) {
            return null;
        }

        // Special handling for local professional name: fetch from appointment_doctor_mapping
        if ("profissionalNome".equalsIgnoreCase(fieldName.trim())) {
            try {
                String doctorId = appointmentData.doctorId();
                if (doctorId == null || doctorId.isBlank()) {
                    return null;
                }

                return appointmentDoctorMappingRepository
                        .findByProfissionalId(doctorId)
                        .map(AppointmentDoctorMapping::getProfissionalNome)
                        .filter(nome -> nome != null && !nome.isBlank())
                        .orElse(null);
            } catch (Exception ex) {
                log.warn("Falha ao recuperar profissionalNome local para doctorId={}: {}", appointmentData.doctorId(), ex.getMessage());
                return null;
            }
        }

        try {
            Method accessor = AppointmentTemplateData.class.getMethod(fieldName.trim());
            Object value = accessor.invoke(appointmentData);
            return value == null ? null : String.valueOf(value);
        } catch (ReflectiveOperationException ex) {
            log.warn("Campo dinâmico inválido no mapeamento. fieldName={}", fieldName, ex);
            return null;
        }
    }

    private String applyTemplateParameterFallback(String value, String fieldName, Integer placeholderIndex) {
        if (value != null && !value.isBlank()) {
            return value.trim();
        }

        log.warn(
            "Valor vazio em variável de template. placeholderIndex={}, fieldName={}, fallback={}",
            placeholderIndex,
            fieldName,
            DEFAULT_TEMPLATE_PARAMETER_VALUE);
        return DEFAULT_TEMPLATE_PARAMETER_VALUE;
    }

    public void setHandoffContext(String destination, String queueId) {
        log.info("Iniciando Handoff (Transferência para Desk) para destination={}, queueId={}", destination, queueId);
        transferThreadToQueue(destination, queueId);
    }

    public void transferThreadToQueue(String destination, String queueId) {
        String normalizedDestination = normalizeUserIdentity(destination);
        if (queueId == null || queueId.isBlank()) {
            log.warn("Transferência de thread ignorada: queueId vazio. destination={}", normalizedDestination);
            return;
        }

        rateLimit();

        String url = UriComponentsBuilder.fromUriString(properties.getBlipBaseUrl())
                .path(properties.getBlipSetContextPath())
                .build()
                .toUriString();

        Map<String, Object> payload = Map.of(
            "id", UUID.randomUUID().toString(),
            "to", THREAD_TRANSFER_COMMAND_TO,
            "method", "set",
            "uri", "/threads/" + normalizedDestination,
            "type", "application/vnd.iris.thread+json",
            "resource", Map.of("ownerIdentity", queueId.trim()));

        ResponseEntity<Map<String, Object>> response = blipRestTemplate.exchange(
            url,
            HttpMethod.POST,
            new HttpEntity<>(payload, buildHeaders(AuthorizationScope.DESK)),
            new ParameterizedTypeReference<>() {
            });

        log.info("Comando /threads enviado ao Blip. destination={}, queueId={}, status={}, body={}",
            normalizedDestination,
            queueId,
            response.getStatusCode(),
            response.getBody());
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
        String normalizedDestination = normalizeUserIdentity(destination);
        rateLimit();

        String url = UriComponentsBuilder.fromUriString(properties.getBlipBaseUrl())
                .path(properties.getBlipSendMessagePath())
                .build()
                .toUriString();

        Map<String, Object> payload = Map.of(
            "to", normalizedDestination,
                "type", "text/plain",
                "content", text);

        blipRestTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(payload, buildHeaders(AuthorizationScope.ROUTER)), Void.class);
        log.info("Mensagem textual enviada ao Blip. destination={}", normalizedDestination);
    }

    /**
     * Direciona o usuário para o sub-bot de agendamentos (Master-State).
     */
    public void pullUserToAgendamentoBot(String userEmail) {
        // Use APP_BLIP_APPOINTMENT_ID as the target bot for appointment master-state
        setMasterState(userEmail, null, "agendamentos");
        setUserState(userEmail, "9bc2484e-fbfc-4c9d-9a19-a20be6286b1b");
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
        log.debug("Iniciando diagnóstico de templates com identidades candidatas: {}", candidateIdentities);

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

                // Diagnóstico adicional: mostrar se há chaves configuradas e uma versão mascarada delas
                String routerResolved = normalizeAuthorizationKey(firstNonBlank(routerKey, properties.getBlipRouterKey()));
                String deskResolved = normalizeAuthorizationKey(firstNonBlank(deskKey, properties.getBlipDeskKey()));
                String routerMasked = routerResolved == null ? "[none]" : maskAuthorizationToken("Key " + routerResolved);
                String deskMasked = deskResolved == null ? "[none]" : maskAuthorizationToken("Key " + deskResolved);
                log.info("Blip templates diagnostics: routerKeyPresent={}, deskKeyPresent={}, routerKeyMasked={}, deskKeyMasked={}",
                    routerResolved != null && !routerResolved.isBlank(),
                    deskResolved != null && !deskResolved.isBlank(),
                    routerMasked,
                    deskMasked);

                return staticTemplateFallback();

        } catch (HttpStatusCodeException httpEx) {
            log.error("Erro HTTP ao buscar templates do Blip. statusCode={}, responseBody={}",
                    httpEx.getStatusCode(), httpEx.getResponseBodyAsString(), httpEx);
            return staticTemplateFallback();
        } catch (RestClientException ex) {
            log.error("Erro inesperado ao buscar templates do Blip", ex);
            return staticTemplateFallback();
        }
    }

    public Map<String, Object> getBlipQueues() {
        Map<String, Object> response = sendDeskCommand("/attendance-queues");

        if (isResponseEmptyOrFailed(response)) {
            log.info("Comando /attendance-queues falhou ou retornou vazio. Tentando fallback para /queues...");
            Map<String, Object> fallbackResponse = sendDeskCommand("/queues");
            if (isResponseEmptyOrFailed(fallbackResponse)) {
                log.info("Fallback /queues também falhou. Tentando /teams...");
                Map<String, Object> teamsFallback = sendDeskCommand("/teams");
                if (teamsFallback != null) return teamsFallback;
            } else {
                return fallbackResponse;
            }
        }

        return response != null ? response : Map.of("status", "error", "reason", "failed-to-retrieve");
    }

    public record BlipQueue(String id, String name) {}

    public List<BlipQueue> listBlipQueues() {
        Map<String, Object> raw = getBlipQueues();
        if (raw == null) return List.of();

        Object resource = raw.get("resource");
        List<BlipQueue> result = new ArrayList<>();

        switch (resource) {
            case Map<?, ?> resourceMap -> {
                Object itemsObj = resourceMap.get("items");
                if (itemsObj instanceof List<?> items) {
                    for (Object item : items) {
                        if (!(item instanceof Map<?, ?> itemMap)) continue;
                        String id = valueAsString(itemMap.get("id"));
                        String name = valueAsString(itemMap.get("name"));
                        if (name == null) name = valueAsString(itemMap.get("title"));
                        if (name == null && id != null) name = id;
                        if (name != null) result.add(new BlipQueue(id == null ? "" : id, name));
                    }
                }
            }
            case List<?> resourceList -> {
                for (Object item : resourceList) {
                    if (!(item instanceof Map<?, ?> itemMap)) continue;
                    String id = valueAsString(itemMap.get("id"));
                    String name = valueAsString(itemMap.get("name"));
                    if (name == null) name = valueAsString(itemMap.get("title"));
                    if (name == null && id != null) name = id;
                    if (name != null) result.add(new BlipQueue(id == null ? "" : id, name));
                }
            }
            default -> {}
        }

        return result.stream()
                .sorted(Comparator.comparing(BlipQueue::name, Comparator.nullsLast(String::compareToIgnoreCase)))
                .collect(Collectors.toList());
    }

    private Map<String, Object> sendDeskCommand(String uri) {
        rateLimit();

        String url = UriComponentsBuilder.fromUriString(properties.getBlipBaseUrl())
                .path(properties.getBlipSetContextPath())
                .build()
                .toUriString();

        Map<String, Object> command = Map.of(
                "id", UUID.randomUUID().toString(),
                "to", THREAD_TRANSFER_COMMAND_TO,
                "method", "get",
                "uri", uri);

        try {
            ResponseEntity<Map<String, Object>> response = blipRestTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(command, buildHeaders(AuthorizationScope.DESK)),
                    new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            return response.getBody();
        } catch (RestClientException ex) {
            log.warn("Erro ao buscar {} do Blip Desk.", uri, ex);
            return null;
        }
    }

    /**
     * Recupera os 'extras' do contato no Blip (se houver).
     */
    public Map<String, String> getContactExtras(String identity) {
        if (identity == null || identity.isBlank()) {
            return Map.of();
        }

        String normalized = normalizeUserIdentity(identity);
        rateLimit();

        String url = UriComponentsBuilder.fromUriString(properties.getBlipBaseUrl())
                .path(properties.getBlipSetContextPath())
                .build()
                .toUriString();

        Map<String, Object> command = Map.of(
                "id", UUID.randomUUID().toString(),
                "to", resolveRouterIdentity(),
                "method", "get",
                "uri", "/contacts/" + normalized);

        try {
                ResponseEntity<Map<String, Object>> response = blipRestTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(command, buildHeaders(AuthorizationScope.ROUTER)),
                    new ParameterizedTypeReference<>() {
                    });

            Map<String, Object> body = response.getBody();
            if (body == null) return Map.of();

            Object resource = body.get("resource");
            if (!(resource instanceof Map<?, ?> resourceMap)) return Map.of();

            Object extrasObj = resourceMap.get("extras");
            if (!(extrasObj instanceof Map<?, ?> extrasMap)) return Map.of();

            Map<String, String> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : extrasMap.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    result.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
                }
            }

            return result;
        } catch (RestClientException ex) {
            log.warn("Erro ao recuperar contact extras do Blip para {}: {}", normalized, ex.getMessage());
            return Map.of();
        }
    }

    private boolean isResponseEmptyOrFailed(Map<String, Object> response) {
        if (response == null) {
            return true;
        }
        if (!"success".equalsIgnoreCase(String.valueOf(response.get("status")))) {
            return true;
        }

        Object resource = response.get("resource");
        if (resource == null) {
            return true;
        }

        if (resource instanceof Map) {
            Map<?, ?> resourceMap = (Map<?, ?>) resource;
            Object items = resourceMap.get("items");
            if (items instanceof List) {
                List<?> itemsList = (List<?>) items;
                return itemsList.isEmpty();
            }
            return resourceMap.isEmpty();
        } else if (resource instanceof List) {
            List<?> resourceList = (List<?>) resource;
            return resourceList.isEmpty();
        }

        return false;
    }

    private BlipTemplateResponse fetchTemplatesByUri(String commandUri, String toIdentity) {
        rateLimit();

        String url = UriComponentsBuilder.fromUriString(properties.getBlipBaseUrl())
                .path(properties.getBlipSetContextPath())
                .build()
                .toUriString();

        Map<String, Object> command = Map.of(
            "id", UUID.randomUUID().toString(),
            // For WhatsApp templates fetch, always target the WA gateway identity
            "to", DEFAULT_ROUTER_IDENTITY,
            "method", "get",
            "uri", commandUri);

        log.info("Comando JSON-RPC enviado ao Blip: {}", command);

        HttpHeaders headers = buildHeaders(AuthorizationScope.ROUTER);
        // Log which (masked) key is being used and the target identity to aid diagnostics
        String resolvedKey = normalizeAuthorizationKey(resolveAuthorizationKey(AuthorizationScope.ROUTER));
        String maskedKey = resolvedKey == null ? "[none]" : maskAuthorizationToken("Key " + resolvedKey);
        log.info("Buscando templates no Blip. uri={}, toIdentity={}, usedAuthMasked={}, headers={}", commandUri, toIdentity, maskedKey, sanitizeHeadersForDebug(headers));

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(command, headers);

        ResponseEntity<BlipTemplateResponse> response = blipRestTemplate.exchange(
            url,
            HttpMethod.POST,
            request,
            new ParameterizedTypeReference<BlipTemplateResponse>() {
            });

        logNamespaceHeaders(response.getHeaders(), commandUri, toIdentity);

        BlipTemplateResponse responseBody = response.getBody();
        log.debug("Blip Raw Response [uri={}, to={}]: {}", commandUri, toIdentity, responseBody);

        if (responseBody != null && "failure".equalsIgnoreCase(responseBody.status())) {
            log.warn("Blip retornou status=failure para uri={} toIdentity={}. responseHeaders={}, usedHeaders={}. Suggestion: verify the router key has admin rights and that identity {} exposes /message-templates for the namespace.",
                commandUri, toIdentity, response.getHeaders(), sanitizeHeadersForDebug(headers), toIdentity);
        }

        return responseBody;
    }

    private List<BlipTemplateDto> staticTemplateFallback() {
        if (blipFallbackTemplates == null || blipFallbackTemplates.isBlank()) {
            return List.of();
        }

        return java.util.Arrays.stream(blipFallbackTemplates.split(","))
                .map(String::trim)
                .filter(name -> !name.isBlank())
                .map(name -> new BlipTemplateDto(name, name))
                .toList();
    }

    private String extractBlipMessageId(Map<String, Object> responseBody) {
        if (responseBody == null || responseBody.isEmpty()) {
            return null;
        }

        String id = valueAsString(responseBody.get("id"));
        if (id != null && !id.isBlank()) {
            return id;
        }

        String messageId = valueAsString(responseBody.get("messageId"));
        if (messageId != null && !messageId.isBlank()) {
            return messageId;
        }

        Object resource = responseBody.get("resource");
        if (resource instanceof Map<?, ?> resourceMap) {
            String resourceMessageId = valueAsString(resourceMap.get("messageId"));
            if (resourceMessageId != null && !resourceMessageId.isBlank()) {
                return resourceMessageId;
            }

            String resourceId = valueAsString(resourceMap.get("id"));
            if (resourceId != null && !resourceId.isBlank()) {
                return resourceId;
            }
        }

        return null;
    }

    private String valueAsString(Object value) {
        if (value == null) {
            return null;
        }

        return String.valueOf(value);
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

    private HttpHeaders buildHeaders(AuthorizationScope scope) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        String authorizationKey = normalizeAuthorizationKey(resolveAuthorizationKey(scope));
        if (authorizationKey == null || authorizationKey.isBlank()) {
            log.warn("Chave de autorização do Blip está vazia para scope={}. Verifique as propriedades app.appointment.motor.blip-router-key e app.appointment.motor.blip-desk-key.", scope);
            return headers;
        }
        headers.set("Authorization", "Key " + authorizationKey);
        log.debug("Headers enviados para Blip. scope={}, keys={}", scope, headers.toSingleValueMap().keySet());
        return headers;
    }

    private void logAuthorizationKeyStatus(AuthorizationScope scope) {
        String resolved = normalizeAuthorizationKey(resolveAuthorizationKey(scope));
        if (resolved == null || resolved.isBlank()) {
            log.error("Chave do Blip não foi carregada para scope={}. Verifique as variáveis APP_APPOINTMENT_BLIP_ROUTER_KEY e APP_APPOINTMENT_BLIP_DESK_KEY.", scope);
            return;
        }

        int visibleCharacters = Math.min(5, resolved.length());
        String prefix = resolved.substring(0, visibleCharacters);
        log.info("Configuração carregada para scope={}: {}", scope, prefix);
    }

    private String resolveAuthorizationKey(AuthorizationScope scope) {
        return switch (scope) {
            case ROUTER -> firstNonBlank(routerKey, properties.getBlipRouterKey());
            case DESK -> firstNonBlank(deskKey, properties.getBlipDeskKey());
        };
    }

    private String firstNonBlank(String... values) {
        if (values == null || values.length == 0) {
            return null;
        }

        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }

        return null;
    }

    private String normalizeAuthorizationKey(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        if (normalized.regionMatches(true, 0, "Key ", 0, 4)) {
            normalized = normalized.substring(4).trim();
        }
        if (normalized.regionMatches(true, 0, "Bearer ", 0, 7)) {
            normalized = normalized.substring(7).trim();
        }

        return normalized;
    }

    private Map<String, String> sanitizeHeadersForDebug(HttpHeaders headers) {
        Map<String, String> singleValueHeaders = new LinkedHashMap<>(headers.toSingleValueMap());
        for (Map.Entry<String, String> entry : singleValueHeaders.entrySet()) {
            if (entry.getKey() != null && HttpHeaders.AUTHORIZATION.equalsIgnoreCase(entry.getKey())) {
                singleValueHeaders.put(entry.getKey(), maskAuthorizationToken(entry.getValue()));
            }
        }

        return singleValueHeaders;
    }

    private String maskAuthorizationToken(String authorizationValue) {
        if (authorizationValue == null || authorizationValue.isBlank()) {
            return authorizationValue;
        }

        String trimmed = authorizationValue.trim();
        int firstSpace = trimmed.indexOf(' ');
        if (firstSpace <= 0) {
            return "********";
        }

        String scheme = trimmed.substring(0, firstSpace + 1);
        return scheme + "********";
    }

    private String toDebugJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            log.warn("Falha ao serializar payload de debug do Blip. Usando toString().", ex);
            return String.valueOf(payload);
        }
    }

    private void setMasterState(String userIdentity, String botIdentity, String operation) {
        // TargetBot dinâmico: se botIdentity for passado (ex: voltar para o builder), usa ele. 
        // Caso contrário, usa a variável de ambiente APP_BLIP_APPOINTMENT_ID.
        String targetBot = firstNonBlank(botIdentity, blipAppointmentId);

        String normalizedIdentity = normalizeUserIdentity(userIdentity);

        String url = UriComponentsBuilder.fromUriString(properties.getBlipBaseUrl())
            .path(properties.getBlipSetContextPath())
            .build()
            .toUriString();

        // Build the context state URI in the form /contexts/{identity}/<stateId%40flowId>
        String stateId = firstNonBlank(operation, "stateid");
        String flowIdentity = firstNonBlank(targetBot, "");
        String flowId = flowIdentity.contains("@") ? flowIdentity.substring(0, flowIdentity.indexOf('@')) : flowIdentity;
        String combined = stateId + "@" + flowId;
        String encodedState = java.net.URLEncoder.encode(combined, java.nio.charset.StandardCharsets.UTF_8);

        String uri = "/contexts/" + normalizedIdentity + "/" + encodedState;

        Map<String, Object> command = Map.of(
            "id", UUID.randomUUID().toString(),
            "to", MASTER_STATE_COMMAND_TO,
            "method", "set",
            "uri", uri,
            "type", "text/plain",
            "metadata", Map.of("expiration", "86400"),
            "resource", targetBot);

        try {
            rateLimit();
            blipRestTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(command, buildHeaders(AuthorizationScope.ROUTER)), Void.class);
            log.info("Master-State atualizado com sucesso. operation={}, userIdentity={}, targetBot={}",
                    operation, normalizedIdentity, targetBot);
        } catch (HttpStatusCodeException httpEx) {
            log.error("Erro HTTP ao atualizar Master-State. operation={}, statusCode={}, responseBody={}",
                    operation, httpEx.getStatusCode(), httpEx.getResponseBodyAsString(), httpEx);
        } catch (RestClientException ex) {
            log.error("Erro inesperado ao atualizar Master-State. operation={}", operation, ex);
        }
    }

    private void setUserState(String userIdentity, String stateName) {
        String normalizedIdentity = normalizeUserIdentity(userIdentity);
        
        log.info("Enviando comando de estado ({}) para a identidade do usuário: {}", stateName, normalizedIdentity);

        String url = UriComponentsBuilder.fromUriString(properties.getBlipBaseUrl())
                .path(properties.getBlipSetContextPath())
                .build()
                .toUriString();

        Map<String, Object> command = Map.of(
                "id", UUID.randomUUID().toString(),
                "to", MASTER_STATE_COMMAND_TO,
                "method", "set",
                "uri", "/contexts/" + normalizedIdentity + "/state",
                "type", "text/plain",
                "resource", stateName);

        try {
            rateLimit();
            blipRestTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(command, buildHeaders(AuthorizationScope.ROUTER)), Void.class);
            log.info("User State atualizado com sucesso. userIdentity={}, stateName={}", normalizedIdentity, stateName);
        } catch (RestClientException ex) {
            log.error("Erro inesperado ao atualizar User State. userIdentity={}, stateName={}", normalizedIdentity, stateName, ex);
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
        String configuredNamespace = blipWabaNamespace;
        if (configuredNamespace == null || configuredNamespace.isBlank()) {
            return "";
        }
        return configuredNamespace;
    }

    private String normalizeUserIdentity(String userIdentity) {
        if (userIdentity == null || userIdentity.isBlank()) {
            return "unknown@wa.gw.msging.net";
        }

        String sanitized = userIdentity.trim();
        if (sanitized.contains("@")) {
            int separatorIndex = sanitized.indexOf('@');
            String localPart = separatorIndex >= 0 ? sanitized.substring(0, separatorIndex) : sanitized;
            String domainPart = separatorIndex >= 0 ? sanitized.substring(separatorIndex + 1) : "";

            if (!"wa.gw.msging.net".equalsIgnoreCase(domainPart)) {
                return sanitized;
            }

            String normalizedLocalPart = normalizePhoneIdentityValue(localPart);
            if (normalizedLocalPart.isBlank()) {
                return "unknown@wa.gw.msging.net";
            }

            String identity = normalizedLocalPart + "@wa.gw.msging.net";
            log.info("Preparando disparo Blip -> Destinatário: {}", identity);
            return identity;
        }

        String normalizedLocalPart = normalizePhoneIdentityValue(sanitized);
        if (normalizedLocalPart.isBlank()) {
            return "unknown@wa.gw.msging.net";
        }

        String identity = normalizedLocalPart + "@wa.gw.msging.net";
        log.info("Preparando disparo Blip -> Destinatário: {}", identity);
        return identity;
    }

    private String normalizePhoneIdentityValue(String phone) {
        if (phone == null || phone.isBlank()) {
            return "";
        }

        String trimmed = phone.trim();
        if (trimmed.startsWith("+")) {
            String digits = trimmed.substring(1).replaceAll("[^0-9]", "");
            if (digits.isBlank()) {
                return "";
            }

            return digits;
        }

        String digitsOnly = trimmed.replaceAll("[^0-9]", "");
        if (digitsOnly.isBlank()) {
            return "";
        }

        return digitsOnly;
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