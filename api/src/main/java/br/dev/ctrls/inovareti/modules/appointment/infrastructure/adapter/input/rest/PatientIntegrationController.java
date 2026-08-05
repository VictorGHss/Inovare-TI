package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.input.rest;

import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentExternalPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.FeegowAppointment;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.FeegowPatient;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.PatientExternalPort;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.utils.DateParserUtils;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.micrometer.observation.annotation.Observed;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Controller REST responsável pela gestão e integração de Pacientes com o Feegow ERP.
 * Expõe endpoints simplificados em JSON para busca, cadastro e consulta de agendamentos via Take Blip.
 */
@Slf4j
@RestController
@RequestMapping("/v1/feegow/patients")
@RequiredArgsConstructor
@Observed
public class PatientIntegrationController {

    private final PatientExternalPort patientExternalPort;
    private final AppointmentExternalPort appointmentExternalPort;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreatePatientRequest {
        private String nome;
        private String cpf;
        private String dataNascimento;
        private String telefone;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PatientSearchResponse {
        private boolean found;
        private FeegowPatient patient;
        private String message;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PatientCreateResponse {
        private boolean success;
        private FeegowPatient patient;
        private String message;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PatientAppointmentsResponse {
        private boolean hasAppointments;
        private String formattedText;
        private List<FeegowAppointment> appointments;
        private String message;
    }

    /**
     * Endpoint de busca de paciente por CPF.
     * Sanitiza o CPF removendo caracteres não numéricos e realiza a consulta no Feegow ERP.
     */
    @GetMapping("/search")
    public ResponseEntity<PatientSearchResponse> searchByCpf(@RequestParam(name = "cpf") String rawCpf) {
        if (rawCpf == null || rawCpf.isBlank()) {
            return ResponseEntity.badRequest().body(
                    PatientSearchResponse.builder()
                            .found(false)
                            .message("CPF não informado.")
                            .build()
            );
        }

        String cleanCpf = rawCpf.replaceAll("\\D", "");
        log.info("[BLIP-INBOUND] Recebida chamada no endpoint /v1/feegow/patients/search para o CPF: {}", cleanCpf);

        FeegowPatient patient = patientExternalPort.patientInfo(cleanCpf);

        if (patient != null && patient.id() != null && !patient.id().isBlank()) {
            return ResponseEntity.ok(
                    PatientSearchResponse.builder()
                            .found(true)
                            .patient(patient)
                            .message("Paciente localizado com sucesso.")
                            .build()
            );
        }

        return ResponseEntity.ok(
                PatientSearchResponse.builder()
                        .found(false)
                        .message("Paciente não localizado para o CPF informado.")
                        .build()
        );
    }

    /**
     * Endpoint de cadastro de paciente.
     * Recebe Nome, CPF e Data de Nascimento, normaliza a data para ISO (YYYY-MM-DD) e consome POST /patient/create.
     */
    @PostMapping("/create")
    public ResponseEntity<PatientCreateResponse> createPatient(@RequestBody CreatePatientRequest request) {
        if (request == null || request.getNome() == null || request.getCpf() == null || request.getDataNascimento() == null) {
            return ResponseEntity.badRequest().body(
                    PatientCreateResponse.builder()
                            .success(false)
                            .message("Campos obrigatórios ausentes: nome, cpf e dataNascimento são exigidos.")
                            .build()
            );
        }

        String cleanCpf = request.getCpf().replaceAll("\\D", "");
        log.info("[BLIP-INBOUND] Recebida chamada no endpoint /v1/feegow/patients/create para Nome: '{}', CPF: '{}', Data: '{}'",
                request.getNome(), cleanCpf, request.getDataNascimento());

        String formattedIsoDate;
        try {
            formattedIsoDate = DateParserUtils.parseToIsoDate(request.getDataNascimento());
        } catch (IllegalArgumentException ex) {
            log.warn("[REST] [PATIENT-CREATE] Data de nascimento inválida: '{}'", request.getDataNascimento());
            return ResponseEntity.badRequest().body(
                    PatientCreateResponse.builder()
                            .success(false)
                            .message(ex.getMessage())
                            .build()
            );
        }

        try {
            FeegowPatient createdPatient = patientExternalPort.createPatient(
                    request.getNome().trim(),
                    cleanCpf,
                    formattedIsoDate,
                    request.getTelefone()
            );

            return ResponseEntity.status(HttpStatus.CREATED).body(
                    PatientCreateResponse.builder()
                            .success(true)
                            .patient(createdPatient)
                            .message("Paciente cadastrado com sucesso no Feegow ERP.")
                            .build()
            );
        } catch (Exception ex) {
            log.error("[REST] [PATIENT-CREATE] Erro ao cadastrar paciente no Feegow: {}", ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    PatientCreateResponse.builder()
                            .success(false)
                            .message("Falha na comunicação com o Feegow ERP: " + ex.getMessage())
                            .build()
            );
        }
    }

    /**
     * Endpoint de consulta das consultas/agendamentos futuros do paciente no Feegow ERP.
     * Devolve um texto formatado limpo e pronto para exibição no WhatsApp via Take Blip.
     */
    @GetMapping("/{patientId}/appointments")
    public ResponseEntity<PatientAppointmentsResponse> getPatientAppointments(@PathVariable("patientId") String patientId) {
        if (patientId == null || patientId.isBlank()) {
            return ResponseEntity.badRequest().body(
                    PatientAppointmentsResponse.builder()
                            .hasAppointments(false)
                            .formattedText("Identificador de paciente inválido.")
                            .message("patientId não informado.")
                            .build()
            );
        }

        log.info("[BLIP-INBOUND] Recebida chamada no endpoint GET /v1/feegow/patients/{}/appointments", patientId);

        List<FeegowAppointment> appointments = appointmentExternalPort.searchPatientAppointments(patientId);
        boolean hasAppointments = appointments != null && !appointments.isEmpty();
        String formattedText = buildCleanFormattedAppointments(appointments);

        return ResponseEntity.ok(
                PatientAppointmentsResponse.builder()
                        .hasAppointments(hasAppointments)
                        .formattedText(formattedText)
                        .appointments(appointments)
                        .message(hasAppointments ? "Consultas encontradas com sucesso." : "Nenhuma consulta futura encontrada.")
                        .build()
        );
    }

    private String buildCleanFormattedAppointments(List<FeegowAppointment> appointments) {
        if (appointments == null || appointments.isEmpty()) {
            return "Nenhuma consulta futura localizada para o cadastro informado.";
        }

        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");
        StringBuilder sb = new StringBuilder("📅 Suas Próximas Consultas:\n\n");

        for (FeegowAppointment appt : appointments) {
            String dateTimeStr = appt.startAt() != null ? appt.startAt().format(dateFormatter) : "Data a definir";
            String doctor = appt.doctorName() != null ? appt.doctorName() : "Profissional Inovare";
            String procedure = appt.procedureName() != null ? appt.procedureName() : "Consulta/Exame";
            String unit = appt.unitName() != null ? appt.unitName() : "Clínica Inovare";

            sb.append("• ").append(dateTimeStr)
                    .append(" - ").append(doctor)
                    .append(" (").append(procedure).append(")")
                    .append(" - ").append(unit).append("\n");
        }

        sb.append("\nQualquer dúvida ou alteração, estamos à disposição!");
        return sb.toString().trim();
    }
}
