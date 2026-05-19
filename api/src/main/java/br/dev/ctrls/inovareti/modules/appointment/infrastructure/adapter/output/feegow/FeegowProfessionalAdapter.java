package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.feegow;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.FeegowProfessional;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.ProfessionalExternalPort;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.utils.CacheJitter;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.client.FeegowProfessionalClient;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.AppointmentMotorProperties;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.FeegowProperties;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;

/**
 * Adaptador de Infraestrutura: FeegowProfessionalAdapter.
 *
 * COMENTÁRIO OBRIGATÓRIO:
 * Este componente de infraestrutura faz a ponte de comunicação física com a API Feegow
 * para ações de Médicos e Profissionais. Ele implementa a Porta de Saída do Domínio 'ProfessionalExternalPort'
 * (Inversão de Dependência) e gerencia de forma isolada a resiliência (Circuit Breaker),
 * o cacheamento em Redis (StringRedisTemplate) por 24 horas e os retries diagnósticos
 * de caminhos alternativos de API e unidade_id de forma robusta e modular.
 */
@Slf4j
@Component
public class FeegowProfessionalAdapter extends AbstractFeegowAdapter implements ProfessionalExternalPort {

    private final FeegowProfessionalClient professionalClient;
    private final StringRedisTemplate stringRedisTemplate;

    public FeegowProfessionalAdapter(
            AppointmentMotorProperties properties,
            FeegowProperties feegowProperties,
            ObjectMapper objectMapper,
            FeegowProfessionalClient professionalClient,
            ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider) {
        super(properties, feegowProperties, objectMapper);
        this.professionalClient = professionalClient;
        this.stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
    }

    @Override
    @CircuitBreaker(name = "feegowApiCircuit", fallbackMethod = "fallbackGetProfessionalName")
    public String getProfessionalName(String professionalId) {
        if (professionalId == null || professionalId.isBlank()) {
            return null;
        }

        String id = professionalId.trim();
        String cacheKey = "feegow:professional:name:v2:" + id;

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

        URI uri = uriBuilder.build().toUri();

        log.info("[FEEGOW] [PROFESSIONAL-ADAPTER] Buscando nome do profissional ID: {} na URL: {}", id, uri);

        try {
            ResponseEntity<String> response = professionalClient.getProfessionalDetails(uri, getAccessToken());
            String body = response.getBody();
            if (body == null || body.isBlank()) {
                return null;
            }

            Object root = objectMapper.readValue(body, Object.class);
            String resolvedName = extractProfessionalName(root);
            if (resolvedName != null && !resolvedName.isBlank()) {
                try {
                    if (stringRedisTemplate != null) { // Adiciona jitter ao TTL do cache para evitar "stampede"
                        stringRedisTemplate.opsForValue().set(cacheKey, resolvedName.trim(), CacheJitter.withJitter(java.time.Duration.ofHours(24)));
                    }
                } catch (RuntimeException ex) {
                    log.warn("Falha ao gravar cache Redis para professional name id={}: {}", id, ex.getMessage());
                }
                return resolvedName.trim();
            }
        } catch (RestClientResponseException ex) {
            log.error("Falha HTTP ao buscar nome do profissional na Feegow para id={}. status={}, responseBody={}",
                    id, ex.getStatusCode().value(), abbreviateResponseBody(ex.getResponseBodyAsString()));

            // Se a Feegow retornar 422, tenta mais uma vez com unidade_id vazia
            try {
                if (ex.getStatusCode() != null && ex.getStatusCode().value() == 422) {
                    URI retryUri = UriComponentsBuilder.fromUriString(properties.getFeegowBaseUrl())
                            .path(resolvedPath)
                            .queryParam("profissional_id", id)
                            .queryParam("ativo", "1")
                            .queryParam("pagina", "1")
                            .queryParam("unidade_id", "")
                            .build()
                            .toUri();

                    log.info("Feegow retornou 422 para busca de profissional; retentando com unidade_id vazia. retryUrl={}", retryUri);
                    ResponseEntity<String> retryResponse = professionalClient.getProfessionalDetails(retryUri, getAccessToken());
                    String retryBody = retryResponse.getBody();
                    if (retryBody != null && !retryBody.isBlank()) {
                        Object retryRoot = objectMapper.readValue(retryBody, Object.class);
                        return extractProfessionalName(retryRoot);
                    }
                }
            } catch (Exception retryEx) {
                log.warn("Retry com unidade_id vazia falhou para profissional id={}: {}", id, retryEx.getMessage());
            }

            throw ex;
        } catch (Exception ex) {
            log.warn("Falha ao buscar nome do profissional na Feegow para id={}: {}", id, ex.getMessage());
        }

        return null;
    }

