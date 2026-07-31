package br.dev.ctrls.inovareti.modules.appointment.application.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class BlipPayloadBuilder {

    /**
     * Formata um telefone de destinatário no formato E.164 estrito com o sinal '+' (ex: "+5542999999999").
     * Exige um destino válido com no mínimo 14 caracteres (+55 + 11 dígitos).
     */
    public static String formatE164Recipient(String rawPhone) {
        if (rawPhone == null || rawPhone.isBlank()) return null;
        String e164 = br.dev.ctrls.inovareti.modules.appointment.infrastructure.utils.StringSanitizer.formatE164(rawPhone);
        if (e164 != null && e164.length() >= 14) {
            return e164;
        }
        return null;
    }

    /**
     * Constrói o comando LIME para o endpoint Active Campaign Growth API (/campaign/full).
     * Inclui masterState, e inclui stateId/flowId no nó 'campaign' APENAS se forem strings válidas e não-vazias.
     */
    public Map<String, Object> buildActiveCampaignCommandPayload(
            String campaignName,
            String recipientPhone,
            String templateName,
            Map<String, String> messageParamValues,
            List<String> messageParamKeys,
            String masterState,
            String stateId,
            String flowId
    ) {
        String recipientE164 = formatE164Recipient(recipientPhone);

        // Se o template for estático de grupo (ex: aviso_agendamento_grupo, aviso_confirmacao_pendente_grupo), força messageParams zerados (mapa/lista vazios)
        String tName = templateName != null ? templateName.trim().toLowerCase() : "";
        if (tName.equals("aviso_agendamento_grupo") || tName.equals("aviso_confirmacao_pendente_grupo") || tName.endsWith("_grupo")) {
            messageParamValues = Map.of();
            messageParamKeys = List.of();
        }

        Map<String, Object> campaign = new java.util.LinkedHashMap<>();
        campaign.put("name", campaignName != null && !campaignName.isBlank() ? campaignName : "Notificacao - " + UUID.randomUUID());
        campaign.put("campaignType", "Individual");
        campaign.put("channelType", "WhatsApp");
        campaign.put("sourceApplication", "Inovare-ITSM");

        if (masterState != null && !masterState.isBlank()) {
            campaign.put("masterState", masterState.trim());
        }

        if (stateId != null && !stateId.isBlank()) {
            campaign.put("stateId", stateId.trim());
        }

        if (flowId != null && !flowId.isBlank()) {
            campaign.put("flowId", flowId.trim());
        }

        String rawPhone = recipientPhone != null ? recipientPhone.replaceAll("\\D", "") : "";
        String plainPhone = (rawPhone.startsWith("55") && rawPhone.length() > 11) ? rawPhone.substring(2) : rawPhone;

        Map<String, String> contextVars = new java.util.HashMap<>();
        if (!plainPhone.isBlank()) {
            contextVars.put("phoneNumber", plainPhone);
            contextVars.put("contact.phoneNumber", plainPhone);
            contextVars.put("telefone", plainPhone);
        }

        Map<String, Object> audience = new java.util.LinkedHashMap<>();
        audience.put("recipient", recipientE164);
        audience.put("messageParams", messageParamValues != null ? messageParamValues : Map.of());
        if (!contextVars.isEmpty()) {
            audience.put("contextVariables", contextVars);
        }

        Map<String, Object> message = Map.of(
            "messageTemplate", templateName,
            "messageTemplateLanguage", "pt_BR",
            "messageParams", messageParamKeys != null ? messageParamKeys : List.of(),
            "channelType", "WhatsApp"
        );

        Map<String, Object> resource = Map.of(
            "campaign", campaign,
            "audience", audience,
            "message", message
        );

        return Map.of(
            "id", UUID.randomUUID().toString(),
            "to", "postmaster@activecampaign.msging.net",
            "method", "set",
            "uri", "/campaign/full",
            "type", "application/vnd.iris.activecampaign.full-campaign+json",
            "resource", resource
        );
    }

    public Map<String, Object> buildActiveCampaignCommandPayload(
            String campaignName,
            String recipientPhone,
            String templateName,
            Map<String, String> messageParamValues,
            List<String> messageParamKeys,
            String masterState,
            String stateId
    ) {
        return buildActiveCampaignCommandPayload(
                campaignName, recipientPhone, templateName, messageParamValues, messageParamKeys,
                masterState, stateId, null
        );
    }

    public Map<String, Object> buildActiveCampaignCommandPayload(
            String campaignName,
            String recipientPhone,
            String templateName,
            Map<String, String> messageParamValues,
            List<String> messageParamKeys
    ) {
        return buildActiveCampaignCommandPayload(
                campaignName, recipientPhone, templateName, messageParamValues, messageParamKeys,
                "fluxov1@msging.net", null, null
        );
    }

    /**
     * Constrói o mapa de dados para envio do template de grupo (ex: aviso_agendamento_grupo)
     * utilizando a Active Campaign Growth API (/campaign/full) omitindo stateId/flowId se não informados.
     */
    public Map<String, Object> buildGroupTemplatePayload(String toPhone, String templateName, String namespace, UUID groupId, String patientName, String masterState, String stateId, String flowId) {
        String safePatientName = (patientName != null && !patientName.isBlank() && !"null".equalsIgnoreCase(patientName.trim()))
                ? patientName.trim()
                : "Paciente";

        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        String campaignName = "Aviso Grupo - " + (groupId != null ? groupId.toString() : uniqueSuffix) + " - " + uniqueSuffix;
        
        Map<String, String> paramValues;
        List<String> paramKeys;

        String tNameGroup = templateName != null ? templateName.trim().toLowerCase() : "";
        if (tNameGroup.equals("aviso_agendamento_grupo") || tNameGroup.equals("aviso_confirmacao_pendente_grupo") || tNameGroup.endsWith("_grupo")) {
            paramValues = Map.of();
            paramKeys = List.of();
        } else {
            paramValues = Map.of("1", safePatientName);
            paramKeys = List.of("1");
        }

        return buildActiveCampaignCommandPayload(campaignName, toPhone, templateName, paramValues, paramKeys, masterState, stateId, flowId);
    }

    public Map<String, Object> buildGroupTemplatePayload(String toPhone, String templateName, String namespace, UUID groupId, String patientName, String masterState, String stateId) {
        return buildGroupTemplatePayload(toPhone, templateName, namespace, groupId, patientName, masterState, stateId, null);
    }

    public Map<String, Object> buildGroupTemplatePayload(String toPhone, String templateName, String namespace, UUID groupId, String patientName) {
        return buildGroupTemplatePayload(toPhone, templateName, namespace, groupId, patientName, null, null, null);
    }
}