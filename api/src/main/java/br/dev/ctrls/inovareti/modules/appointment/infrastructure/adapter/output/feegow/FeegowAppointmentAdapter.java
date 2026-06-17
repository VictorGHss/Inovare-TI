package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.feegow;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import br.dev.ctrls.inovareti.modules.appointment.application.dto.FeegowSearchResponseDto;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentExternalPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.FeegowAppointment;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.client.FeegowAppointmentClient;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.client.FeegowStatusUpdatePayload;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.client.FeegowCancelPayload;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.AppointmentMotorProperties;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.FeegowProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.retry.annotation.Retryable;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.web.client.RestClientException;
import lombok.extern.slf4j.Slf4j;

/**
 * Adaptador de Infraestrutura: FeegowAppointmentAdapter.
 *
 * COMENTÁRIO OBRIGATÓRIO:
 * Este componente de infraestrutura faz a ponte de comunicação física com a API Feegow
 * para ações de Agendamentos (Appointment). Ele implementa a Porta de Saída do Domínio 'AppointmentExternalPort'
 * (Inversão de Dependência) e gerencia de forma isolada a resiliência (Circuit Breaker),
 * os fallbacks estruturados de gravação offline para evitar indisponibilidades e a comunicação
 * física usando o cliente declarativo HTTP 'FeegowAppointmentClient'.
 */
@Slf4j
@Component
@lombok.RequiredArgsConstructor
public class FeegowAppointmentAdapter implements AppointmentExternalPort {

    private static final DateTimeFormatter FEEGOW_RESPONSE_DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter FEEGOW_RESPONSE_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter FEEGOW_RESPONSE_TIME_WITH_SECONDS_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final AppointmentMotorProperties properties;
    private final FeegowProperties feegowProperties;
    private final ObjectMapper objectMapper;
    private final FeegowAppointmentClient appointmentClient;

    @Override
    public List<FeegowAppointment> searchAppointments(LocalDate date, int statusId) {
        return searchAppointments(date, statusId, null);
    }

