package br.dev.ctrls.inovareti.domain.appointment;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import br.dev.ctrls.inovareti.domain.appointment.dto.FeegowPatientDetailsDto;
import br.dev.ctrls.inovareti.domain.appointment.dto.FeegowSearchResponseDto;
import jakarta.annotation.PostConstruct;
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

    public FeegowClient(
            @Qualifier("feegowRestTemplate") RestTemplate restTemplate,
            AppointmentMotorProperties properties,
            ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Value("${app.feegow.api-key}")
    private String apiKey;

    @PostConstruct
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
            JsonNode root = objectMapper.readTree(responseBody);

            if (root.isArray()) {
                return objectMapper.convertValue(
                        root,
                        new TypeReference<List<FeegowSearchResponseDto.FeegowSearchAppointmentDto>>() {
                        });
            }

            FeegowSearchResponseDto mappedResponse = objectMapper.treeToValue(root, FeegowSearchResponseDto.class);
            List<FeegowSearchResponseDto.FeegowSearchAppointmentDto> appointments = mappedResponse != null
                    ? mappedResponse.appointments()
                    : List.of();

            if (appointments.isEmpty()) {
                log.warn("Resposta da busca Feegow sem itens em 'content' ou 'data'. keys={}", topLevelKeys(root));
            }

            return appointments;
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Falha ao converter JSON da busca Feegow.", ex);
        }
    }

    private List<String> topLevelKeys(JsonNode root) {
        if (root == null || !root.isObject()) {
            return List.of();
        }

        List<String> keys = new ArrayList<>();
        Iterator<String> fieldNames = root.fieldNames();
        while (fieldNames.hasNext()) {
            keys.add(fieldNames.next());
        }

        return keys;
    }

    public FeegowPatient patientInfo(String patientId) {
        FeegowPatientDetailsDto.PatientItem patientDetails = getPatientDetails(patientId);
        if (patientDetails == null) {
            return new FeegowPatient(patientId, null, null);
        }

        String resolvedPatientId = patientDetails.getId() == null || patientDetails.getId().isBlank()
                ? patientId
                : patientDetails.getId();

        return new FeegowPatient(
                resolvedPatientId,
                patientDetails.getNome(),
            resolvePreferredPhone(patientDetails));
    }

    public FeegowPatientDetailsDto.PatientItem getPatientDetails(String patientId) {
        String url = UriComponentsBuilder.fromUriString(properties.getFeegowBaseUrl())
                .path(resolvePatientDetailsPath())
                .queryParam("paciente_id", patientId)
                .build()
                .toUriString();

        HttpHeaders headers = buildHeaders();

        ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);

        return extractPatientDetails(response.getBody());
    }

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
            FeegowPatientDetailsDto response = objectMapper.readValue(responseBody, FeegowPatientDetailsDto.class);
            if (response == null || response.getContent() == null || response.getContent().isNull()) {
                return null;
            }

            JsonNode contentNode = response.getContent();
            if (!contentNode.isContainerNode()) {
                return null;
            }

            JsonNode firstItemNode;
            String detectedFormat;
            if (contentNode.isArray()) {
                detectedFormat = "lista";
                if (contentNode.isEmpty()) {
                    return null;
                }

                firstItemNode = contentNode.get(0);
            } else {
                detectedFormat = "objeto";
                firstItemNode = contentNode;
            }

            log.debug("Formato de resposta Feegow detectado: {}", detectedFormat);

            if (firstItemNode == null || firstItemNode.isNull()) {
                return null;
            }

            return objectMapper.treeToValue(firstItemNode, FeegowPatientDetailsDto.PatientItem.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Falha ao converter JSON de detalhes do paciente Feegow.", ex);
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

    private FeegowAppointment parseAppointment(FeegowSearchResponseDto.FeegowSearchAppointmentDto row) {
        String appointmentId = normalizeFeegowIdentifier(row.appointmentId());
        String patientId = row.patientId() == null ? "" : row.patientId();
        String doctorId = row.doctorId() == null ? "" : row.doctorId();
        String doctorName = row.doctorName();
        String unit = row.unitName();

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
                startAt);
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
            String doctorId,
            String doctorName,
            String unitName,
            LocalDateTime startAt) {
    }

    public record FeegowPatient(String id, String name, String phone) {
    }
}
