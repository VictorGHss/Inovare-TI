package br.dev.ctrls.inovareti.domain.appointment.usecase;

import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.dev.ctrls.inovareti.core.exception.NotFoundException;
import br.dev.ctrls.inovareti.domain.appointment.AppointmentCategory;
import br.dev.ctrls.inovareti.domain.appointment.AppointmentConfig;
import br.dev.ctrls.inovareti.domain.appointment.AppointmentConfigRepository;
import br.dev.ctrls.inovareti.domain.appointment.AppointmentDoctorMapping;
import br.dev.ctrls.inovareti.domain.appointment.AppointmentDoctorMappingRepository;
import br.dev.ctrls.inovareti.domain.appointment.AppointmentSession;
import br.dev.ctrls.inovareti.domain.appointment.AppointmentSessionRepository;
import br.dev.ctrls.inovareti.domain.appointment.BlipClient;
import br.dev.ctrls.inovareti.domain.appointment.BlipErrorMapper;
import br.dev.ctrls.inovareti.domain.appointment.FeegowClient;
import br.dev.ctrls.inovareti.domain.appointment.dto.AppointmentTemplateData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class SendAppointmentTemplateUseCase {

    private static final DateTimeFormatter BRAZILIAN_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter SHORT_BRAZILIAN_DATE = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter BRAZILIAN_TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final String DEFAULT_TEMPLATE_VALUE = "Informação não disponível";
    private static final String DEFAULT_PROVIDER_VALUE = "Clínica Inovare";

    private final AppointmentConfigRepository appointmentConfigRepository;
    private final AppointmentSessionRepository appointmentSessionRepository;
    private final FeegowClient feegowClient;
    private final BlipClient blipClient;
    private final ObjectMapper objectMapper;
    private final AppointmentDoctorMappingRepository appointmentDoctorMappingRepository;

    @Transactional
    public boolean execute(AppointmentSession session, AppointmentCategory category) {

        AppointmentConfig config = appointmentConfigRepository.findByCategory(category)
            .orElseThrow(() -> new NotFoundException("Configuração não encontrada para categoria " + category));

        // Busca o agendamento real
        FeegowClient.FeegowAppointment appointment = feegowClient.searchAppointments(
            session.getAppointmentAt().toLocalDate(),
            1, // status Marcado
            session.getDoctorProfissionalId()
        ).stream()
         .filter(a -> a.id().equals(session.getFeegowAppointmentId()))
         .findFirst()
         .orElse(new FeegowClient.FeegowAppointment(
            session.getFeegowAppointmentId(),
            session.getPatientId(),
            session.getDoctorProfissionalId(),
            null,
            null,
            session.getAppointmentAt(),
            null));

        FeegowClient.FeegowPatient patient = feegowClient.patientInfo(session.getPatientId());

        String normalizedProfissionalId = session.getDoctorProfissionalId();
        if (normalizedProfissionalId != null) {
            normalizedProfissionalId = normalizedProfissionalId.trim();
        }

        log.info("[DIAGNOSTICO MÉDICO] Buscando nome para ProfissionalID: {} | Nome vindo da Feegow: {}",
            normalizedProfissionalId,
            appointment.doctorName());

        // Inversão de prioridade: busca no banco primeiro
        String doctorName = null;
        if (normalizedProfissionalId != null && !normalizedProfissionalId.isBlank()) {
            doctorName = appointmentDoctorMappingRepository.findByProfissionalId(normalizedProfissionalId)
                .map(AppointmentDoctorMapping::getProfissionalNome)
                .filter(nome -> !nome.isBlank())
                .orElse(null);
        }
            
        if (doctorName == null || doctorName.isBlank()) {
            doctorName = appointment.doctorName(); // Fallback para a API da Feegow
        }
        
        if (doctorName == null || doctorName.isBlank()) {
            log.debug("[FEEGOW] Nome do médico não encontrado para ID: {}", normalizedProfissionalId);
            doctorName = "Doutor(a)";
        }

        String appointmentDate = appointment.startAt() != null
            ? appointment.startAt().toLocalDate().format(BRAZILIAN_DATE)
            : DEFAULT_TEMPLATE_VALUE;
        String appointmentDateShort = appointment.startAt() != null
            ? appointment.startAt().toLocalDate().format(SHORT_BRAZILIAN_DATE)
            : DEFAULT_TEMPLATE_VALUE;
        String appointmentTime = appointment.startAt() != null
            ? appointment.startAt().toLocalTime().format(BRAZILIAN_TIME)
            : DEFAULT_TEMPLATE_VALUE;

        AppointmentTemplateData templateData = new AppointmentTemplateData(
            fallbackValue(appointment.id()),
            fallbackValue(appointment.patientId()),
            fallbackValue(patient != null ? patient.name() : null),
            fallbackValue(patient != null ? patient.phone() : null),
            fallbackValue(appointment.doctorId()),
            fallbackProviderValue(doctorName),
            DEFAULT_PROVIDER_VALUE,
            fallbackProviderValue(appointment.unitName()),
            appointmentDate,
            appointmentDateShort,
            appointmentTime,
            appointmentDate);

        try {
            if ("confirmacao_consulta_v5".equalsIgnoreCase(config.getTemplateId())) {
                blipClient.sendAppointmentNotification(session.getPhoneNumber(), templateData);
            } else {
                blipClient.sendTemplateMessage(session.getPhoneNumber(), config.getTemplateId(), templateData);
            }
            session.setStatusDetails(null);
            appointmentSessionRepository.save(session);
            return true;
        } catch (RestClientResponseException ex) {
            Integer blipCode = extractBlipErrorCode(ex.getResponseBodyAsString());
            BlipErrorMapper mappedError = BlipErrorMapper.fromCode(blipCode);
            String statusDetails = blipCode == null
                    ? mappedError.getDescription()
                    : "Código " + blipCode + ": " + mappedError.getDescription();

            session.setStatusDetails(statusDetails);
            appointmentSessionRepository.save(session);

            log.error("Falha ao enviar template para Blip. sessionId={}, category={}, statusHttp={}, blipCode={}, details={}, responseBody={}",
                    session.getId(),
                    category,
                    ex.getStatusCode().value(),
                    blipCode,
                    statusDetails,
                    ex.getResponseBodyAsString(),
                    ex);
            return false;
        } catch (RuntimeException ex) {
            String statusDetails = "Erro desconhecido na API do Blip.";
            session.setStatusDetails(statusDetails);
            appointmentSessionRepository.save(session);

            log.error("Falha inesperada ao enviar template para Blip. sessionId={}, category={}, details={}",
                    session.getId(),
                    category,
                    statusDetails,
                    ex);
            return false;
        }
    }

    private Integer extractBlipErrorCode(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }

        try {
            JsonNode root = objectMapper.readTree(responseBody);

            Integer topLevelCode = asInteger(root.get("code"));
            if (topLevelCode != null) {
                return topLevelCode;
            }

            JsonNode errorNode = root.get("error");
            Integer nestedErrorCode = asInteger(errorNode != null ? errorNode.get("code") : null);
            if (nestedErrorCode != null) {
                return nestedErrorCode;
            }

            JsonNode resourceNode = root.get("resource");
            return asInteger(resourceNode != null ? resourceNode.get("code") : null);
        } catch (JsonProcessingException ignored) {
            return null;
        }
    }

    private Integer asInteger(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }

        if (node.isInt() || node.isLong()) {
            return node.intValue();
        }

        if (node.isTextual()) {
            try {
                return Integer.valueOf(node.asText().trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        return null;
    }

    private String fallbackValue(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_TEMPLATE_VALUE;
        }

        return value.trim();
    }

    private String fallbackProviderValue(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_PROVIDER_VALUE;
        }

        return value.trim();
    }
}
