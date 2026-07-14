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
@lombok.RequiredArgsConstructor
public class FeegowPatientAdapter implements PatientExternalPort {

    private final AppointmentMotorProperties properties;
    private final FeegowProperties feegowProperties;
    private final ObjectMapper objectMapper;
    private final FeegowPatientClient patientClient;

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

        // COMENTÁRIO BR: Prioriza a busca por um número de celular válido do PR (iniciando com 419, 429, 439, 449, 459 ou 469)
        if (patientDetails.getCelulares() != null) {
            for (String cel : patientDetails.getCelulares()) {
                if (cel != null && !cel.isBlank()) {
                    String cleaned = cleanPhoneString(cel);
                    if (isPrCellPhone(cleaned)) {
                        return formatWithCountryCode(cleaned);
                    }
                }
            }
        }

        // COMENTÁRIO BR: Fallback 1: Caso não encontre celular do PR válido, aplica fallback buscando nos telefones fixos
        if (patientDetails.getTelefones() != null) {
            for (String tel : patientDetails.getTelefones()) {
                if (tel != null && !tel.isBlank()) {
                    String cleaned = cleanPhoneString(tel);
                    if (!cleaned.isEmpty()) {
                        return formatWithCountryCode(cleaned);
                    }
                }
            }
        }

        // COMENTÁRIO BR: Fallback 2: Se não houver telefone fixo, retorna o primeiro celular encontrado (mesmo de outro estado)
        if (patientDetails.getCelulares() != null) {
            for (String cel : patientDetails.getCelulares()) {
                if (cel != null && !cel.isBlank()) {
                    String cleaned = cleanPhoneString(cel);
                    if (!cleaned.isEmpty()) {
                        return formatWithCountryCode(cleaned);
                    }
                }
            }
        }

        return null;
    }

    private String cleanPhoneString(String phone) {
        if (phone == null) {
            return "";
        }
        // COMENTÁRIO BR: Limpa a string de telefone removendo parênteses, hífens e espaços em branco
        return phone.replaceAll("[()\\s-]", "");
    }

    private boolean isPrCellPhone(String cleanedNumber) {
        if (cleanedNumber == null || cleanedNumber.isBlank()) {
            return false;
        }
        String digits = cleanedNumber;
        if (digits.startsWith("+")) {
            digits = digits.substring(1);
        }
        if (digits.startsWith("55")) {
            digits = digits.substring(2);
        }
        if (digits.startsWith("0")) {
            digits = digits.substring(1);
        }
        // COMENTÁRIO BR: Valida se possui o formato de celular válido do PR (DDD 41 a 46 seguido do dígito 9 e mais 8 dígitos)
        if (digits.length() == 11) {
            String prefix = digits.substring(0, 3);
            return "419".equals(prefix) || "429".equals(prefix) || "439".equals(prefix)
                || "449".equals(prefix) || "459".equals(prefix) || "469".equals(prefix);
        }
        return false;
    }

    private String formatWithCountryCode(String cleanedNumber) {
        if (cleanedNumber == null || cleanedNumber.isBlank()) {
            return "";
        }
        String number = cleanedNumber;
        if (number.startsWith("+")) {
            number = number.substring(1);
        }
        if (number.startsWith("55")) {
            return number;
        }
        if (number.startsWith("0")) {
            number = number.substring(1);
        }
        // COMENTÁRIO BR: Garante o retorno do número purificado com o prefixo do país (55)
        return "55" + number;
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

    /**
     * Retorna a chave de acesso (API Key) normalizada da Feegow.
     */
    private String getAccessToken() {
        String apiKey = feegowProperties.getApiKey();
        if (apiKey == null) {
            return "";
        }
        String normalized = apiKey.trim();
        if (normalized.regionMatches(true, 0, "Bearer ", 0, 7)) {
            normalized = normalized.substring(7).trim();
        }
        return normalized;
    }

    /**
     * Abrevia a resposta da requisição Feegow para evitar logs extremamente grandes.
     */
    private String abbreviateResponseBody(String responseBody) {
        if (responseBody == null) {
            return "";
        }
        String normalized = responseBody.trim();
        int maxLength = 500;
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }

    private String formatBirthdateToIso(String birthdate) {
        if (birthdate == null || birthdate.isBlank()) {
            return null;
        }
        String clean = birthdate.trim();
        if (clean.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return clean;
        }
        if (clean.matches("\\d{2}/\\d{2}/\\d{4}")) {
            String[] parts = clean.split("/");
            return parts[2] + "-" + parts[1] + "-" + parts[0];
        }
        if (clean.matches("\\d{2}-\\d{2}-\\d{4}")) {
            String[] parts = clean.split("-");
            return parts[2] + "-" + parts[1] + "-" + parts[0];
        }
        return null;
    }

    @Override
    public void updatePatientCpf(String patientId, String cpf, String name, String birthdate) {
        if (patientId == null || patientId.isBlank() || cpf == null || cpf.isBlank()) {
            return;
        }

        URI uri = UriComponentsBuilder.fromUriString(properties.getFeegowBaseUrl())
                .path("/v1/api/patient/edit")
                .build()
                .toUri();

        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        try {
            payload.put("paciente_id", Integer.valueOf(patientId.trim()));
        } catch (NumberFormatException e) {
            payload.put("paciente_id", patientId);
        }
        
        payload.put("cpf", cpf.replaceAll("\\D", ""));
        
        if (name != null && !name.isBlank()) {
            payload.put("nome_completo", name);
        }
        if (birthdate != null && !birthdate.isBlank()) {
            String isoDate = formatBirthdateToIso(birthdate);
            if (isoDate != null) {
                payload.put("data_nascimento", isoDate);
            }
        }

        log.info("[FEEGOW] [PATIENT-ADAPTER] Sincronizando CPF do paciente ID: {} para: {} na URL: {}. Payload: {}", patientId, cpf, uri, payload);

        try {
            ResponseEntity<String> response = patientClient.savePatient(uri, payload, getAccessToken());
            log.info("[FEEGOW] Resposta do patient/edit: status={}, body={}", response.getStatusCode(), response.getBody());
        } catch (org.springframework.web.client.RestClientResponseException ex) {
            log.error("Erro ao atualizar CPF do paciente ID {} na Feegow (Status {}): {}. Response body: {}", 
                    patientId, ex.getStatusCode(), ex.getMessage(), ex.getResponseBodyAsString());
        } catch (Exception ex) {
            log.error("Erro ao atualizar CPF do paciente ID {} na Feegow: {}", patientId, ex.getMessage());
        }
    }
}
