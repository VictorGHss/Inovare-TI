package br.dev.ctrls.inovareti.modules.appointment.application.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;

import br.dev.ctrls.inovareti.modules.appointment.application.dto.AppointmentTemplateData;
import br.dev.ctrls.inovareti.modules.appointment.application.dto.BlipTemplateDto;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentTemplateMapping;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentTemplateMappingRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.client.BlipLIMEClient;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.AppointmentMotorProperties;
import io.micrometer.observation.annotation.Observed;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Observed
public class BlipNotificationService {

    private final BlipLIMEClient limeClient;
    private final AppointmentTemplateMappingRepositoryPort templateMappingRepository;
    private final AppointmentMotorProperties motorProperties;
    private final BlipPayloadBuilder blipPayloadBuilder;

    public BlipNotificationService(
            BlipLIMEClient limeClient,
            AppointmentTemplateMappingRepositoryPort templateMappingRepository,
            AppointmentMotorProperties motorProperties,
            BlipPayloadBuilder blipPayloadBuilder) {
        this.limeClient = limeClient;
        this.templateMappingRepository = templateMappingRepository;
        this.motorProperties = motorProperties;
        this.blipPayloadBuilder = blipPayloadBuilder;
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
        sendTemplateMessage(destination, motorProperties.getBlipTemplateConfirmation(), appointmentData);
    }

    public void sendTemplateMessage(String destination, String templateName, AppointmentTemplateData appointmentData) {
        String recipientE164 = BlipPayloadBuilder.formatE164Recipient(destination);

        String doctorId = null;
        if (appointmentData != null) {
            doctorId = appointmentData.doctorId();
        }
        
        if (!isDoctorAllowed(doctorId)) {
            log.warn("[SANDBOX] Disparo bloqueado. Dr ID: {}, destination={}, template={}",
                doctorId != null ? doctorId : "null",
                destination,
                templateName);
            return;
        }

        List<Map<String, String>> parameters = buildDynamicParameters(templateName, appointmentData);
        String appointmentId = appointmentData == null ? "" : Objects.toString(appointmentData.appointmentId(), "");

        log.info("[PARAMS TEMPLATE] destination={}, template={}, params={}", recipientE164, templateName, parameters);

        if (parameters.isEmpty()) {
            log.error("[ABORT] Parâmetros vazios para o template '{}'. Envio cancelado para evitar mensagem sem conteúdo. destination={}",
                templateName, recipientE164);
            return;
        }

        Map<String, String> messageParamValues = new java.util.LinkedHashMap<>();
        List<String> messageParamKeys = new ArrayList<>();

        for (int i = 0; i < parameters.size(); i++) {
            String key = String.valueOf(i + 1);
            String val = parameters.get(i).getOrDefault("text", "");
            messageParamValues.put(key, val);
            messageParamKeys.add(key);
        }

        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        String campaignName = "Confirmacao Consulta - " + (appointmentId.isBlank() ? uniqueSuffix : appointmentId + " - " + uniqueSuffix);

        Map<String, Object> commandPayload = blipPayloadBuilder.buildActiveCampaignCommandPayload(
            campaignName,
            recipientE164,
            templateName,
            messageParamValues,
            messageParamKeys,
            resolveMasterState(),
            resolveStateId(),
            resolveFlowId()
        );

        var response = limeClient.executeCommand(commandPayload, BlipLIMEClient.AuthorizationScope.ROUTER);
        validateBlipResponse(response, templateName, recipientE164);
        log.info("Template enviado via Active Campaign (/campaign/full). destination={}, template={}, status={}", recipientE164, templateName, response != null ? response.get("status") : "success");
    }

