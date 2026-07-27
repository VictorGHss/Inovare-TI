package br.dev.ctrls.inovareti.modules.appointment.application.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class BlipPayloadBuilder {

    /**
     * Formata um telefone de destinatário no formato E.164 estrito com o sinal '+' (ex: "+5542999999999").
     */
    public static String formatE164Recipient(String rawPhone) {
        if (rawPhone == null || rawPhone.isBlank()) return "+5500000000000";
        String phone = rawPhone.trim();
        if (phone.contains("@")) {
            phone = phone.substring(0, phone.indexOf('@')).trim();
        }
        String digitsOnly = phone.replaceAll("\\D", "");
        if (digitsOnly.isBlank()) return "+5500000000000";
        return "+" + digitsOnly;
    }

    /**
     * Constrói o comando LIME para o endpoint Active Campaign Growth API (/campaign/full)
     * incluindo obrigatoriamente masterState e stateId para roteamento de respostas pelo bot Roteador,
     * e opcionalmente flowId.
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

        String safeMasterState = (masterState != null && !masterState.isBlank()) ? masterState.trim() : "fluxov1@msging.net";
        String safeStateId = (stateId != null && !stateId.isBlank()) ? stateId.trim() : "a0776d9c-6486-42f3-8a4f-2706f0185908";

        Map<String, Object> campaign = new java.util.LinkedHashMap<>();
        campaign.put("name", campaignName != null && !campaignName.isBlank() ? campaignName : "Notificacao - " + UUID.randomUUID());
        campaign.put("campaignType", "Individual");
        campaign.put("channelType", "WhatsApp");
        campaign.put("sourceApplication", "Inovare-ITSM");
        campaign.put("masterState", safeMasterState);
        campaign.put("stateId", safeStateId);
        if (flowId != null && !flowId.isBlank()) {
            campaign.put("flowId", flowId.trim());
        }

        Map<String, Object> audience = Map.of(
            "recipient", recipientE164,
            "messageParams", messageParamValues != null ? messageParamValues : Map.of()
        );

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
                "fluxov1@msging.net", "a0776d9c-6486-42f3-8a4f-2706f0185908", null
        );
    }

    /**
     * Constrói o mapa de dados para envio do template de grupo (ex: aviso_agendamento_grupo)
     * utilizando a Active Campaign Growth API (/campaign/full) com masterState, stateId e flowId.
     */
    public Map<String, Object> buildGroupTemplatePayload(String toPhone, String templateName, String namespace, UUID groupId, String patientName, String masterState, String stateId, String flowId) {
        String safePatientName = (patientName != null && !patientName.isBlank() && !"null".equalsIgnoreCase(patientName.trim()))
                ? patientName.trim()
                : "Paciente";

        String campaignName = "Aviso Grupo - " + (groupId != null ? groupId.toString() : UUID.randomUUID().toString());
        Map<String, String> paramValues = Map.of("1", safePatientName);
        List<String> paramKeys = List.of("1");

        return buildActiveCampaignCommandPayload(campaignName, toPhone, templateName, paramValues, paramKeys, masterState, stateId, flowId);
    }

    public Map<String, Object> buildGroupTemplatePayload(String toPhone, String templateName, String namespace, UUID groupId, String patientName, String masterState, String stateId) {
        return buildGroupTemplatePayload(toPhone, templateName, namespace, groupId, patientName, masterState, stateId, null);
    }

    public Map<String, Object> buildGroupTemplatePayload(String toPhone, String templateName, String namespace, UUID groupId, String patientName) {
        return buildGroupTemplatePayload(toPhone, templateName, namespace, groupId, patientName, "fluxov1@msging.net", "a0776d9c-6486-42f3-8a4f-2706f0185908", null);
    }
}