    /**
     * Fallback para a busca de nome do profissional na Feegow.
     * Retorna fallback seguro ("Clínica Inovare") e registra intenção de sincronização offline.
     */
    public String fallbackGetProfessionalName(String professionalId, Throwable t) {
        log.warn("[OFFLINE-SYNC-INTENT] [FEEGOW] Falha ao obter nome do profissional ID: {}. Circuito aberto ou erro de rede: {}. Retornando nome padrão Clínica Inovare.", professionalId, t.getMessage());
        return "Clínica Inovare";
    }

    @Override
    @CircuitBreaker(name = "feegowApiCircuit", fallbackMethod = "fallbackListProfessionals")
    public List<FeegowProfessional> listProfessionals() {
        String configuredPath = properties.getFeegowProfessionalPath();
        String resolvedPath = (configuredPath == null || configuredPath.isBlank())
                ? "/v1/api/professional/list"
                : configuredPath;

        String localId = resolveLocalId();

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(properties.getFeegowBaseUrl())
                .path(resolvedPath)
                .queryParam("unidade_id", "0")
                .queryParam("ativo", "1")
                .queryParam("start", "0")
                .queryParam("offset", "50");

        if (localId != null && !localId.isBlank()) {
            uriBuilder.queryParam("local_id", localId);
        }

        URI uri = uriBuilder.build().toUri();

        log.info("[FEEGOW] [PROFESSIONAL-ADAPTER] Listando profissionais na URL: {}", uri);

        try {
            ResponseEntity<String> response = professionalClient.getProfessionalDetails(uri, getAccessToken());
            return parseProfessionalsResponseBody(response.getBody());
        } catch (RestClientResponseException ex) {
            String raw = ex.getResponseBodyAsString();
            log.error("Falha HTTP ao listar profissionais na Feegow. status={}, responseBody={}",
                    ex.getStatusCode().value(), abbreviateResponseBody(raw));

            // Se retornar 422, tenta com unidade_id vazia
            try {
                if (ex.getStatusCode() != null && ex.getStatusCode().value() == 422) {
                    URI retryUri = UriComponentsBuilder.fromUriString(properties.getFeegowBaseUrl())
                            .path(resolvedPath)
                            .queryParam("ativo", "1")
                            .queryParam("start", "0")
                            .queryParam("offset", "50")
                            .queryParam("unidade_id", "")
                            .build()
                            .toUri();

                    log.info("Feegow retornou 422 ao listar profissionais; retentando com unidade_id vazia. retryUrl={}", retryUri);
                    ResponseEntity<String> retryResponse = professionalClient.getProfessionalDetails(retryUri, getAccessToken());
                    return parseProfessionalsResponseBody(retryResponse.getBody());
                }
            } catch (Exception retryEx) {
                log.warn("Retry com unidade_id vazia falhou ao listar profissionais: {}", retryEx.getMessage());
            }

            // Tenta o endpoint alternativo '/profissionais/listar'
            String altPath = "/profissionais/listar";
            if (!resolvedPath.equals(altPath)) {
                URI altUri = UriComponentsBuilder.fromUriString(properties.getFeegowBaseUrl())
                        .path(altPath)
                        .queryParam("ativo", "1")
                        .queryParam("start", "0")
                        .queryParam("offset", "50")
                        .build()
                        .toUri();

                log.info("Tentando endpoint alternativo Feegow para listar profissionais: {}", altUri);
                try {
                    ResponseEntity<String> altResponse = professionalClient.getProfessionalDetails(altUri, getAccessToken());
                    return parseProfessionalsResponseBody(altResponse.getBody());
                } catch (Exception altEx) {
                    log.warn("Erro ao chamar endpoint alternativo Feegow: {}", altEx.getMessage());
                }
            }

            throw ex;
        } catch (Exception ex) {
            log.warn("Falha ao buscar lista de profissionais na Feegow: {}", ex.getMessage());
            throw new RuntimeException(ex);
        }
    }

