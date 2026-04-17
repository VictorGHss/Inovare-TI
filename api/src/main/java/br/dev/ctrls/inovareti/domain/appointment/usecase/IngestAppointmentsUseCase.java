package br.dev.ctrls.inovareti.domain.appointment.usecase;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.dev.ctrls.inovareti.domain.appointment.AppointmentCategory;
import br.dev.ctrls.inovareti.domain.appointment.AppointmentMotorProperties;
import br.dev.ctrls.inovareti.domain.appointment.AppointmentSendIdempotencyService;
import br.dev.ctrls.inovareti.domain.appointment.AppointmentSession;
import br.dev.ctrls.inovareti.domain.appointment.AppointmentSessionRepository;
import br.dev.ctrls.inovareti.domain.appointment.AppointmentSessionStatus;
import br.dev.ctrls.inovareti.domain.appointment.DoctorMappingRepository;
import br.dev.ctrls.inovareti.domain.appointment.FeegowClient;
import br.dev.ctrls.inovareti.domain.appointment.NoopAppointmentSendIdempotencyService;
import br.dev.ctrls.inovareti.domain.appointment.dto.FeegowPatientDetailsDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class IngestAppointmentsUseCase {

    private static final int FEEGOW_STATUS_AGENDADO = 1;

    private final AppointmentMotorProperties appointmentMotorProperties;
    private final FeegowClient feegowClient;
    private final ObjectMapper objectMapper;
    private final DoctorMappingRepository doctorMappingRepository;
    private final AppointmentSessionRepository appointmentSessionRepository;
    private final SendAppointmentTemplateUseCase sendAppointmentTemplateUseCase;
    private final Optional<AppointmentSendIdempotencyService> appointmentSendIdempotencyService;
    private final Optional<NoopAppointmentSendIdempotencyService> noopAppointmentSendIdempotencyService;

    @Transactional
    public IngestionSummary execute() {
        LocalDate targetDate = LocalDate.now().plusDays(1);
        log.info("Consultando Feegow para ingestão de agendamentos com status Marcado (ID={})", FEEGOW_STATUS_AGENDADO);
        List<FeegowClient.FeegowAppointment> appointments = feegowClient.searchAppointments(
                targetDate,
                FEEGOW_STATUS_AGENDADO,
            null);
        int totalReceived = appointments.size();

        int filteredReceived = appointments.size();

        int created = 0;
        int messagesSent = 0;
        for (FeegowClient.FeegowAppointment appointment : appointments) {
            if (!hasMappedDoctor(appointment.doctorId())) {
                log.info("Agendamento ignorado por ausência de mapeamento do médico. appointmentId={}, profissional_id={}",
                        appointment.id(),
                        appointment.doctorId());
                continue;
            }

            String feegowAppointmentId = normalizeFeegowAppointmentId(appointment.id());
            if (feegowAppointmentId.isBlank()) {
                log.warn("ID Feegow vazio. Conteúdo recebido: {} | rawId={}",
                        serializeAppointmentForLog(appointment),
                        appointment.id());
                continue;
            }

            if (appointmentSessionRepository.findByFeegowAppointmentId(feegowAppointmentId).isPresent()) {
                continue;
            }

            boolean canSend = appointmentSendIdempotencyService
                    .map(service -> service.registerIfFirstSend(feegowAppointmentId))
                    .orElseGet(() -> noopAppointmentSendIdempotencyService
                            .map(service -> service.registerIfFirstSend(feegowAppointmentId))
                            .orElse(true));

            if (!canSend) {
                log.info("Envio ignorado por idempotência Redis. appointmentId={}", feegowAppointmentId);
                continue;
            }

            String patientId = appointment.patientId();
            FeegowPatientDetailsDto.PatientItem patientDetails;
            try {
                patientDetails = feegowClient.getPatientDetails(patientId);
            } catch (RestClientResponseException ex) {
                int statusCode = ex.getStatusCode().value();
                if (statusCode == 404 || statusCode == 500) {
                    log.error("Falha ao recuperar contato do paciente ID {}. Pulando agendamento.", patientId, ex);
                } else {
                    log.error("Erro ao recuperar contato do paciente ID {}. statusCode={}. Pulando agendamento.", patientId,
                            statusCode,
                            ex);
                }
                continue;
            } catch (RuntimeException ex) {
                log.error("Falha ao recuperar contato do paciente ID {}. Pulando agendamento.", patientId, ex);
                continue;
            }

            String phoneSourceField = "celulares";
            String patientPhone = firstNonBlank(patientDetails != null ? patientDetails.getCelulares() : null);
            if (patientPhone == null || patientPhone.isBlank()) {
                patientPhone = firstNonBlank(patientDetails != null ? patientDetails.getTelefones() : null);
                phoneSourceField = "telefones";
            }

            String phoneNumber = normalizePhoneNumberForBlip(patientPhone);
            if (patientPhone == null || patientPhone.isBlank() || phoneNumber.isBlank()) {
                log.error("Falha ao recuperar contato do paciente ID {}. Pulando agendamento.", patientId);
                continue;
            }

            log.info("Dados do paciente recuperados: ID={}, Telefone={}, CampoUtilizado={}",
                    patientId,
                    patientPhone,
                    phoneSourceField);

            log.info("Agendamento Ingerido: Paciente={}, Telefone Feegow={}, Telefone Normalizado para Blip={}",
                    patientDetails != null ? patientDetails.getNome() : null,
                    patientPhone,
                    phoneNumber);

            AppointmentSession session = AppointmentSession.builder()
                    .feegowAppointmentId(feegowAppointmentId)
                    .patientId(appointment.patientId())
                    .phoneNumber(phoneNumber)
                    .doctorProfissionalId(appointment.doctorId())
                    .appointmentAt(appointment.startAt())
                    .status(AppointmentSessionStatus.PENDING)
                    .lastInteractionAt(LocalDateTime.now())
                    .build();

            AppointmentSession saved = appointmentSessionRepository.save(session);
            log.info("Agendamento salvo localmente: ID Feegow = {}", saved.getFeegowAppointmentId());
            boolean templateSent = sendAppointmentTemplateUseCase.execute(saved, AppointmentCategory.CONFIRMATION);
            created++;
            if (templateSent) {
                messagesSent++;
            }
        }

        String mode = appointmentMotorProperties.isTestMode() ? "TEST" : "PROD";
        log.info("Ingestão de consultas executada. totalRecebido={}, totalAposFiltro={}, sessoesCriadas={}, mensagensEnviadas={}, modo={}",
                totalReceived,
                filteredReceived,
                created,
                messagesSent,
                mode);

        return new IngestionSummary(totalReceived, filteredReceived, created, messagesSent, mode);
    }

    public record IngestionSummary(int totalReceived, int filteredReceived, int sessionsCreated, int messagesSent, String mode) {
    }

    private String normalizeFeegowAppointmentId(String feegowAppointmentId) {
        if (feegowAppointmentId == null) {
            return "";
        }

        String normalized = feegowAppointmentId.trim();
        if (normalized.matches("^\\d+\\.0+$")) {
            return normalized.substring(0, normalized.indexOf('.'));
        }

        return normalized;
    }

    private boolean hasMappedDoctor(String profissionalId) {
        if (profissionalId == null || profissionalId.isBlank()) {
            return false;
        }

        return doctorMappingRepository.findByProfissionalId(profissionalId.trim()).isPresent();
    }

    private String normalizePhoneNumberForBlip(String originalPhone) {
        if (originalPhone == null || originalPhone.isBlank()) {
            return "";
        }

        String trimmed = originalPhone.trim();

        // Alguns cadastros retornam múltiplos contatos no mesmo campo. Mantém apenas o primeiro.
        if (trimmed.contains(",") || trimmed.contains("/") || trimmed.contains(" ")) {
            String[] parts = trimmed.split("[,/\\s]+");
            if (parts.length == 0 || parts[0] == null || parts[0].isBlank()) {
                return "";
            }

            trimmed = parts[0].trim();
        }

        String digitsOnly = trimmed.replaceAll("\\D", "");
        if (digitsOnly.isBlank()) {
            return "";
        }

        if (digitsOnly.startsWith("55")) {
            return "+" + digitsOnly;
        }

        return "+55" + digitsOnly;
    }

    private String serializeAppointmentForLog(FeegowClient.FeegowAppointment appointment) {
        if (appointment == null) {
            return "{}";
        }

        try {
            return objectMapper.writeValueAsString(appointment);
        } catch (JsonProcessingException ex) {
            return String.valueOf(appointment);
        }
    }

    private String firstNonBlank(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }

        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }
}
