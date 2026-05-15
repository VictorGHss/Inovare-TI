package br.dev.ctrls.inovareti.domain.appointment;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.data.redis.core.StringRedisTemplate;

import br.dev.ctrls.inovareti.domain.appointment.dto.FeegowPatientDetailsDto;
import br.dev.ctrls.inovareti.domain.appointment.dto.FeegowSearchResponseDto;
// removed PostConstruct diagnostic usage (startup unit discovery removed)
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@ConditionalOnProperty(value = "app.appointment.motor.enabled", havingValue = "true", matchIfMissing = true)
public class FeegowClient {

    private static final DateTimeFormatter FEEGOW_RESPONSE_DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter FEEGOW_RESPONSE_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter FEEGOW_RESPONSE_TIME_WITH_SECONDS_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final RestTemplate restTemplate;
    private final AppointmentMotorProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient feegowRestClient;
    private final StringRedisTemplate stringRedisTemplate;

    public FeegowClient(
            @Qualifier("feegowRestTemplate") RestTemplate restTemplate,
            AppointmentMotorProperties properties,
            ObjectMapper objectMapper,
            @Qualifier("feegowRestClient") ObjectProvider<RestClient> feegowRestClientProvider,
            ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider) {
        this.restTemplate = restTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.feegowRestClient = feegowRestClientProvider.getIfAvailable();
        this.stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
    }

    @Value("${app.feegow.api-key}")
    private String apiKey;

    @Value("${app.feegow.unidade-id}")
    private String feegowUnidadeId;


    public void logFeegowApiKeyStatus() {
        String normalizedApiKey = normalizeApiKey(apiKey);
        if (normalizedApiKey == null || normalizedApiKey.isBlank()) {
            log.error("ERRO FATAL NO BOOT: Token da Feegow (x-access-token) está nulo ou vazio. Verifique a variável APP_FEEGOW_API_KEY!");
            return;
        }
        log.info("Configuração carregada: token de autenticação Feegow disponível para x-access-token.");
    }

    public List<FeegowAppointment> searchAppointments(LocalDate date, int statusId) {
        return searchAppointments(date, statusId, null);
    }

    public List<FeegowAppointment> searchAppointments(LocalDate date, int statusId, String profissionalId) {
        LocalDate effectiveDate = date != null ? date : LocalDate.now();
        String formattedDate = effectiveDate.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(properties.getFeegowBaseUrl())
                .path(properties.getFeegowSearchPath())
                .queryParam("data_start", formattedDate)
                .queryParam("data_end", formattedDate)
                .queryParam("status", statusId);

        if (profissionalId != null && !profissionalId.isBlank()) {
            uriBuilder.queryParam("profissional_id", profissionalId.trim());
        }

        String url = uriBuilder.build().toUriString();

        HttpHeaders headers = buildHeaders();

        ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);

        List<FeegowSearchResponseDto.FeegowSearchAppointmentDto> searchItems = extractSearchItems(response.getBody());
        if (searchItems.isEmpty()) {
            return List.of();
        }

