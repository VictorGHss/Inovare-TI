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
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.AppointmentMotorProperties;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.FeegowProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
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
public class FeegowAppointmentAdapter extends AbstractFeegowAdapter implements AppointmentExternalPort {

    private static final DateTimeFormatter FEEGOW_RESPONSE_DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter FEEGOW_RESPONSE_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter FEEGOW_RESPONSE_TIME_WITH_SECONDS_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final FeegowAppointmentClient appointmentClient;

    public FeegowAppointmentAdapter(
            AppointmentMotorProperties properties,
            FeegowProperties feegowProperties,
            ObjectMapper objectMapper,
            FeegowAppointmentClient appointmentClient) {
        super(properties, feegowProperties, objectMapper);
        this.appointmentClient = appointmentClient;
    }

    @Override
    public List<FeegowAppointment> searchAppointments(LocalDate date, int statusId) {
        return searchAppointments(date, statusId, null);
    }

    @Override
    @CircuitBreaker(name = "feegowApiCircuit", fallbackMethod = "fallbackSearchAppointments")
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

        return new FeegowAppointment(id, pacienteId, profissionalId, doctorName, unitName, parsedStartAt, statusId);
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
}