    /**
     * Fallback para a listagem de profissionais da Feegow em caso de falha.
     * Retorna fallback seguro (lista vazia) para evitar o estouro de erro 500 no processamento.
     */
    public List<FeegowProfessional> fallbackListProfessionals(Throwable t) {
        log.warn("[OFFLINE-SYNC-INTENT] [FEEGOW] Falha ao obter lista de profissionais. Circuito aberto ou erro de rede: {}. Retornando lista vazia.", t.getMessage());
        return List.of();
    }

    private String resolveLocalId() {
        String env = System.getenv("FEEGOW_UNIDADE_ID");
        if (env != null && !env.isBlank() && !"0".equals(env.trim())) {
            return env.trim();
        }
        String localUnidadeId = feegowProperties.getUnidadeId();
        if (localUnidadeId != null && !localUnidadeId.isBlank() && !"0".equals(localUnidadeId.trim())) {
            return localUnidadeId.trim();
        }
        return "";
    }

    private List<FeegowProfessional> parseProfessionalsResponseBody(String body) throws JsonProcessingException {
        if (body == null || body.isBlank()) {
            return List.of();
        }

        FeegowResponseDTO dto = objectMapper.readValue(body, FeegowResponseDTO.class);
        List<ProfessionalDTO> items;
        if (dto != null && dto.content() != null && !dto.content().isEmpty()) {
            items = dto.content();
        } else {
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
            String name = p.nome() == null ? null : p.nome().trim();
            if ((id != null && !id.isBlank()) || (name != null && !name.isBlank())) {
                professionals.add(new FeegowProfessional(id == null ? "" : id, name == null ? "" : name));
            }
        }
        return professionals;
    }

    private String extractProfessionalName(Object root) {
        if (root == null) {
            return null;
        }

        try {
            switch (root) {
                case Map<?, ?> map -> {
                    Object content = map.get("content");
                    if (content instanceof List<?> list && !list.isEmpty()) {
                        Object first = list.get(0);
                        if (first instanceof Map<?, ?> row) {
                            Object nome = row.get("nome");
                            if (nome != null) {
                                return nome.toString().trim();
                            }
                        }
                    }

                    Object dados = map.get("dados");
                    if (dados instanceof List<?> dlist && !dlist.isEmpty()) {
                        Object first = dlist.get(0);
                        if (first instanceof Map<?, ?> row) {
                            Object nome = row.get("nome");
                            if (nome != null) {
                                return nome.toString().trim();
                            }
                        }
                    }
                }
                default -> {
                }
            }
        } catch (Exception e) {
            log.warn("Erro ao extrair nome do profissional da resposta Feegow: {}", e.getMessage());
        }
        return null;
    }

    private List<ProfessionalDTO> extractProfessionalItemsFromLooseJson(Object root) {
        if (root == null) {
            return List.of();
        }
        try {
            switch (root) {
                case Map<?, ?> map -> {
                    Object dados = map.get("dados");
                    if (dados instanceof List<?>) {
                        return objectMapper.convertValue(dados, new TypeReference<List<ProfessionalDTO>>() { });
                    }
                }
                case List<?> list -> {
                    return objectMapper.convertValue(list, new TypeReference<List<ProfessionalDTO>>() { });
                }
                default -> {
                }
            }
        } catch (IllegalArgumentException ex) {
            log.warn("Falha ao converter lista de profissionais (fallback JSON): {}", ex.getMessage());
        }
        return List.of();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ProfessionalDTO(
            @JsonAlias({"id", "profissionalId"})
            String id,
            @JsonProperty("nome")
            String nome,
            @JsonProperty("tratamento")
            String tratamento) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record FeegowResponseDTO(boolean success, List<ProfessionalDTO> content) {
    }
}