    private List<Map<String, String>> buildDynamicParameters(String templateName, AppointmentTemplateData appointmentData) {
        List<AppointmentTemplateMapping> mappings = templateMappingRepository
            .findByTemplateNameIgnoreCaseOrderByPlaceholderIndexAsc(templateName);

        if (mappings.isEmpty()) {
            log.info("[TEMPLATE MAPPING] Nenhum mapeamento no banco para '{}'. Aplicando fallback automático (paciente, médico, horário).", templateName);
            String pName = appointmentData != null ? appointmentData.patientName() : "Paciente";
            String dName = appointmentData != null ? appointmentData.doctorName() : "Clínica Inovare";
            String aTime = appointmentData != null ? appointmentData.appointmentTime() : "horário agendado";

            pName = br.dev.ctrls.inovareti.modules.appointment.infrastructure.utils.StringSanitizer.sanitize(pName != null && !pName.isBlank() ? pName : "Paciente");
            dName = br.dev.ctrls.inovareti.modules.appointment.infrastructure.utils.StringSanitizer.sanitize(dName != null && !dName.isBlank() ? dName : "Clínica Inovare");
            aTime = br.dev.ctrls.inovareti.modules.appointment.infrastructure.utils.StringSanitizer.sanitize(aTime != null && !aTime.isBlank() ? aTime : "horário agendado");

            List<Map<String, String>> fallbackParams = new ArrayList<>();
            fallbackParams.add(Map.of("type", "text", "text", pName));
            fallbackParams.add(Map.of("type", "text", "text", dName));
            fallbackParams.add(Map.of("type", "text", "text", aTime));
            return fallbackParams;
        }

        List<Map<String, String>> parameters = new ArrayList<>();
        mappings.stream()
            .sorted(Comparator.comparing(mapping -> mapping.getPlaceholderIndex()))
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

        // Mapa explícito: nome do campo no banco â†’ extrator do record.
        // Aceita tanto snake_case quanto camelCase para resiliência.
        String key = fieldName.trim().toLowerCase();
        return switch (key) {
            // Paciente
            case "patientname", "patient_name", "nome_paciente", "paciente"   -> data.patientName();
            case "patientphone", "patient_phone", "telefone_paciente"          -> data.patientPhone();
            case "patientid", "patient_id"                                     -> data.patientId();

            // Médico â€” usa o doctorName JA resolvido no SendAppointmentTemplateUseCase
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
        String recipientE164 = BlipPayloadBuilder.formatE164Recipient(destination);

        String safePatientName = (patientName != null && !patientName.isBlank() && !"null".equalsIgnoreCase(patientName.trim()))
                ? patientName.trim()
                : "Paciente";

        Map<String, Object> commandPayload = blipPayloadBuilder.buildGroupTemplatePayload(
                recipientE164, templateName, resolveWabaNamespace(), groupId, safePatientName,
                resolveMasterState(), resolveStateId(), resolveFlowId()
        );

        log.info("[MENSAGERIA-GRUPO] Transmitindo template de grupo via Active Campaign (/campaign/full) para o telefone={} com o groupId={}", recipientE164, groupId);
        try {
            var response = limeClient.executeCommand(commandPayload, BlipLIMEClient.AuthorizationScope.ROUTER);
            validateBlipResponse(response, templateName, recipientE164);
            log.info("[MENSAGERIA-GRUPO] Template de grupo disparado com sucesso via Active Campaign para o telefone={}", recipientE164);
        } catch (Exception e) {
            log.error("[ERRO-CRITICO-GRUPO-TRANSMISSAO] Erro ao transmitir template de grupo para o telefone={} com o groupId={}", recipientE164, groupId, e);
            throw e;
        }
    }

    public void sendSimpleTemplateMessage(String destination, String templateName, AppointmentTemplateData appointmentData) {
        String recipientE164 = BlipPayloadBuilder.formatE164Recipient(destination);

        String doctorId = null;
        if (appointmentData != null) {
            doctorId = appointmentData.doctorId();
        }
        
        if (!isDoctorAllowed(doctorId)) {
            log.warn("[SANDBOX] Disparo bloqueado. Dr ID: {}, destination={}, template={}",
                doctorId != null ? doctorId : "null",
                destination,
                templateName);
            return;
        }

        List<Map<String, String>> parameters = buildDynamicParameters(templateName, appointmentData);
        String appointmentId = appointmentData == null ? "" : Objects.toString(appointmentData.appointmentId(), "");

        Map<String, String> messageParamValues = new java.util.LinkedHashMap<>();
        List<String> messageParamKeys = new ArrayList<>();

        for (int i = 0; i < parameters.size(); i++) {
            String key = String.valueOf(i + 1);
            String val = parameters.get(i).getOrDefault("text", "");
            messageParamValues.put(key, val);
            messageParamKeys.add(key);
        }

        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        String campaignName = "Notificacao Consulta - " + (appointmentId.isBlank() ? uniqueSuffix : appointmentId + " - " + uniqueSuffix);

        Map<String, Object> commandPayload = blipPayloadBuilder.buildActiveCampaignCommandPayload(
            campaignName,
            recipientE164,
            templateName,
            messageParamValues,
            messageParamKeys,
            resolveMasterState(),
            resolveStateId(),
            resolveFlowId()
        );

        var response = limeClient.executeCommand(commandPayload, BlipLIMEClient.AuthorizationScope.ROUTER);
        validateBlipResponse(response, templateName, recipientE164);
        log.info("Template simples enviado via Active Campaign (/campaign/full). destination={}, template={}, status={}", recipientE164, templateName, response != null ? response.get("status") : "success");
    }

    private void validateBlipResponse(Map<String, Object> response, String templateName, String recipient) {
        if (response == null || response.isEmpty()) {
            log.error("[LIME-FAILURE] Resposta nula ou vazia do Blip para o template '{}' (destinatário={}).", templateName, recipient);
            throw new br.dev.ctrls.inovareti.modules.appointment.domain.exception.BlipNotificationException(
                "Resposta nula ou vazia do servidor Blip ao enviar template " + templateName
            );
        }

        String status = String.valueOf(response.getOrDefault("status", "unknown"));

        if ("failure".equalsIgnoreCase(status) || "error".equalsIgnoreCase(status) || "offline-queued".equalsIgnoreCase(status) || "timeout".equalsIgnoreCase(status)) {
            Object reasonObj = response.get("reason");
            String reasonStr = reasonObj != null ? reasonObj.toString() : "desconhecida";
            log.error("[LIME-FAILURE] Disparo de template '{}' rejeitado pelo Blip (destinatário={}). Status: {}, Motivo: {}",
                templateName, recipient, status, reasonStr);
            throw new br.dev.ctrls.inovareti.modules.appointment.domain.exception.BlipNotificationException(
                "Envio de template '" + templateName + "' falhou no Blip. Status: " + status + ", Motivo: " + reasonStr
            );
        }

        if (response.containsKey("reason")) {
            Object reasonObj = response.get("reason");
            if (reasonObj instanceof Map<?, ?> reasonMap) {
                Object codeObj = reasonMap.get("code");
                Object descObj = reasonMap.get("description");
                if (codeObj != null) {
                    log.error("[LIME-FAILURE] Comando LIME retornou código de erro {} ({}) para o template '{}' (destinatário={}).",
                        codeObj, descObj, templateName, recipient);
                    throw new br.dev.ctrls.inovareti.modules.appointment.domain.exception.BlipNotificationException(
                        "Falha na API do Blip ao enviar template '" + templateName + "'. Código de Erro: " + codeObj + " - " + descObj
                    );
                }
            }
        }
    }

    private String resolveMasterState() {
        return "fluxov1@msging.net";
    }

    private String resolveStateId() {
        if (motorProperties != null && motorProperties.getState() != null) {
            String stateId = motorProperties.getState().getBlipLandingConfirmacaoItsmStateId();
            if (stateId != null && !stateId.isBlank() && !"null".equalsIgnoreCase(stateId.trim())) {
                return stateId.trim();
            }
            String landingBlock = motorProperties.getState().getBlipLandingBlockId();
            if (landingBlock != null && !landingBlock.isBlank() && !"null".equalsIgnoreCase(landingBlock.trim())) {
                return landingBlock.trim();
            }
        }
        return null;
    }

    private String resolveFlowId() {
        if (motorProperties != null && motorProperties.getState() != null) {
            String flowId = motorProperties.getState().getBlipFluxov1FlowId();
            if (flowId != null && !flowId.isBlank() && !"null".equalsIgnoreCase(flowId.trim())) {
                return flowId.trim();
            }
            String itsmFlowId = motorProperties.getState().getBlipItsmFlowId();
            if (itsmFlowId != null && !itsmFlowId.isBlank() && !"null".equalsIgnoreCase(itsmFlowId.trim())) {
                return itsmFlowId.trim();
            }
        }
        return null;
    }

    /**
     * Envia uma mensagem de texto simples (text/plain) diretamente para o WhatsApp do destinatário
     * via protocolo LIME. Disparo ativo â€” não depende de transição de bloco no Builder.
     *
     * @param destination identidade do destinatário (ex: "5511999999999@wa.gw.msging.net")
     * @param text        corpo da mensagem a ser enviada
     */
    public void sendPlainTextMessage(String destination, String text) {
        if (destination == null || destination.isBlank() || text == null || text.isBlank()) {
            log.warn("[PLAIN-TEXT] Destino ou texto inválido. Envio cancelado. destination={}", destination);
            return;
        }
        String normalizedDestination = ensureWabaIdentity(destination);
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("id", UUID.randomUUID().toString());
        payload.put("to", normalizedDestination);
        payload.put("from", "roteadorprincipal57@msging.net");
        payload.put("type", "text/plain");
        payload.put("content", text);
        try {
            var response = limeClient.executeMessage(payload, BlipLIMEClient.AuthorizationScope.ROUTER);
            Object status = response != null ? response.getOrDefault("status", "unknown") : "unknown";
            log.info("[PLAIN-TEXT] Mensagem de texto enviada ativamente. destination={}, status={}", normalizedDestination, status);
        } catch (RuntimeException ex) {
            log.error("[PLAIN-TEXT] Falha ao enviar mensagem de texto para {}. Erro: {}", normalizedDestination, ex.getMessage(), ex);
        }
    }

    /**
     * Envia uma mensagem interativa do tipo select (Quick Reply) contendo a lista de agendamentos
     * e os botões rápidos de confirmação e alteração de grupo.
     */
    public void sendGroupScheduleMessage(String destination, String text, java.util.UUID groupId) {
        if (destination == null || destination.isBlank() || text == null || text.isBlank() || groupId == null) {
            log.warn("[SELECT-MESSAGE] Parâmetros inválidos. destination={}, text={}, groupId={}", destination, text, groupId);
            return;
        }
        String normalizedDestination = ensureWabaIdentity(destination);

        Map<String, Object> optionConfirm = Map.of(
            "text", "CONFIRMAR TUDO",
            "previewText", "CONFIRMAR TUDO",
            "value", "confirm_group_" + groupId.toString(),
            "type", "text/plain",
            "index", 0
        );

        Map<String, Object> optionAlter = Map.of(
            "text", "PRECISO ALTERAR",
            "previewText", "PRECISO ALTERAR",
            "value", "alter_group_" + groupId.toString(),
            "type", "text/plain",
            "index", 1
        );

        String fullMessageText = "PRÓXIMOS ATENDIMENTOS:\n" + text + "\n\nPor favor, confirme se você comparecerá aos horários listados acima.";

        Map<String, Object> content = Map.of(
            "text", fullMessageText,
            "scope", "immediate",
            "options", List.of(optionConfirm, optionAlter)
        );

        Map<String, Object> payload = Map.of(
            "id", java.util.UUID.randomUUID().toString(),
            "to", normalizedDestination,
            "from", "roteadorprincipal57@msging.net",
            "type", "application/vnd.lime.select+json",
            "content", content
        );

        try {
            var response = limeClient.executeMessage(payload, BlipLIMEClient.AuthorizationScope.ROUTER);
            Object status = response != null ? response.getOrDefault("status", "unknown") : "unknown";
            log.info("[SELECT-MESSAGE] Mensagem de grupo interativa (select) enviada. destination={}, status={}", normalizedDestination, status);
        } catch (RuntimeException ex) {
            log.error("[SELECT-MESSAGE] Falha ao enviar select de grupo para {}. Erro: {}", normalizedDestination, ex.getMessage(), ex);
        }
    }

    private String resolveWabaNamespace() {
        String ns = motorProperties.getBlipWabaNamespace();
        return (ns != null && !ns.isBlank()) ? ns : "";
    }

    private String ensureWabaIdentity(String destination) {
        if (destination == null || destination.isBlank()) {
            return "unknown@wa.gw.msging.net";
        }
        String cleaned = destination.trim();
        if (cleaned.contains("@")) {
            int idx = cleaned.indexOf('@');
            String local = cleaned.substring(0, idx).trim();
            String domain = cleaned.substring(idx + 1).trim();
            if (local.matches("^\\+?\\d+$")) {
                local = local.replaceAll("\\D", "");
            }
            return local + "@" + domain;
        }
        String digits = cleaned.replaceAll("\\D", "");
        return digits + "@wa.gw.msging.net";
    }

    private boolean isDoctorAllowed(String doctorId) {
        String docId = doctorId != null ? doctorId.trim() : "";
        if (motorProperties.getTestDoctorIds().contains(docId)) {
            return true;
        }
        if (motorProperties.getActiveDoctorIds().contains(docId)) {
            return true;
        }
        return false;
    }
}

