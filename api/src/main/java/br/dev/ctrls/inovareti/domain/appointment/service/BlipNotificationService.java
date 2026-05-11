package br.dev.ctrls.inovareti.domain.appointment.service;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import br.dev.ctrls.inovareti.domain.appointment.dto.AppointmentTemplateData;
import br.dev.ctrls.inovareti.domain.appointment.dto.BlipTemplateDto;
import br.dev.ctrls.inovareti.domain.appointment.AppointmentTemplateMapping;
import br.dev.ctrls.inovareti.domain.appointment.AppointmentDoctorMapping;
import br.dev.ctrls.inovareti.domain.appointment.AppointmentTemplateMappingRepository;
import br.dev.ctrls.inovareti.domain.appointment.AppointmentDoctorMappingRepository;

import br.dev.ctrls.inovareti.domain.appointment.AppointmentMotorProperties;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class BlipNotificationService {

    private final BlipLIMEClient limeClient;
    private final AppointmentTemplateMappingRepository templateMappingRepository;
    private final AppointmentDoctorMappingRepository doctorMappingRepository;
    private final AppointmentMotorProperties properties;

    @Value("${app.appointment.motor.blip-waba-namespace}")
    private String blipWabaNamespace;

    public BlipNotificationService(
            BlipLIMEClient limeClient, 
            AppointmentTemplateMappingRepository templateMappingRepository, 
            AppointmentDoctorMappingRepository doctorMappingRepository,
            AppointmentMotorProperties properties) {
        this.limeClient = limeClient;
        this.templateMappingRepository = templateMappingRepository;
        this.doctorMappingRepository = doctorMappingRepository;
        this.properties = properties;
    }

    public List<BlipTemplateDto> fetchTemplatesFromBlip() {
        Map<String, Object> command = Map.of(
            "id", UUID.randomUUID().toString(),
            "to", "postmaster@wa.gw.msging.net",
            "method", "get",
            "uri", "/message-templates"
        );
        try {
            var response = limeClient.executeCommand(command, BlipLIMEClient.AuthorizationScope.ROUTER);
            Map<String, Object> body = response.getBody();
            if (body == null || !body.containsKey("resource")) return List.of();
            
            Object resourceObj = body.get("resource");
            if (resourceObj instanceof Map<?, ?> resourceMap) {
                Object docsObj = resourceMap.get("documents");
                if (docsObj instanceof List<?> docs) {
                    List<BlipTemplateDto> templates = new ArrayList<>();
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    for (Object item : docs) {
                        if (item instanceof Map<?, ?> itemMap) {
                            String status = String.valueOf(itemMap.get("status"));
                            if (!"APPROVED".equalsIgnoreCase(status)) continue;
                            
                            String id = String.valueOf(itemMap.get("id"));
                            String name = String.valueOf(itemMap.get("name"));
                            String bodyContent = "{}";
                            try {
                                bodyContent = mapper.writeValueAsString(itemMap);
                            } catch (Exception ignored) {}
                            templates.add(new BlipTemplateDto(id, name, bodyContent));
                        }
                    }
                    return templates;
                }
            }
            return List.of();
        } catch (Exception ex) {
            log.error("Erro ao buscar templates no Blip", ex);
            return List.of();
        }
    }

    public void sendAppointmentNotification(String destination, AppointmentTemplateData appointmentData) {
        sendTemplateMessage(destination, "confirmacao_consulta_v6_itsm", appointmentData);
    }

    public void sendTemplateMessage(String destination, String templateName, AppointmentTemplateData appointmentData) {
        String normalizedDestination = limeClient.normalizeUserIdentity(destination);

        if (!normalizedDestination.contains("42991617187")) {
            log.warn("[SANDBOX] Disparo bloqueado para número real: {}.", destination);
            return;
        }

        List<Map<String, String>> parameters = buildDynamicParameters(templateName, appointmentData);
        String appointmentId = appointmentData == null ? "" : Objects.toString(appointmentData.appointmentId(), "");

        Map<String, Object> button = Map.of(
            "type", "button", "sub_type", "quick_reply", "index", 0,
            "parameters", List.of(Map.of("type", "payload", "payload", "confirm_" + appointmentId))
        );

        List<Map<String, Object>> components = new ArrayList<>();
        components.add(Map.of("type", "body", "parameters", parameters));
        components.add(button);

        Map<String, Object> content = Map.of(
            "type", "template",
            "template", Map.of(
                "name", templateName,
                "namespace", resolveWabaNamespace(),
                "language", Map.of("code", "pt_BR", "policy", "deterministic"),
                "components", components
            )
        );

        Map<String, Object> payload = Map.of(
            "id", UUID.randomUUID().toString(),
            "to", normalizedDestination,
            "from", "roteadorprincipal57@msging.net",
            "type", "application/json",
            "content", content,
            "metadata", Map.of("appointmentId", appointmentId)
        );

        var response = limeClient.executeMessage(payload, BlipLIMEClient.AuthorizationScope.ROUTER);
        log.info("Template enviado. destination={}, template={}, status={}", normalizedDestination, templateName, response.getStatusCode());
    }

    private List<Map<String, String>> buildDynamicParameters(String templateName, AppointmentTemplateData appointmentData) {
        List<AppointmentTemplateMapping> mappings = templateMappingRepository
            .findByTemplateNameIgnoreCaseOrderByPlaceholderIndexAsc(templateName);

        if (mappings.isEmpty()) return List.of();

        List<Map<String, String>> parameters = new ArrayList<>();
        mappings.stream()
            .sorted(Comparator.comparing(AppointmentTemplateMapping::getPlaceholderIndex))
            .forEach(mapping -> {
                String fieldName = mapping.getFeegowFieldName();
                String value = resolveDynamicFieldValue(appointmentData, fieldName);
                
                String safeValue = "Informado na Recepção";
                if (value != null && !value.isBlank() && !"Informação não disponível".equals(value.trim())) {
                    safeValue = value.trim();
                } else {
                    if (fieldName != null) {
                        if (fieldName.toLowerCase().contains("profissional") || fieldName.toLowerCase().contains("doctor")) {
                            safeValue = "Médico";
                        } else if (fieldName.toLowerCase().contains("patient") || fieldName.toLowerCase().contains("paciente")) {
                            safeValue = "Paciente";
                        }
                    }
                }
                
                parameters.add(Map.of("type", "text", "text", safeValue));
            });

        return parameters;
    }

    private String resolveDynamicFieldValue(AppointmentTemplateData appointmentData, String fieldName) {
        if (appointmentData == null || fieldName == null || fieldName.isBlank()) return null;

        if ("profissionalNome".equalsIgnoreCase(fieldName.trim())) {
            try {
                String doctorId = appointmentData.doctorId();
                if (doctorId == null || doctorId.isBlank() || "Informação não disponível".equals(doctorId.trim())) return null;
                return doctorMappingRepository.findByProfissionalId(doctorId.trim())
                        .map(AppointmentDoctorMapping::getProfissionalNome)
                        .orElse(null);
            } catch (Exception ex) { return null; }
        }

        try {
            Method accessor = AppointmentTemplateData.class.getMethod(fieldName.trim());
            Object value = accessor.invoke(appointmentData);
            return value == null ? null : String.valueOf(value);
        } catch (ReflectiveOperationException ex) { return null; }
    }

    private String resolveWabaNamespace() {
        return (blipWabaNamespace != null && !blipWabaNamespace.isBlank()) ? blipWabaNamespace : "";
    }
}