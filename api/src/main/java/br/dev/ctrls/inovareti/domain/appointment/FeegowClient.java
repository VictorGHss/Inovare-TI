package br.dev.ctrls.inovareti.domain.appointment;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.annotation.PostConstruct;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.beans.factory.annotation.Value;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = "app.appointment.motor.enabled", havingValue = "true", matchIfMissing = true)
public class FeegowClient {

    private final RestTemplate restTemplate;
    private final AppointmentMotorProperties properties;

    @Value("${app.feegow.api-key}")
    private String apiKey;

    @PostConstruct
    public void logFeegowApiKeyStatus() {
        String normalizedApiKey = normalizeApiKey(apiKey);
        if (normalizedApiKey == null || normalizedApiKey.isBlank()) {
            log.warn("Chave da Feegow não foi carregada. Verifique a variável de ambiente APP_FEEGOW_API_KEY.");
            return;
        }

        int visibleCharacters = Math.min(4, normalizedApiKey.length());
        String suffix = normalizedApiKey.substring(normalizedApiKey.length() - visibleCharacters);
        log.info("Chave da Feegow carregada com sucesso. tamanho={}, sufixo={}", normalizedApiKey.length(), suffix);
    }

    public List<FeegowAppointment> searchAppointments(LocalDate date, int statusId) {
        String url = UriComponentsBuilder.fromUriString(properties.getFeegowBaseUrl())
                .path(properties.getFeegowSearchPath())
                .queryParam("date", date)
                .queryParam("status", statusId)
                .build()
                .toUriString();

        ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(buildHeaders()),
                new ParameterizedTypeReference<>() {
                });

        List<Map<String, Object>> body = response.getBody();
        if (body == null) {
            return List.of();
        }

        List<FeegowAppointment> appointments = new ArrayList<>();
        for (Map<String, Object> row : body) {
            appointments.add(parseAppointment(row));
        }
        return appointments;
    }

    public FeegowPatient patientInfo(String patientId) {
        String url = UriComponentsBuilder.fromUriString(properties.getFeegowBaseUrl())
                .path(properties.getFeegowPatientPath().replace("{id}", patientId))
                .build()
                .toUriString();

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(buildHeaders()),
                new ParameterizedTypeReference<>() {
                });

        Map<String, Object> body = response.getBody();
        if (body == null) {
            return new FeegowPatient(patientId, null, null);
        }

        return new FeegowPatient(
                String.valueOf(body.getOrDefault("id", patientId)),
                body.get("nome") == null ? null : String.valueOf(body.get("nome")),
                body.get("telefone") == null ? null : String.valueOf(body.get("telefone")));
    }

    public void updateStatus(String appointmentId, int statusId) {
        String url = UriComponentsBuilder.fromUriString(properties.getFeegowBaseUrl())
                .path(properties.getFeegowUpdateStatusPath().replace("{id}", appointmentId))
                .build()
                .toUriString();

        restTemplate.exchange(
                url,
                HttpMethod.PATCH,
                new HttpEntity<>(Map.of("status", statusId), buildHeaders()),
                Void.class);

        log.info("Status da consulta atualizado no Feegow. appointmentId={}, status={}", appointmentId, statusId);
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        String normalizedApiKey = normalizeApiKey(apiKey);
        if (normalizedApiKey != null && !normalizedApiKey.isBlank()) {
            log.info("Utilizando x-api-key para Feegow: {}", normalizedApiKey.substring(0, Math.min(5, normalizedApiKey.length())));
            headers.add("x-api-key", normalizedApiKey);
        }
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

    private FeegowAppointment parseAppointment(Map<String, Object> row) {
        String appointmentId = String.valueOf(row.getOrDefault("id", ""));
        String patientId = String.valueOf(row.getOrDefault("paciente_id", ""));
        String doctorId = String.valueOf(row.getOrDefault("profissional_id", ""));
        String doctorName = row.get("profissional_nome") == null ? null : String.valueOf(row.get("profissional_nome"));
        String unit = row.get("unidade") == null ? null : String.valueOf(row.get("unidade"));
        String startAtRaw = String.valueOf(row.getOrDefault("inicio", ""));
        LocalDateTime startAt = LocalDateTime.parse(startAtRaw, DateTimeFormatter.ISO_DATE_TIME);

        return new FeegowAppointment(
                appointmentId,
                patientId,
                doctorId,
                doctorName,
                unit,
                startAt);
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