    @Override
    @CircuitBreaker(name = "feegowApiCircuit", fallbackMethod = "fallbackSearchAppointments")
    @Retryable(
        retryFor = { RestClientException.class, org.springframework.web.client.ResourceAccessException.class, org.springframework.dao.DataAccessException.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2.0)
    )
    public List<FeegowAppointment> searchAppointments(LocalDate date, int statusId, String profissionalId) {
        LocalDate effectiveDate = date != null ? date : LocalDate.now();
        if (effectiveDate.isBefore(LocalDate.now())) {
            effectiveDate = LocalDate.now();
        }
        String formattedDate = effectiveDate.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(properties.getFeegowBaseUrl())
                .path(properties.getFeegowSearchPath())
                .queryParam("data_start", formattedDate)
                .queryParam("data_end", formattedDate)
                .queryParam("status", statusId);

        if (profissionalId != null && !profissionalId.isBlank()) {
            uriBuilder.queryParam("profissional_id", profissionalId.trim());
        }

        URI uri = uriBuilder.build().toUri();

        log.info("[FEEGOW] [APPOINTMENT-ADAPTER] Buscando agendamentos na URL: {}", uri);

        try {
            ResponseEntity<String> response = appointmentClient.searchAppointments(uri, getAccessToken());
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
                // Filtro por status para ignorar agendamentos 'CANCELADOS' ou 'FALTAS'
                String status = parsedAppointment.statusId();
                if ("11".equals(status) || "12".equals(status)) {
                    continue;
                }
                // Filtro para buscar apenas agendamentos com data maior ou igual a LocalDate.now()
                if (parsedAppointment.startAt() != null && parsedAppointment.startAt().toLocalDate().isBefore(LocalDate.now())) {
                    continue;
                }
                appointments.add(parsedAppointment);
            }
            return appointments;
        } catch (Exception ex) {
            log.warn("Falha ao buscar agendamentos na Feegow: {}", ex.getMessage());
            throw ex;
        }
    }

    /**
     * Fallback para a busca de agendamentos da Feegow em caso de falha de rede ou circuito aberto.
     * Retorna fallback seguro (lista vazia) para evitar o estouro de erro 500 no fluxo.
     */
    public List<FeegowAppointment> fallbackSearchAppointments(LocalDate date, int statusId, String profissionalId, Throwable t) {
        log.warn("[OFFLINE-SYNC-INTENT] [FEEGOW] Falha ao buscar agendamentos na Feegow. Circuito aberto ou erro de rede: {}. Retornando lista vazia para posterior processamento offline.", t.getMessage());
        return List.of();
    }

    @Override
    @CircuitBreaker(name = "feegowApiCircuit", fallbackMethod = "fallbackUpdateAppointmentStatus")
    @Retryable(
        retryFor = { RestClientException.class, org.springframework.web.client.ResourceAccessException.class, org.springframework.dao.DataAccessException.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2.0)
    )
    public void updateAppointmentStatus(String appointmentId, String statusId) {
        String normalizedAppointmentId = appointmentId == null ? "" : appointmentId.trim();
        if (normalizedAppointmentId.isBlank()) {
            throw new IllegalArgumentException("appointmentId não pode ser vazio");
        }

        String normalizedStatusId = statusId == null ? "" : statusId.trim();
        if (normalizedStatusId.isBlank()) {
            normalizedStatusId = resolveConfirmedStatusId();
        }

        String statusUpdateUrl = feegowProperties.getStatusUpdateUrl();
        URI uri = URI.create(statusUpdateUrl);

        int statusIdInt = 7;
        try {
            statusIdInt = Integer.parseInt(normalizedStatusId);
        } catch (NumberFormatException ignored) {}

        FeegowStatusUpdatePayload payload = new FeegowStatusUpdatePayload(
                normalizeAppointmentIdForPayload(normalizedAppointmentId),
                statusIdInt,
                ""
        );

        try {
            String jsonPayload = objectMapper.writeValueAsString(payload);
            log.info("[FEEGOW] [APPOINTMENT-ADAPTER] Enviando atualização de status. URL: {}, Payload: {}", uri, jsonPayload);

            ResponseEntity<String> response = appointmentClient.updateStatus(uri, getAccessToken(), payload);
            int statusCode = response.getStatusCode().value();
            String responseBody = response.getBody();

            log.info("[FEEGOW] Resposta bruta do statusUpdate: {} - {}", statusCode, responseBody);

            if (statusCode >= 200 && statusCode < 300) {
                log.info("Status do agendamento {} atualizado para {} na Feegow", appointmentId, statusId);
            } else {
                log.error("Não foi possível atualizar status do agendamento na Feegow (fluxo Blip segue). appointmentId={}, statusId={}, statusCode={}, responseBody={}",
                        normalizedAppointmentId, normalizedStatusId, statusCode, abbreviateResponseBody(responseBody));
            }
        } catch (RestClientResponseException ex) {
            log.error("Falha HTTP ao atualizar status na Feegow. appointmentId={}, statusId={}, statusCode={}, responseBody={}",
                    normalizedAppointmentId, normalizedStatusId, ex.getStatusCode().value(), abbreviateResponseBody(ex.getResponseBodyAsString()));
        } catch (JsonProcessingException | RuntimeException ex) {
            log.error("Erro inesperado ao atualizar status na Feegow (fluxo Blip não interrompido). appointmentId={}, statusId={}",
                    normalizedAppointmentId, normalizedStatusId, ex);
        }
    }

    /**
     * Fallback para a atualização de status do agendamento na Feegow.
     * Registra o log estruturado [OFFLINE-SYNC-INTENT] para que a alteração não seja perdida e o fluxo siga offline.
     */
    public void fallbackUpdateAppointmentStatus(String appointmentId, String statusId, Throwable t) {
        log.warn("[OFFLINE-SYNC-INTENT] [FEEGOW] Falha ao atualizar status do agendamento na Feegow. Circuito aberto ou erro de rede: {}. Gravando intenção de sincronização offline para appointmentId={}, statusId={}", 
            t.getMessage(), appointmentId, statusId);
    }

    @Override
    public void updateStatus(String appointmentId, int statusId) {
        updateAppointmentStatus(appointmentId, String.valueOf(statusId));
    }

    private List<FeegowSearchResponseDto.FeegowSearchAppointmentDto> extractSearchItems(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return List.of();
        }

        try {
            Object root = objectMapper.readValue(responseBody, Object.class);
            if (root == null) {
                log.warn("Formato raiz JSON da busca Feegow inesperado: null");
                return List.of();
            }

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
                    log.warn("Resposta da busca Feegow sem itens.");
                }

                return appointments;
            }

            log.warn("Formato raiz JSON da busca Feegow inesperado: {}", root.getClass().getName());
            return List.of();
        } catch (JsonProcessingException ex) {
            log.error("[FEEGOW] [APPOINTMENT-ADAPTER] Falha ao ler/analisar JSON da busca de agendamentos. Body: {}", abbreviateResponseBody(responseBody), ex);
            return List.of();
        } catch (RuntimeException ex) {
            log.error("[FEEGOW] [APPOINTMENT-ADAPTER] Falha na conversão ou estrutura indevida dos dados de busca de agendamentos. Body: {}", abbreviateResponseBody(responseBody), ex);
            return List.of();
        }
    }

    private FeegowAppointment parseAppointment(FeegowSearchResponseDto.FeegowSearchAppointmentDto item) {
        if (item == null) {
            return null;
        }

        String id = item.appointmentId() == null ? null : String.valueOf(item.appointmentId());
        String pacienteId = item.patientId() == null ? null : String.valueOf(item.patientId());
        String profissionalId = item.doctorId() == null ? null : String.valueOf(item.doctorId());

        if (id == null || id.isBlank() || pacienteId == null || pacienteId.isBlank() || profissionalId == null || profissionalId.isBlank()) {
            return null;
        }

        LocalDateTime parsedStartAt = parseLocalDateTime(item.appointmentDate(), item.appointmentTime());
        if (parsedStartAt == null) {
            return null;
        }

        String doctorName = item.doctorName() != null ? item.doctorName().trim() : "";
        String unitName = item.unitName() != null ? item.unitName().trim() : "";
        String statusId = item.statusId() != null ? String.valueOf(item.statusId()) : "";
        String procedureName = item.procedureName() != null ? item.procedureName().trim() : "";

        return new FeegowAppointment(id, pacienteId, profissionalId, doctorName, unitName, parsedStartAt, statusId, procedureName);
    }

    private LocalDateTime parseLocalDateTime(String data, String hora) {
        if (data == null || data.isBlank() || hora == null || hora.isBlank()) {
            return null;
        }

        LocalDate parsedDate;
        try {
            parsedDate = LocalDate.parse(data.trim(), FEEGOW_RESPONSE_DATE_FORMAT);
        } catch (DateTimeParseException ex) {
            log.warn("Falha ao analisar data da Feegow '{}': {}", data, ex.getMessage());
            return null;
        }

        LocalTime parsedTime = null;
        String cleanTime = hora.trim();
        try {
            parsedTime = LocalTime.parse(cleanTime, FEEGOW_RESPONSE_TIME_WITH_SECONDS_FORMAT);
        } catch (DateTimeParseException ex) {
            try {
                parsedTime = LocalTime.parse(cleanTime, FEEGOW_RESPONSE_TIME_FORMAT);
            } catch (DateTimeParseException innerEx) {
                log.warn("Falha ao analisar hora da Feegow '{}' com formatos hh:mm:ss ou hh:mm: {}", hora, innerEx.getMessage());
            }
        }

        if (parsedTime == null) {
            return null;
        }

        return LocalDateTime.of(parsedDate, parsedTime);
    }

    private String resolveConfirmedStatusId() {
        return "7";
    }

    private Object normalizeAppointmentIdForPayload(String value) {
        if (value == null) {
            return "";
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException ignored) {
            return value.trim();
        }
    }

    /**
     * Captura graciosamente a falha definitiva após 3 tentativas de busca de agendamentos.
     */
    @Recover
    public List<FeegowAppointment> recoverSearchAppointments(RestClientException ex, LocalDate date, int statusId, String profissionalId) {
        log.error("[RECOVERY-FEEGOW] Falha definitiva após 3 tentativas de busca de agendamentos na Feegow ERP. Erro: {}", 
            ex.getMessage(), ex);
        return List.of();
    }

    /**
     * Captura graciosamente a falha definitiva após 3 tentativas de atualização de status.
     */
    @Recover
    public void recoverUpdateAppointmentStatus(RestClientException ex, String appointmentId, String statusId) {
        log.error("[RECOVERY-FEEGOW] Falha definitiva após 3 tentativas de atualização de status do agendamento {} na Feegow ERP. Erro: {}", 
            appointmentId, ex.getMessage(), ex);
    }

    @Override
    @CircuitBreaker(name = "feegowApiCircuit", fallbackMethod = "fallbackCancelAppointment")
    @Retryable(
        retryFor = { RestClientException.class, org.springframework.web.client.ResourceAccessException.class, org.springframework.dao.DataAccessException.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2.0)
    )
    public void cancelAppointment(String appointmentId, String obs) {
        String normalizedAppointmentId = appointmentId == null ? "" : appointmentId.trim();
        if (normalizedAppointmentId.isBlank()) {
            throw new IllegalArgumentException("appointmentId não pode ser vazio");
        }

        String cancelUrl = feegowProperties.getCancelUrl();
        URI uri = URI.create(cancelUrl);

        // O motivo_id padrão para cancelamento pelo paciente / sistema é 1
        int motivoId = 1;

        FeegowCancelPayload payload = new FeegowCancelPayload(
                normalizeAppointmentIdForPayload(normalizedAppointmentId),
                motivoId,
                obs != null ? obs.trim() : ""
        );

        try {
            String jsonPayload = objectMapper.writeValueAsString(payload);
            log.info("[FEEGOW] [APPOINTMENT-ADAPTER] Enviando cancelamento de consulta. URL: {}, Payload: {}", uri, jsonPayload);

            ResponseEntity<String> response = appointmentClient.cancelAppointment(uri, getAccessToken(), payload);
            int statusCode = response.getStatusCode().value();
            String responseBody = response.getBody();

            log.info("[FEEGOW] Resposta bruta do cancelAppointment: {} - {}", statusCode, responseBody);

            if (statusCode >= 200 && statusCode < 300) {
                log.info("Agendamento {} cancelado com sucesso na Feegow. Obs: {}", appointmentId, obs);
            } else {
                log.error("Não foi possível cancelar agendamento na Feegow. appointmentId={}, statusCode={}, responseBody={}",
                        normalizedAppointmentId, statusCode, abbreviateResponseBody(responseBody));
            }
        } catch (RestClientResponseException ex) {
            log.error("Falha HTTP ao cancelar agendamento na Feegow. appointmentId={}, statusCode={}, responseBody={}",
                    normalizedAppointmentId, ex.getStatusCode().value(), abbreviateResponseBody(ex.getResponseBodyAsString()));
        } catch (JsonProcessingException | RuntimeException ex) {
            log.error("Erro inesperado ao cancelar agendamento na Feegow. appointmentId={}",
                    normalizedAppointmentId, ex);
        }
    }

    public void fallbackCancelAppointment(String appointmentId, String obs, Throwable t) {
        log.warn("[OFFLINE-SYNC-INTENT] [FEEGOW] Falha ao cancelar agendamento na Feegow. Circuito aberto ou erro de rede: {}. Gravando intenção de sincronização offline para appointmentId={}, obs={}", 
            t.getMessage(), appointmentId, obs);
    }

    @Recover
    public void recoverCancelAppointment(RestClientException ex, String appointmentId, String obs) {
        log.error("[RECOVERY-FEEGOW] Falha definitiva após 3 tentativas de cancelamento do agendamento {} na Feegow ERP. Erro: {}", 
            appointmentId, ex.getMessage(), ex);
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
}