        List<FeegowAppointment> appointments = new ArrayList<>();
        for (FeegowSearchResponseDto.FeegowSearchAppointmentDto item : searchItems) {
            FeegowAppointment parsedAppointment = parseAppointment(item);
            if (parsedAppointment == null) {
                continue;
            }

            appointments.add(parsedAppointment);
        }
        return appointments;
    }

    private List<FeegowSearchResponseDto.FeegowSearchAppointmentDto> extractSearchItems(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return List.of();
        }

        try {
            Object root = objectMapper.readValue(responseBody, Object.class);

            if (root instanceof List<?> listRoot) {
                List<FeegowSearchResponseDto.FeegowSearchAppointmentDto> converted = objectMapper.convertValue(
                        listRoot,
                        new TypeReference<List<FeegowSearchResponseDto.FeegowSearchAppointmentDto>>() {
                        });
                return converted != null ? converted : List.of();
            }

            if (root instanceof Map<?, ?> mapObj) {
                @SuppressWarnings("unchecked")
                Map<String, Object> rootMap = (Map<String, Object>) mapObj;
                FeegowSearchResponseDto mappedResponse = objectMapper.convertValue(rootMap, FeegowSearchResponseDto.class);
                List<FeegowSearchResponseDto.FeegowSearchAppointmentDto> appointments = mappedResponse != null
                        ? mappedResponse.appointments()
                        : List.of();

                if (appointments.isEmpty()) {
                    log.warn("Resposta da busca Feegow sem itens em 'content' ou 'data'. keys={}", topLevelKeys(rootMap));
                }

                return appointments;
            }

            log.warn("Formato raiz JSON da busca Feegow inesperado: {}", root.getClass().getName());
            return List.of();
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Falha ao converter JSON da busca Feegow.", ex);
        }
    }

    private List<String> topLevelKeys(Map<String, Object> root) {
        if (root == null || root.isEmpty()) {
            return List.of();
        }

        return new ArrayList<>(root.keySet());
    }

    public FeegowPatient patientInfo(String patientId) {
        FeegowPatientDetailsDto.PatientItem patientDetails = getPatientDetails(patientId);
        if (patientDetails == null) {
            return new FeegowPatient(patientId, null, null, null, null);
        }

        String resolvedPatientId = patientDetails.getId() == null || patientDetails.getId().isBlank()
                ? patientId
                : patientDetails.getId();

        return new FeegowPatient(
                resolvedPatientId,
                patientDetails.getNome(),
                resolvePreferredPhone(patientDetails),
                sanitizeCpf(patientDetails.getCpf()),
                patientDetails.getNascimento());
    }

    public String sanitizeCpf(String cpf) {
        if (cpf == null || cpf.isBlank()) {
            return "";
        }
        return cpf.replaceAll("\\D", "");
    }

    public FeegowPatientDetailsDto.PatientItem getPatientDetails(String patientId) {
        String url = UriComponentsBuilder.fromUriString(properties.getFeegowBaseUrl())
                .path(resolvePatientDetailsPath())
                .queryParam("paciente_id", patientId)
                .toUriString();

        log.info("[FEEGOW] Buscando detalhes do paciente ID: {} na URL: {}", patientId, url);

        HttpHeaders headers = buildHeaders();

        ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);

        return extractPatientDetails(response.getBody());
    }

    /**
     * Busca o nome do profissional na API da Feegow e faz cache em Redis por 24h.
     */
    public String getProfessionalName(String professionalId) {
        if (professionalId == null || professionalId.isBlank()) {
            return null;
        }

        String id = professionalId.trim();
        String cacheKey = "feegow:professional:name:" + id;

        try {
            if (stringRedisTemplate != null) {
                String cached = stringRedisTemplate.opsForValue().get(cacheKey);
                if (cached != null && !cached.isBlank()) {
                    return cached;
                }
            }
        } catch (RuntimeException ex) {
            log.warn("Falha ao verificar cache Redis para professional name id={}: {}", id, ex.getMessage());
        }

        String configuredPath = properties.getFeegowProfessionalPath();
        String resolvedPath = (configuredPath == null || configuredPath.isBlank())
            ? "/v1/api/professional/list"
            : configuredPath;

        String unidadeId = resolveUnidadeId();

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(properties.getFeegowBaseUrl())
            .path(resolvedPath)
            .queryParam("profissional_id", id)
            .queryParam("ativo", "1")
            .queryParam("pagina", "1")
            .queryParam("start", "0")
            .queryParam("offset", "50");

        if (unidadeId != null && !unidadeId.isBlank()) {
            uriBuilder.queryParam("unidade_id", unidadeId);
        }

        String url = uriBuilder.build().toUriString();

        HttpHeaders headers = buildHeaders();
        // Log sanitized outgoing headers to aid diagnosis (mask x-access-token)
        try {
            Map<String, String> sanitized = new java.util.LinkedHashMap<>(headers.toSingleValueMap());
            if (sanitized.containsKey("x-access-token")) {
                sanitized.put("x-access-token", maskToken(sanitized.get("x-access-token")));
            }
            log.info("Feegow outgoing headers (sanitized) for professional lookup: {}", sanitized);
        } catch (Exception e) {
            log.warn("Falha ao serializar headers para log de diagnóstico Feegow: {}", e.getMessage());
        }
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            String body = response.getBody();
            if (body == null || body.isBlank()) {
                return null;
            }

            Object root;
            try {
                root = objectMapper.readValue(body, Object.class);
            } catch (JsonProcessingException jex) {
                log.warn("Falha ao parsear resposta Feegow (professional id={}) como JSON: {}", id, jex.getMessage());
                return null;
            }

            String resolved = extractProfessionalName(root);
            if (resolved != null && !resolved.isBlank()) {
                try {
                    if (stringRedisTemplate != null) {
                        stringRedisTemplate.opsForValue().set(cacheKey, resolved.trim(), java.time.Duration.ofHours(24));
                    }
                } catch (RuntimeException ex) {
                    log.warn("Falha ao gravar cache Redis para professional name id={}: {}", id, ex.getMessage());
                }
                return resolved.trim();
            }
        } catch (RestClientResponseException ex) {
            // Log body and headers to aid diagnosis and rethrow to be handled globally when appropriate
            log.error("Falha HTTP ao buscar nome do profissional na Feegow para id={}. status={}, responseBody={}, responseHeaders={}",
                    id, ex.getStatusCode().value(), abbreviateResponseBody(ex.getResponseBodyAsString()), ex.getResponseHeaders());
            // If Feegow returned 422, retry once with empty unidade_id (some instances expect unidade_id=)
            try {
                if (ex.getStatusCode() != null && ex.getStatusCode().value() == 422) {
                    // Detailed diagnostic to help identify if token lacks required scopes
                    try {
                        Map<String, String> sanitizedOutgoing = new java.util.LinkedHashMap<>(headers.toSingleValueMap());
                        if (sanitizedOutgoing.containsKey("x-access-token")) {
                            sanitizedOutgoing.put("x-access-token", maskToken(sanitizedOutgoing.get("x-access-token")));
                        }
                        log.error("Feegow returned 422 (Unprocessable Entity) for professional lookup id={}. Possible missing token scope or permissions. responseHeaders={}, sanitizedOutgoingHeaders={}",
                                id, ex.getResponseHeaders(), sanitizedOutgoing);
                    } catch (Exception diagEx) {
                        log.warn("Falha ao montar diagnóstico adicional para 422 Feegow: {}", diagEx.getMessage());
                    }
                    String retryUrl = UriComponentsBuilder.fromUriString(properties.getFeegowBaseUrl())
                            .path(resolvedPath)
                            .queryParam("profissional_id", id)
                            .queryParam("ativo", "1")
                            .queryParam("pagina", "1")
                            .queryParam("unidade_id", "")
                            .build()
                            .toUriString();

                    log.info("Feegow returned 422 for professional lookup; retrying with empty unidade_id. retryUrl={}", retryUrl);
                    ResponseEntity<String> retryResponse = restTemplate.exchange(retryUrl, HttpMethod.GET, new HttpEntity<>(headers), String.class);
                    String retryBody = retryResponse.getBody();
                    if (retryBody == null || retryBody.isBlank()) {
                        return null;
                    }
                    Object retryRoot = objectMapper.readValue(retryBody, Object.class);
                    return extractProfessionalName(retryRoot);
                }
            } catch (org.springframework.web.client.RestClientException | com.fasterxml.jackson.core.JsonProcessingException retryEx) {
                log.warn("Retry with empty unidade_id failed for professional id={}: {}", id, retryEx.getMessage());
            }

            throw ex;
        } catch (RestClientException ex) {
            log.warn("Falha ao buscar nome do profissional na Feegow para id={}: {}", id, ex.getMessage());
        }

        return null;
    }

    private String extractProfessionalName(Object root) {
        if (root == null) {
            return null;
        }

        try {
            if (root instanceof Map<?, ?> map) {
                Object content = map.get("content");
                if (content instanceof List<?> list && !list.isEmpty()) {
                    Object first = list.get(0);
                    if (first instanceof Map<?, ?> row) {
                        Object tratamento = row.get("tratamento");
                        Object nome = row.get("nome");
                        if (nome != null) {
                            String prefix = (tratamento != null && !tratamento.toString().isBlank()) ? tratamento.toString().trim() + " " : "";
                            return prefix + nome.toString().trim();
                        }
                    }
                }

                Object dados = map.get("dados");
                if (dados instanceof List<?> dlist && !dlist.isEmpty()) {
                    Object first = dlist.get(0);
                    if (first instanceof Map<?, ?> row) {
                        Object tratamento = row.get("tratamento");
                        Object nome = row.get("nome");
                        if (nome != null) {
                            String prefix = (tratamento != null && !tratamento.toString().isBlank()) ? tratamento.toString().trim() + " " : "";
                            return prefix + nome.toString().trim();
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Erro ao extrair nome do profissional da resposta Feegow: {}", e.getMessage());
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private List<ProfessionalDTO> extractProfessionalItemsFromLooseJson(Object root) {
        if (root == null) {
            return List.of();
        }
        try {
            if (root instanceof Map<?, ?> map) {
                Object dados = ((Map<String, Object>) map).get("dados");
                if (dados instanceof List<?>) {
                    return objectMapper.convertValue(dados, new TypeReference<List<ProfessionalDTO>>() { });
                }
            }
            if (root instanceof List<?>) {
                return objectMapper.convertValue(root, new TypeReference<List<ProfessionalDTO>>() { });
            }
        } catch (IllegalArgumentException ex) {
            log.warn("Falha ao converter lista de profissionais (fallback JSON): {}", ex.getMessage());
        }
        return List.of();
    }

    /**
     * Lista todos os profissionais disponíveis na Feegow (id + nome).
     */
    public List<FeegowProfessional> listProfessionals() {
        String configuredPath = properties.getFeegowProfessionalPath();
        String resolvedPath = (configuredPath == null || configuredPath.isBlank())
            ? "/v1/api/professional/list"
            : configuredPath;

        // Always send unidade_id=0 and pagination params required by Feegow v1
        String localId = resolveLocalId();

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(properties.getFeegowBaseUrl())
            .path(resolvedPath)
            .queryParam("unidade_id", "0")
            .queryParam("ativo", "1")
            .queryParam("start", "0")
            .queryParam("offset", "50");

        // local_id remains optional and will be appended when present
        if (localId != null && !localId.isBlank()) {
            uriBuilder.queryParam("local_id", localId);
        }

        String url = uriBuilder.build().toUriString();

        HttpHeaders headers = buildHeaders();

        // Log sanitized outgoing headers (mask token) to help diagnose Feegow errors
        try {
            Map<String, String> sanitized = new java.util.LinkedHashMap<>(headers.toSingleValueMap());
            if (sanitized.containsKey("x-access-token")) {
                sanitized.put("x-access-token", maskToken(sanitized.get("x-access-token")));
            }
            log.info("Feegow outgoing headers (sanitized) for listing professionals: {}", sanitized);
        } catch (Exception e) {
            log.warn("Falha ao serializar headers para log de diagnóstico Feegow: {}", e.getMessage());
        }

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            String body = response.getBody();
            if (body == null || body.isBlank()) {
                return List.of();
            }

            try {
                FeegowResponseDTO dto = objectMapper.readValue(body, FeegowResponseDTO.class);
                List<ProfessionalDTO> items = List.of();
                if (dto != null && dto.content() != null && !dto.content().isEmpty()) {
                    items = dto.content();
                } else {
                    // fallback: attempt to parse legacy shapes (dados or raw array)
                    Object root = objectMapper.readValue(body, Object.class);
                    items = extractProfessionalItemsFromLooseJson(root);
                }

                if (items == null || items.isEmpty()) {
                    return List.of();
                }

                List<FeegowProfessional> professionals = new ArrayList<>();
                for (ProfessionalDTO p : items) {
                    if (p == null) continue;
                    String id = p.id() == null ? null : String.valueOf(p.id());
                    String name = p.nome() == null ? null : p.nome();
                    String tratamento = p.tratamento() == null ? "" : p.tratamento().trim();
                    if (name != null) {
                        String prefix = !tratamento.isBlank() ? tratamento + " " : "";
                        name = prefix + name.trim();
                    }
                    if ((id != null && !id.isBlank()) || (name != null && !name.isBlank())) {
                        professionals.add(new FeegowProfessional(id == null ? "" : id, name == null ? "" : name));
                    }
                }

                return professionals;
            } catch (JsonProcessingException ex) {
                log.warn("Falha ao parsear resposta Feegow (lista profissionais) como JSON via DTO: {}. Raw body: {}", ex.getMessage(), abbreviateResponseBody(body));
            }

        } catch (org.springframework.web.client.RestClientResponseException ex) {
            // Log full response body and headers to help diagnose Feegow 4xx/5xx errors (include raw body for debugging)
            String raw = ex.getResponseBodyAsString();
            log.error("Falha HTTP ao buscar lista de profissionais na Feegow. status={}, responseBody={}, responseHeaders={}",
                    ex.getStatusCode().value(), abbreviateResponseBody(raw), ex.getResponseHeaders());
            log.debug("Resposta completa da Feegow ao listar profissionais: {}", raw);
            // If Feegow returned 422, attempt a retry with empty unidade_id (some instances expect unidade_id=)
            try {
                if (ex.getStatusCode() != null && ex.getStatusCode().value() == 422) {
                    try {
                        Map<String, String> sanitizedOutgoing = new java.util.LinkedHashMap<>(headers.toSingleValueMap());
                        if (sanitizedOutgoing.containsKey("x-access-token")) {
                            sanitizedOutgoing.put("x-access-token", maskToken(sanitizedOutgoing.get("x-access-token")));
                        }
                        log.error("Feegow returned 422 when listing professionals. Possible missing token scope or permissions. responseHeaders={}, sanitizedOutgoingHeaders={}", ex.getResponseHeaders(), sanitizedOutgoing);
                    } catch (Exception diagEx) {
                        log.warn("Falha ao montar diagnóstico adicional para 422 Feegow (professionals): {}", diagEx.getMessage());
                    }
                        String retryUrl = UriComponentsBuilder.fromUriString(properties.getFeegowBaseUrl())
                            .path(resolvedPath)
                            .queryParam("ativo", "1")
                            .queryParam("start", "0")
                            .queryParam("offset", "50")
                            .queryParam("unidade_id", "")
                            .build()
                            .toUriString();

                        log.info("Feegow returned 422 when listing professionals; retrying with empty unidade_id. retryUrl={}", retryUrl);
                    ResponseEntity<String> retryResponse = restTemplate.exchange(retryUrl, HttpMethod.GET, new HttpEntity<>(headers), String.class);
                    String retryBody = retryResponse.getBody();
                    if (retryBody == null || retryBody.isBlank()) return List.of();

                    try {
                        FeegowResponseDTO dto = objectMapper.readValue(retryBody, FeegowResponseDTO.class);
                        List<ProfessionalDTO> items = List.of();
                        if (dto != null && dto.content() != null && !dto.content().isEmpty()) {
                            items = dto.content();
                        } else {
                            Object root = objectMapper.readValue(retryBody, Object.class);
                            items = extractProfessionalItemsFromLooseJson(root);
                        }

                        if (items == null || items.isEmpty()) return List.of();

                        List<FeegowProfessional> professionals = new ArrayList<>();
                        for (ProfessionalDTO p : items) {
                            if (p == null) continue;
                            String id = p.id() == null ? null : String.valueOf(p.id());
                            String name = p.nome() == null ? null : p.nome();
                            String tratamento = p.tratamento() == null ? "" : p.tratamento().trim();
                            if (name != null) {
                                String prefix = !tratamento.isBlank() ? tratamento + " " : "";
                                name = prefix + name.trim();
                            }
                            if ((id != null && !id.isBlank()) || (name != null && !name.isBlank())) {
                                professionals.add(new FeegowProfessional(id == null ? "" : id, name == null ? "" : name));
                            }
                        }

                        return professionals;
                    } catch (JsonProcessingException jex) {
                        log.warn("Falha ao parsear resposta Feegow (lista profissionais) do retry como JSON via DTO: {}. Raw body: {}", jex.getMessage(), abbreviateResponseBody(retryBody));
                        return List.of();
                    }
                }
            } catch (org.springframework.web.client.RestClientException retryEx) {
                log.warn("Retry with empty unidade_id failed when listing professionals: {}", retryEx.getMessage());
            }

            // Try a common alternate endpoint in case Feegow instance uses a localized path (no unidade_id)
            String altPath = "/profissionais/listar";
            if (!resolvedPath.equals(altPath)) {
                String altUrl = UriComponentsBuilder.fromUriString(properties.getFeegowBaseUrl())
                        .path(altPath)
                        .queryParam("ativo", "1")
                        .queryParam("start", "0")
                        .queryParam("offset", "50")
                        .build()
                        .toUriString();

                log.info("Tentando endpoint alternativo Feegow para listar profissionais: {}", altUrl);
                try {
                    ResponseEntity<String> altResponse = restTemplate.exchange(altUrl, HttpMethod.GET, new HttpEntity<>(headers), String.class);
                    String altBody = altResponse.getBody();
                    if (altBody == null || altBody.isBlank()) {
                        return List.of();
                    }

                    try {
                        FeegowResponseDTO dto = objectMapper.readValue(altBody, FeegowResponseDTO.class);
                        List<ProfessionalDTO> items = List.of();
                        if (dto != null && dto.content() != null && !dto.content().isEmpty()) {
                            items = dto.content();
                        } else {
                            Object root = objectMapper.readValue(altBody, Object.class);
                            items = extractProfessionalItemsFromLooseJson(root);
                        }

                        if (items == null || items.isEmpty()) {
                            return List.of();
                        }

                        List<FeegowProfessional> professionals = new ArrayList<>();
                        for (ProfessionalDTO p : items) {
                            if (p == null) continue;
                            String id = p.id() == null ? null : String.valueOf(p.id());
                            String name = p.nome() == null ? null : p.nome();
                            if ((id != null && !id.isBlank()) || (name != null && !name.isBlank())) {
                                professionals.add(new FeegowProfessional(id == null ? "" : id, name == null ? "" : name));
                            }
                        }

                        return professionals;
                    } catch (JsonProcessingException jex) {
                        log.warn("Falha ao parsear resposta Feegow (lista profissionais) do endpoint alternativo como JSON via DTO: {}. Raw body: {}", jex.getMessage(), abbreviateResponseBody(altBody));
                        return List.of();
                    }
                } catch (org.springframework.web.client.RestClientResponseException altEx) {
                    String altRaw = altEx.getResponseBodyAsString();
                    log.error("Falha HTTP no endpoint alternativo Feegow. status={}, responseBody={}", altEx.getStatusCode().value(), abbreviateResponseBody(altRaw));
                    log.debug("Resposta completa do endpoint alternativo: {}", altRaw);
                } catch (RestClientException altEx) {
                    log.warn("Erro ao chamar endpoint alternativo Feegow: {}", altEx.getMessage());
                }
            }

            // Re-throw original exception so callers can handle mapping to API responses
            throw ex;
        } catch (RestClientException ex) {
            log.warn("Falha ao buscar lista de profissionais na Feegow: {}", ex.getMessage());
            throw ex;
        }

        return List.of();
    }

    @PostConstruct
    public void discoverFeegowLocalsOnStartup() {
        try {
            if (!properties.isFeegowStartupProbeEnabled()) return;
            String json = listLocals();
            if (json != null) {
                log.info("Feegow locals discovery (startup): {}", json);
            } else {
                log.warn("Feegow locals discovery returned empty response at startup.");
            }
        } catch (Exception ex) {
            log.warn("Falha ao executar discovery de locais Feegow no startup: {}", ex.getMessage());
        }
    }

    /**
     * Temporary discovery endpoint to list 'locals' in Feegow for troubleshooting.
     */
    public String listLocals() {
        String path = "/v1/api/company/list-local";
        String url = UriComponentsBuilder.fromUriString(properties.getFeegowBaseUrl())
                .path(path)
                .build()
                .toUriString();

        HttpHeaders headers = buildHeaders();
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            return response.getBody();
        } catch (RestClientException ex) {
            log.warn("Erro ao descobrir locais Feegow: {}", ex.getMessage());
            return null;
        }
    }

    private String resolveLocalId() {
        // Use canonical FEEGOW_UNIDADE_ID only (env or configured property).
        String unidadeEnv = System.getenv("FEEGOW_UNIDADE_ID");
        if (unidadeEnv != null && !unidadeEnv.isBlank() && !"0".equals(unidadeEnv.trim())) {
            return unidadeEnv.trim();
        }

        if (feegowUnidadeId != null && !feegowUnidadeId.isBlank() && !"0".equals(feegowUnidadeId.trim())) {
            return feegowUnidadeId.trim();
        }

        return "";
    }

        // Diagnostic startup unit discovery removed from production code.

        // legacy JSON-node helpers removed: using typed DTO parsing with @JsonIgnoreProperties

    private String resolvePatientDetailsPath() {
        String configuredPatientPath = properties.getFeegowPatientPath();
        if (configuredPatientPath == null || configuredPatientPath.isBlank()) {
            return "/v1/api/patient/search";
        }

        return configuredPatientPath
                .replace("/{id}", "")
                .replace("{id}", "");
    }

    private FeegowPatientDetailsDto.PatientItem extractPatientDetails(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }

        try {
            Object root = objectMapper.readValue(responseBody, Object.class);
            Object contentObj = null;

            if (root instanceof Map<?, ?> map) {
                contentObj = map.get("content");
                if (contentObj == null) {
                    contentObj = map.get("data");
                }
                // Se não tem content nem data, mas tem campos de paciente, o próprio root é o paciente
                if (contentObj == null && map.containsKey("id") && (map.containsKey("nome") || map.containsKey("cpf"))) {
                    contentObj = map;
                }
            } else if (root instanceof List<?> list) {
                contentObj = list;
            }

            if (contentObj == null) {
                log.warn("[FEEGOW] Resposta de detalhes do paciente sem 'content' ou 'data'. Body: {}", abbreviateResponseBody(responseBody));
                return null;
            }

            Object firstItemNode;
            if (contentObj instanceof List<?> list) {
                if (list.isEmpty()) {
                    return null;
                }
                firstItemNode = list.get(0);
            } else {
                firstItemNode = contentObj;
            }

            if (firstItemNode == null) {
                return null;
            }

            return objectMapper.convertValue(firstItemNode, FeegowPatientDetailsDto.PatientItem.class);
        } catch (JsonProcessingException ex) {
            log.error("[FEEGOW] Falha ao converter JSON de detalhes do paciente. Body: {}", abbreviateResponseBody(responseBody), ex);
            return null;
        }
    }

    private String resolvePreferredPhone(FeegowPatientDetailsDto.PatientItem patientDetails) {
        if (patientDetails == null) {
            return null;
        }

        String celular = firstNonBlank(patientDetails.getCelulares());
        if (celular != null) {
            return celular;
        }

        return firstNonBlank(patientDetails.getTelefones());
    }

    private String firstNonBlank(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }

        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }

        return null;
    }

    public void updateAppointmentStatus(String appointmentId, String statusId) {
        String normalizedAppointmentId = appointmentId == null ? "" : appointmentId.trim();
        if (normalizedAppointmentId.isBlank()) {
            throw new IllegalArgumentException("appointmentId não pode ser vazio");
        }

        String normalizedStatusId = statusId == null ? "" : statusId.trim();
        if (normalizedStatusId.isBlank()) {
            normalizedStatusId = resolveConfirmedStatusId();
        }

        String url = "https://api.feegow.com/v1/api/appoints/statusUpdate";

        HttpHeaders headers = buildHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        FeegowStatusUpdatePayload payload = new FeegowStatusUpdatePayload(
                normalizeAppointmentIdForPayload(normalizedAppointmentId),
                7,
                ""
        );

        try {
            String jsonPayload = objectMapper.writeValueAsString(payload);
            log.info("[FEEGOW] Enviando atualização de status. URL: {}, Payload: {}", url, jsonPayload);

            if (!tryUpdateAppointmentStatus(url, HttpMethod.POST, headers, payload, normalizedAppointmentId, "7")) {
                log.error(
                        "Não foi possível atualizar status do agendamento na Feegow (fluxo Blip segue). appointmentId={}, statusId={}",
                        normalizedAppointmentId,
                        normalizedStatusId);
            }
        } catch (Exception ex) {
            log.error(
                    "Erro inesperado ao atualizar status na Feegow (fluxo Blip não interrompido). appointmentId={}, statusId={}",
                    normalizedAppointmentId,
                    normalizedStatusId,
                    ex);
        }
    }



    private boolean tryUpdateAppointmentStatus(
            String url,
            HttpMethod method,
            HttpHeaders headers,
            Object payload,
            String appointmentId,
            String statusId) {
        if (feegowRestClient != null) {
            return tryUpdateAppointmentStatusWithRestClient(url, method, headers, payload, appointmentId, statusId);
        }
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    method,
                    new HttpEntity<>(payload, headers),
                    String.class);

            String responseBody = response.getBody();
            int statusCode = response.getStatusCode().value();

            log.info("[FEEGOW] Resposta bruta do statusUpdate: {} - {}", statusCode, responseBody);

            if (statusCode >= 200 && statusCode < 300) {
                log.info("Status do agendamento {} atualizado para {} na Feegow", appointmentId, statusId);
                return true;
            }

            log.error(
                    "Resposta inesperada ao atualizar status na Feegow. appointmentId={}, statusId={}, method={}, statusCode={}, responseBody={}",
                    appointmentId,
                    statusId,
                    method,
                    statusCode,
                    abbreviateResponseBody(response.getBody()));
            return false;
        } catch (RestClientResponseException ex) {
            log.error(
                    "Falha HTTP ao atualizar status na Feegow. appointmentId={}, statusId={}, method={}, statusCode={}, responseBody={}",
                    appointmentId,
                    statusId,
                    method,
                    ex.getStatusCode().value(),
                    abbreviateResponseBody(ex.getResponseBodyAsString()));
            return false;
        } catch (Exception ex) {
            log.error(
                    "Falha ao atualizar status na Feegow. appointmentId={}, statusId={}, method={}, erro={}",
                    appointmentId,
                    statusId,
                    method,
                    ex.getMessage(),
                    ex);
            return false;
        }
    }

    private boolean tryUpdateAppointmentStatusWithRestClient(
            String url,
            HttpMethod method,
            HttpHeaders headers,
            Object payload,
            String appointmentId,
            String statusId) {
        try {
            org.springframework.web.client.RestClient.RequestBodySpec spec;
            if (method == HttpMethod.POST) {
                spec = feegowRestClient.post().uri(URI.create(url));
            } else if (method == HttpMethod.PUT) {
                spec = feegowRestClient.put().uri(URI.create(url));
            } else {
                return false;
            }

            ResponseEntity<String> response = spec
                    .headers(h -> headers.forEach((name, values) -> values.forEach(v -> h.add(name, v))))
                    .body(payload)
                    .retrieve()
                    .toEntity(String.class);

            String responseBody = response.getBody();
            int statusCode = response.getStatusCode().value();
            log.info("[FEEGOW] Resposta bruta do statusUpdate (RestClient): {} - {}", statusCode, responseBody);

            if (statusCode >= 200 && statusCode < 300) {
                log.info("Status do agendamento {} atualizado para {} na Feegow (RestClient)", appointmentId, statusId);
                return true;
            }

            log.error(
                    "Resposta inesperada ao atualizar status na Feegow (RestClient). appointmentId={}, statusId={}, method={}, statusCode={}, responseBody={}",
                    appointmentId,
                    statusId,
                    method,
                    statusCode,
                    abbreviateResponseBody(response.getBody()));
            return false;
        } catch (Exception ex) {
            log.error(
                    "Falha ao atualizar status na Feegow (RestClient). appointmentId={}, statusId={}, method={}, erro={}",
                    appointmentId,
                    statusId,
                    method,
                    ex.getMessage(),
                    ex);
            return false;
        }
    }

    private String resolveConfirmedStatusId() {
        String configuredConfirmedStatusId = properties.getFeegowConfirmedStatusId();
        if (configuredConfirmedStatusId == null || configuredConfirmedStatusId.isBlank()) {
            return "7";
        }

        return configuredConfirmedStatusId.trim();
    }

    public void updateStatus(String appointmentId, int statusId) {
        String configuredUpdateStatusPath = properties.getFeegowUpdateStatusPath();
        String resolvedUpdateStatusPath = configuredUpdateStatusPath == null
                ? ""
            : configuredUpdateStatusPath.replace("{id}", appointmentId);

        String url = UriComponentsBuilder.fromUriString(properties.getFeegowBaseUrl())
                .path(resolvedUpdateStatusPath)
                .build()
                .toUriString();

        HttpHeaders headers = buildHeaders();
        List<Map<String, Object>> payloadCandidates = buildUpdateStatusPayloadCandidates(
                appointmentId,
            statusId);
        RuntimeException lastException = null;

        for (Map<String, Object> payload : payloadCandidates) {
            try {
                ResponseEntity<String> response = restTemplate.exchange(
                        url,
                        HttpMethod.PATCH,
                        new HttpEntity<>(payload, headers),
                        String.class);

                int statusCode = response.getStatusCode().value();
                if (statusCode == 200 || statusCode == 204) {
                    log.info("Status da consulta atualizado no Feegow. appointmentId={}, status={}, payloadKeys={}",
                            appointmentId,
                            statusId,
                            payload.keySet());
                    return;
                }

                log.error(
                        "Resposta inesperada ao atualizar status na Feegow. appointmentId={}, status={}, payloadKeys={}, statusCode={}, responseBody={}",
                        appointmentId,
                        statusId,
                        payload.keySet(),
                        statusCode,
                        abbreviateResponseBody(response.getBody()));
            } catch (RestClientResponseException ex) {
                log.error(
                        "Falha HTTP ao atualizar status na Feegow. appointmentId={}, status={}, payloadKeys={}, statusCode={}, responseBody={}",
                        appointmentId,
                        statusId,
                        payload.keySet(),
                        ex.getStatusCode().value(),
                        abbreviateResponseBody(ex.getResponseBodyAsString()));
                lastException = ex;
            }
        }

        if (lastException != null) {
            throw lastException;
        }

        throw new IllegalStateException(
                "Não foi possível atualizar status da consulta no Feegow. appointmentId=" + appointmentId + ", status=" + statusId);
    }

    private List<Map<String, Object>> buildUpdateStatusPayloadCandidates(
            String appointmentId,
            int statusId) {
        Object appointmentIdValue = normalizeAppointmentIdForPayload(appointmentId);

        Map<String, Object> statusPayload = new LinkedHashMap<>();
        statusPayload.put("StatusID", statusId);
        statusPayload.put("Obs", "");
        statusPayload.put("AgendamentoID", appointmentIdValue);

        return List.of(statusPayload);
    }

    private Object normalizeAppointmentIdForPayload(String appointmentId) {
        if (appointmentId == null) {
            return null;
        }

        String normalized = appointmentId.trim();
        if (normalized.matches("^\\d+$")) {
            try {
                return Long.valueOf(normalized);
            } catch (NumberFormatException ignored) {
                return normalized;
            }
        }

        return normalized;
    }

    private String abbreviateResponseBody(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "[empty]";
        }

        String normalized = responseBody.trim();
        int maxLength = 500;
        if (normalized.length() <= maxLength) {
            return normalized;
        }

        return normalized.substring(0, maxLength) + "...";
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        String tokenValue = normalizeApiKey(apiKey);
        if (tokenValue != null && !tokenValue.isBlank()) {
            headers.set("x-access-token", tokenValue);
        } else {
            log.error("ERRO: Token x-access-token nulo ou vazio no momento de montar os headers!");
        }

        log.debug("Headers enviados para Feegow: {}", headers.toSingleValueMap().keySet());
        return headers;
    }

    private String normalizeApiKey(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        if (normalized.regionMatches(true, 0, "Bearer ", 0, 7)) {
            normalized = normalized.substring(7).trim();
        }

        return normalized;
    }

    private String resolveUnidadeId() {
        // Use a single canonical environment variable for unidade: FEEGOW_UNIDADE_ID.
        String env = System.getenv("FEEGOW_UNIDADE_ID");
        if (env != null && !env.isBlank() && !"0".equals(env.trim())) {
            return env.trim();
        }

        if (feegowUnidadeId != null && !feegowUnidadeId.isBlank() && !"0".equals(feegowUnidadeId.trim())) {
            return feegowUnidadeId.trim();
        }

        return "";
    }

    private String maskToken(String token) {
        if (token == null) return "[none]";
        String t = token.trim();
        if (t.length() <= 8) return "****";
        return t.substring(0, 4) + "..." + t.substring(t.length() - 4);
    }

    private FeegowAppointment parseAppointment(FeegowSearchResponseDto.FeegowSearchAppointmentDto row) {
        String appointmentId = normalizeFeegowIdentifier(row.appointmentId());
        String patientId = row.patientId() == null ? "" : row.patientId();
        String doctorId = row.doctorId() == null ? "" : row.doctorId();
        String doctorName = row.doctorName(); // Agora mapeado para 'nome' do JSON
        String unit = row.unitName();
        String statusId = row.statusId() == null ? "" : row.statusId();

        String dateRaw = row.appointmentDate() == null ? "" : row.appointmentDate().trim();
        String timeRaw = row.appointmentTime() == null ? "" : row.appointmentTime().trim();

        if (dateRaw.isBlank() || timeRaw.isBlank()) {
            log.warn("Agendamento ID {} ignorado por falta de data/hora", appointmentId);
            return null;
        }

        LocalDate datePart;
        LocalTime timePart;
        try {
            datePart = LocalDate.parse(dateRaw, FEEGOW_RESPONSE_DATE_FORMAT);
            timePart = parseFeegowTime(timeRaw);
        } catch (DateTimeParseException ex) {
            log.warn("Agendamento ID {} ignorado por data/hora inválida. data={}, horario={}",
                    appointmentId,
                    dateRaw,
                    timeRaw);
            return null;
        }

        LocalDateTime startAt = LocalDateTime.of(datePart, timePart);

        return new FeegowAppointment(
                appointmentId,
                patientId,
                doctorId,
                doctorName,
                unit,
                startAt,
                statusId);
    }

    private String normalizeFeegowIdentifier(Object identifierValue) {
        if (identifierValue == null) {
            return "";
        }

        String normalized = String.valueOf(identifierValue).trim();
        if (normalized.isBlank() || "null".equalsIgnoreCase(normalized)) {
            return "";
        }

        return normalized;
    }

    private LocalTime parseFeegowTime(String timeRaw) {
        try {
            return LocalTime.parse(timeRaw, FEEGOW_RESPONSE_TIME_FORMAT);
        } catch (DateTimeParseException ex) {
            return LocalTime.parse(timeRaw, FEEGOW_RESPONSE_TIME_WITH_SECONDS_FORMAT);
        }
    }

    public record FeegowAppointment(
            String id,
            String patientId,
            @JsonProperty("profissional_id") String doctorId,
            @JsonProperty("nome") String doctorName,
            String unitName,
            LocalDateTime startAt,
            String statusId) {
    }

    public record FeegowPatient(
            String id,
            String name,
            String phone,
            String cpf,
            String birthdate) {
    }

        public record FeegowProfessional(
            @JsonProperty("profissional_id") String id,
            @JsonProperty("nome") String name) {
    }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record ProfessionalDTO(
            @JsonProperty("profissional_id")
            @JsonAlias({"id", "profissionalId"})
            String id,
            @JsonProperty("nome")
            String nome,
            @JsonProperty("tratamento")
            String tratamento) {
        }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FeegowResponseDTO(boolean success, List<ProfessionalDTO> content) {
    }

    public record FeegowStatusUpdatePayload(
            @JsonProperty("AgendamentoID") Object agendamentoId,
            @JsonProperty("StatusID") Integer statusId,
            @JsonProperty("Obs") String obs
    ) {}
}
