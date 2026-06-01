package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.feegow;

import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import br.dev.ctrls.inovareti.modules.appointment.application.dto.FeegowPatientDetailsDto;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.FeegowPatient;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.PatientExternalPort;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.client.FeegowPatientClient;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.AppointmentMotorProperties;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.FeegowProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.retry.annotation.Retryable;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.web.client.RestClientException;
import lombok.extern.slf4j.Slf4j;

/**
 * Adaptador de Infraestrutura: FeegowPatientAdapter.
 *
 * COMENTÁRIO OBRIGATÓRIO:
 * Este componente de infraestrutura faz a ponte de comunicação física com a API Feegow
 * para ações de Pacientes. Ele implementa a Porta de Saída do Domínio 'PatientExternalPort'
 * (Inversão de Dependência) e gerencia de forma isolada a resiliência (Circuit Breaker)
 * e o consumo do cliente declarativo HTTP 'FeegowPatientClient'.
 */
@Slf4j
@Component
public class FeegowPatientAdapter extends AbstractFeegowAdapter implements PatientExternalPort {

    private final FeegowPatientClient patientClient;

    public FeegowPatientAdapter(
            AppointmentMotorProperties properties,
            FeegowProperties feegowProperties,
            ObjectMapper objectMapper,
            FeegowPatientClient patientClient) {
        super(properties, feegowProperties, objectMapper);
        this.patientClient = patientClient;
    }

    @Override
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

    @CircuitBreaker(name = "feegowApiCircuit", fallbackMethod = "fallbackGetPatientDetails")
    @Retryable(
        retryFor = { RestClientException.class, org.springframework.web.client.ResourceAccessException.class, org.springframework.dao.DataAccessException.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2.0)
    )
    public FeegowPatientDetailsDto.PatientItem getPatientDetails(String patientId) {
        String path = resolvePatientDetailsPath();
        URI uri = UriComponentsBuilder.fromUriString(properties.getFeegowBaseUrl())
                .path(path)
                .queryParam("paciente_id", patientId)
                .build()
                .toUri();

        log.info("[FEEGOW] [PATIENT-ADAPTER] Buscando detalhes do paciente ID: {} na URL: {}", patientId, uri);

        try {
            ResponseEntity<String> response = patientClient.getPatientDetails(uri, getAccessToken());
            return extractPatientDetails(response.getBody());
        } catch (Exception ex) {
            log.warn("Erro ao buscar detalhes do paciente id={} na Feegow: {}", patientId, ex.getMessage());
            throw ex;
        }
    }

    /**
     * Fallback para a busca de detalhes do paciente da Feegow em caso de erro ou circuito aberto.
     * Retorna fallback seguro (null) e registra intenção estruturada de sincronização offline.
     */
    public FeegowPatientDetailsDto.PatientItem fallbackGetPatientDetails(String patientId, Throwable t) {
        log.warn("[OFFLINE-SYNC-INTENT] [FEEGOW] Falha ao obter detalhes do paciente ID: {}. Circuito aberto ou erro de rede: {}. Retornando null.", patientId, t.getMessage());
        return null;
    }

    private String resolvePatientDetailsPath() {
        String configuredPatientPath = properties.getFeegowPatientPath();
        if (configuredPatientPath == null || configuredPatientPath.isBlank()) {
            return "/v1/api/patient/search";
        }
        return configuredPatientPath.replace("/{id}", "").replace("{id}", "");
    }

    private FeegowPatientDetailsDto.PatientItem extractPatientDetails(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }

        try {
            Object root = objectMapper.readValue(responseBody, Object.class);
            if (root == null) {
                log.warn("[FEEGOW] Resposta de detalhes do paciente vazia apos parse.");
                return null;
            }

            Object contentObj = null;
            switch (root) {
                case Map<?, ?> map -> {
                    contentObj = map.get("content");
                    if (contentObj == null) {
                        contentObj = map.get("data");
                    }
                    if (contentObj == null && map.containsKey("id") && (map.containsKey("nome") || map.containsKey("cpf"))) {
                        contentObj = map;
                    }
                }
                case List<?> list -> contentObj = list;
                default -> {
                    // Sem conversao possivel
                }
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
            log.error("[FEEGOW] Falha ao ler/analisar JSON de detalhes do paciente. Body: {}", abbreviateResponseBody(responseBody), ex);
            return null;
        } catch (RuntimeException ex) {
            log.error("[FEEGOW] Falha na coerção de tipos ou estrutura inesperada na conversão dos detalhes do paciente. Body: {}", abbreviateResponseBody(responseBody), ex);
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

    private String sanitizeCpf(String cpf) {
        if (cpf == null || cpf.isBlank()) {
            return "";
        }
        return cpf.replaceAll("\\D", "");
    }

    /**
     * Captura graciosamente a falha definitiva após 3 tentativas de busca de detalhes do paciente.
     */
    @Recover
    public FeegowPatientDetailsDto.PatientItem recoverGetPatientDetails(RestClientException ex, String patientId) {
        log.error("[RECOVERY-FEEGOW] Falha definitiva após 3 tentativas de busca de detalhes do paciente {} no Feegow ERP. Erro: {}", 
            patientId, ex.getMessage(), ex);
        return null;
    }
}
