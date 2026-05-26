package br.dev.ctrls.inovareti.modules.appointment.application.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.client.BlipLIMEClient;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.AppointmentMotorProperties;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;

import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentTemplateMapping;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentTemplateMappingRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.application.dto.AppointmentTemplateData;
import br.dev.ctrls.inovareti.modules.appointment.application.dto.BlipTemplateDto;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class BlipNotificationService {

    private final BlipLIMEClient limeClient;
    private final AppointmentTemplateMappingRepositoryPort templateMappingRepository;
    private final AppointmentMotorProperties motorProperties;

    public BlipNotificationService(
            BlipLIMEClient limeClient,
            AppointmentTemplateMappingRepositoryPort templateMappingRepository,
            AppointmentMotorProperties motorProperties) {
        this.limeClient = limeClient;
        this.templateMappingRepository = templateMappingRepository;
        this.motorProperties = motorProperties;
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
            // A resposta agora é um Map direto, não um ResponseEntity
            Map<String, Object> body = response;
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
                            } catch (JsonProcessingException ignored) {}
                            templates.add(new BlipTemplateDto(id, name, bodyContent));
                        }
                    }
                    return templates;
                }
            }
            return List.of();
        } catch (RuntimeException ex) {
            log.error("Erro ao buscar templates no Blip", ex);
            return List.of();
        }
    }

    public void sendAppointmentNotification(String destination, AppointmentTemplateData appointmentData) {
        sendTemplateMessage(destination, "confirmacao_consulta_v6_itsm", appointmentData);
    }

    public void sendTemplateMessage(String destination, String templateName, AppointmentTemplateData appointmentData) {
        String normalizedDestination = limeClient.normalizeUserIdentity(destination);

        boolean isHomologDoctor = appointmentData != null && "70".equals(appointmentData.doctorId());
        if (!normalizedDestination.contains("42991617187") && !isHomologDoctor) {
            log.warn("[SANDBOX] Disparo bloqueado para número real: {}. Dr ID: {}", destination, appointmentData != null ? appointmentData.doctorId() : "null");
            return;
        }

        List<Map<String, String>> parameters = buildDynamicParameters(templateName, appointmentData);
        String appointmentId = appointmentData == null ? "" : Objects.toString(appointmentData.appointmentId(), "");

        log.info("[PARAMS TEMPLATE] destination={}, template={}, params={}", normalizedDestination, templateName, parameters);

        if (parameters.isEmpty()) {
            log.error("[ABORT] Parâmetros vazios para o template '{}'. Envio cancelado para evitar mensagem sem conteúdo. destination={}",
                templateName, normalizedDestination);
            return;
        }
        Map<String, Object> confirmButton = Map.of(
            "type", "button", "sub_type", "quick_reply", "index", 0,
            "parameters", List.of(Map.of("type", "payload", "payload", "confirm_" + appointmentId))
        );

        Map<String, Object> alterButton = Map.of(
            "type", "button", "sub_type", "quick_reply", "index", 1,
            "parameters", List.of(Map.of("type", "payload", "payload", "alter_" + appointmentId))
        );

        List<Map<String, Object>> components = new ArrayList<>();
        components.add(Map.of("type", "body", "parameters", parameters));
        components.add(confirmButton);
        components.add(alterButton);

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
        // Verifica o status customizado no Map de fallback ou a presença da resposta
        Object status = response.getOrDefault("status", "unknown");
        log.info("Template enviado. destination={}, template={}, status={}", normalizedDestination, templateName, status);
    }

    private List<Map<String, String>> buildDynamicParameters(String templateName, AppointmentTemplateData appointmentData) {
        List<AppointmentTemplateMapping> mappings = templateMappingRepository
            .findByTemplateNameIgnoreCaseOrderByPlaceholderIndexAsc(templateName);

        if (mappings.isEmpty()) {
            log.warn("[TEMPLATE MAPPING] Nenhum mapeamento encontrado para o template: '{}'. Verifique a tabela appointment_template_mapping.", templateName);
            return List.of();
        }

        List<Map<String, String>> parameters = new ArrayList<>();
        mappings.stream()
            .sorted(Comparator.comparing(AppointmentTemplateMapping::getPlaceholderIndex))
            .forEach(mapping -> {
                String fieldName = mapping.getFeegowFieldName();
                String value = resolveDynamicFieldValue(appointmentData, fieldName);
                
                String safeValue = "Recepção";
                if (value != null && !value.isBlank() && !"null".equalsIgnoreCase(value.trim()) && !"Informação não disponível".equalsIgnoreCase(value.trim())) {
                    safeValue = value.trim();
                } else {
                    if (fieldName != null) {
                        if (fieldName.toLowerCase().contains("profissional") || fieldName.toLowerCase().contains("doctor") || fieldName.toLowerCase().contains("medico")) {
                            safeValue = "Clínica Inovare";
                        } else if (fieldName.toLowerCase().contains("patient") || fieldName.toLowerCase().contains("paciente")) {
                            safeValue = "Paciente";
                        }
                    }
                }
                
                safeValue = br.dev.ctrls.inovareti.modules.appointment.infrastructure.utils.StringSanitizer.sanitize(safeValue);
                parameters.add(Map.of("type", "text", "text", safeValue));
            });

        log.debug("[PARAMS] Template [{}]: {} parâmetro(s) mapeados", templateName, parameters.size());
        return parameters;
    }

    private String resolveDynamicFieldValue(AppointmentTemplateData data, String fieldName) {
        if (data == null || fieldName == null || fieldName.isBlank()) return null;

        // Mapa explícito: nome do campo no banco → extrator do record.
        // Aceita tanto snake_case quanto camelCase para resiliência.
        String key = fieldName.trim().toLowerCase();
        return switch (key) {
            // Paciente
            case "patientname", "patient_name", "nome_paciente", "paciente"   -> data.patientName();
            case "patientphone", "patient_phone", "telefone_paciente"          -> data.patientPhone();
            case "patientid", "patient_id"                                     -> data.patientId();

            // Médico — usa o doctorName JA resolvido no SendAppointmentTemplateUseCase
            case "doctorname", "doctor_name",
                 "profissionalnome", "profissional_nome",
                 "nome_medico", "medico", "professional_name"                  -> data.doctorName();
            case "doctorid", "doctor_id", "profissional_id"                    -> data.doctorId();
            case "specialty", "especialidade"                                  -> data.specialty();

            // Agenda
            case "appointmentdate", "appointment_date", "data_consulta",
                 "data"                                                         -> data.appointmentDate();
            case "appointmentdateshort", "appointment_date_short", "data_curta" -> data.appointmentDateShort();
            case "appointmenttime", "appointment_time", "hora", "hora_consulta" -> data.appointmentTime();
            case "appointmentdatetime", "appointment_date_time", "data_hora"    -> data.appointmentDateTime();
            case "appointmentid", "appointment_id"                             -> data.appointmentId();

            // Unidade
            case "unitname", "unit_name", "unidade", "local"                   -> data.unitName();

            default -> {
                log.warn("[FIELD MAPPING] Campo '{}' não mapeado em AppointmentTemplateData. Revise a tabela appointment_template_mapping.", fieldName);
                yield null;
            }
        };
    }

    public void sendGroupTemplateMessage(String destination, String templateName, java.util.UUID groupId, String patientName) {
        String normalizedDestination = limeClient.normalizeUserIdentity(destination);

        // Trava de Sandbox (mesma que no sendTemplateMessage)
        if (!normalizedDestination.contains("42991617187") && !destination.contains("42991617187")) {
            log.warn("[SANDBOX] Disparo de grupo bloqueado para número real: {}", destination);
            return;
        }

        List<Map<String, String>> parameters = new ArrayList<>();
        parameters.add(Map.of("type", "text", "text", patientName != null ? patientName : "Paciente"));

        Map<String, Object> viewButton = Map.of(
            "type", "button", "sub_type", "quick_reply", "index", 0,
            "parameters", List.of(Map.of("type", "payload", "payload", "ver_agenda_" + groupId.toString()))
        );

        List<Map<String, Object>> components = new ArrayList<>();
        components.add(Map.of("type", "body", "parameters", parameters));
        components.add(viewButton);

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
            "id", java.util.UUID.randomUUID().toString(),
            "to", normalizedDestination,
            "from", "roteadorprincipal57@msging.net",
            "type", "application/json",
            "content", content,
            "metadata", Map.of("groupId", groupId.toString())
        );

        var response = limeClient.executeMessage(payload, BlipLIMEClient.AuthorizationScope.ROUTER);
        Object status = response.getOrDefault("status", "unknown");
        log.info("Template de grupo enviado. destination={}, template={}, status={}, groupId={}", 
            normalizedDestination, templateName, status, groupId);
    }

    private String resolveWabaNamespace() {
        String ns = motorProperties.getBlipWabaNamespace();
        return (ns != null && !ns.isBlank()) ? ns : "";
    }
}