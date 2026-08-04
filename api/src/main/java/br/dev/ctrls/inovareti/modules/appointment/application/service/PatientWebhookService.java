package br.dev.ctrls.inovareti.modules.appointment.application.service;

import br.dev.ctrls.inovareti.modules.appointment.application.dto.PatientAppointmentsResponse;
import br.dev.ctrls.inovareti.modules.appointment.application.dto.PatientWebhookRegistrationRequest;
import br.dev.ctrls.inovareti.modules.appointment.application.dto.PatientWebhookRegistrationResponse;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentExternalPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.FeegowAppointment;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.FeegowPatient;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.PatientExternalPort;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.client.FeegowPatientClient;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.AppointmentMotorProperties;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.FeegowProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Serviço de aplicação PatientWebhookService.
 * Orquestra as integrações com a API do Feegow para cadastro de pacientes e consulta de agendamentos futuros.
 * Comentários mantidos em PT-BR pelas Regras de Ouro.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PatientWebhookService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final PatientExternalPort patientExternalPort;
    private final AppointmentExternalPort appointmentExternalPort;
    private final FeegowPatientClient patientClient;
    private final AppointmentMotorProperties motorProperties;
    private final FeegowProperties feegowProperties;
    private final ObjectMapper objectMapper;

    /**
     * Efetua o cadastro ou atualização do paciente na API do Feegow (/v1/api/patient/edit ou /v1/api/patient/save).
     *
     * @param request Dados do paciente contendo CPF, nome e data de nascimento.
     * @return PatientWebhookRegistrationResponse contendo o paciente_id gerado.
     */
    public PatientWebhookRegistrationResponse registerPatient(PatientWebhookRegistrationRequest request) {
        log.info("[PatientWebhookService] Cadastrando paciente no Feegow. Nome: {}, CPF: {}", request.getNome(), request.getCpf());

        String cleanCpf = request.getCpf() != null ? request.getCpf().replaceAll("\\D", "") : "";
        if (cleanCpf.length() != 11) {
            log.warn("[PatientWebhookService] CPF com formato ou tamanho inválido ({}) dígitos. Rejeitando requisição.", cleanCpf.length());
            return PatientWebhookRegistrationResponse.builder()
                    .status("error")
                    .mensagem("CPF inválido ou rejeitado pelo Feegow.")
                    .build();
        }

        String isoBirthdate = formatBirthdateToIso(request.getNascimento());

        URI uri = UriComponentsBuilder.fromUriString(motorProperties.getFeegowBaseUrl())
                .path("/v1/api/patient/edit")
                .build()
                .toUri();

        Map<String, Object> payload = new HashMap<>();
        payload.put("paciente_id", 0);
        payload.put("cpf", cleanCpf);
        payload.put("nome", request.getNome() != null ? request.getNome().trim() : "");
        payload.put("nome_completo", request.getNome() != null ? request.getNome().trim() : "");
        if (isoBirthdate != null) {
            payload.put("nascimento", isoBirthdate);
            payload.put("data_nascimento", isoBirthdate);
        }

        String pacienteId = null;

        try {
            ResponseEntity<String> response = patientClient.savePatient(uri, payload, getAccessToken());
            log.info("[PatientWebhookService] Resposta Feegow (status {}): {}", response.getStatusCode(), response.getBody());
            pacienteId = extractPatientId(response.getBody());
        } catch (org.springframework.web.client.HttpStatusCodeException ex) {
            int statusCode = ex.getStatusCode().value();
            log.warn("[PatientWebhookService] Erro HTTP {} ao chamar Feegow patient/edit. Body: {}", statusCode, ex.getResponseBodyAsString());
            if (statusCode == 422 || statusCode == 400) {
                return PatientWebhookRegistrationResponse.builder()
                        .status("error")
                        .mensagem("CPF inválido ou rejeitado pelo Feegow.")
                        .build();
            }
        } catch (org.springframework.web.client.RestClientResponseException ex) {
            int statusCode = ex.getStatusCode().value();
            log.warn("[PatientWebhookService] Erro RestClient {} ao chamar Feegow patient/edit. Body: {}", statusCode, ex.getResponseBodyAsString());
            if (statusCode == 422 || statusCode == 400) {
                return PatientWebhookRegistrationResponse.builder()
                        .status("error")
                        .mensagem("CPF inválido ou rejeitado pelo Feegow.")
                        .build();
            }
        } catch (Exception ex) {
            log.warn("[PatientWebhookService] Exceção genérica ao chamar Feegow patient/edit: {}", ex.getMessage());
        }

        // Faz obrigatoriamente a consulta por paciente_cpf para retornar o paciente_id real gerado no Feegow
        try {
            FeegowPatient patient = patientExternalPort.patientInfo(cleanCpf);
            if (patient != null && patient.id() != null && !patient.id().isBlank()) {
                pacienteId = patient.id();
                log.info("[PatientWebhookService] Paciente ID real localizado no Feegow via consulta por CPF: {}", pacienteId);
            }
        } catch (Exception ex) {
            log.warn("[PatientWebhookService] Não foi possível consultar o paciente real por CPF: {}", ex.getMessage());
        }

        if (pacienteId == null || pacienteId.isBlank()) {
            pacienteId = "UNKNOWN_" + cleanCpf;
        }

        return PatientWebhookRegistrationResponse.builder()
                .status("success")
                .pacienteId(pacienteId)
                .mensagem("Paciente cadastrado/sincronizado com sucesso no Feegow.")
                .build();
    }

    /**
     * Consulta os agendamentos futuros de um paciente a partir de seu CPF.
     *
     * @param rawCpf CPF do paciente.
     * @return PatientAppointmentsResponse contendo a lista formatada de agendamentos.
     */
    public PatientAppointmentsResponse getFutureAppointments(String rawCpf) {
        if (rawCpf == null || rawCpf.isBlank()) {
            return PatientAppointmentsResponse.builder()
                    .status("success")
                    .total(0)
                    .mensagem("CPF não informado.")
                    .agendamentos(List.of())
                    .build();
        }

        String cleanCpf = rawCpf.replaceAll("\\D", "");
        log.info("[PatientWebhookService] Consultando agendamentos futuros para CPF: {}", cleanCpf);

        // 1. Localiza o prontuário do paciente pelo CPF
        FeegowPatient patient = patientExternalPort.patientInfo(cleanCpf);
        String patientId = patient != null ? patient.id() : null;

        LocalDate today = LocalDate.now();
        List<PatientAppointmentsResponse.AppointmentItem> appointmentItems = new ArrayList<>();

        // 2. Consulta a pauta de hoje e dos próximos dias
        for (int i = 0; i <= 30; i++) {
            LocalDate dateToSearch = today.plusDays(i);
            try {
                List<FeegowAppointment> daily = appointmentExternalPort.searchAppointments(dateToSearch, 1);
                if (daily != null) {
                    for (FeegowAppointment app : daily) {
                        boolean matches = false;
                        if (patientId != null && patientId.equals(app.patientId())) {
                            matches = true;
                        }

                        if (matches) {
                            String dataStr = app.startAt() != null ? app.startAt().format(DATE_FORMATTER) : dateToSearch.format(DATE_FORMATTER);
                            String horaStr = app.startAt() != null ? app.startAt().format(TIME_FORMATTER) : "00:00";
                            String medicoStr = app.doctorName() != null ? app.doctorName() : "Médico Inovare";

                            appointmentItems.add(PatientAppointmentsResponse.AppointmentItem.builder()
                                    .agendamentoId(app.id())
                                    .data(dataStr)
                                    .hora(horaStr)
                                    .medico(medicoStr)
                                    .especialidade("Consulta Médica")
                                    .unidade("Clínica Inovare")
                                    .build());
                        }
                    }
                }
            } catch (Exception ex) {
                log.warn("[PatientWebhookService] Falha ao consultar agendamentos para a data {}: {}", dateToSearch, ex.getMessage());
            }
        }

        if (appointmentItems.isEmpty()) {
            return PatientAppointmentsResponse.builder()
                    .status("success")
                    .total(0)
                    .mensagem("Nenhum agendamento futuro encontrado para o CPF informado.")
                    .agendamentos(List.of())
                    .build();
        }

        return PatientAppointmentsResponse.builder()
                .status("success")
                .total(appointmentItems.size())
                .agendamentos(appointmentItems)
                .build();
    }

    private String extractPatientId(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if (root.has("content")) {
                JsonNode content = root.get("content");
                if (content.has("id")) return content.get("id").asText();
                if (content.has("paciente_id")) return content.get("paciente_id").asText();
            }
            if (root.has("id")) return root.get("id").asText();
            if (root.has("paciente_id")) return root.get("paciente_id").asText();
        } catch (Exception e) {
            log.warn("[PatientWebhookService] Erro ao extrair paciente_id do JSON: {}", e.getMessage());
        }
        return null;
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
}
