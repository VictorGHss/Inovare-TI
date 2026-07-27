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
     * Constrói o comando LIME para o endpoint Active Campaign Growth API (/campaign/full).
     */
    public Map<String, Object> buildActiveCampaignCommandPayload(
            String campaignName,
            String recipientPhone,
            String templateName,
            Map<String, String> messageParamValues,
            List<String> messageParamKeys
    ) {
        String recipientE164 = formatE164Recipient(recipientPhone);

        Map<String, Object> campaign = Map.of(
            "name", campaignName != null && !campaignName.isBlank() ? campaignName : "Notificacao - " + UUID.randomUUID(),
            "campaignType", "Individual",
            "channelType", "WhatsApp",
            "sourceApplication", "Inovare-ITSM"
        );

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

    /**
     * Constrói o mapa de dados para envio do template de grupo (ex: aviso_agendamento_grupo)
     * utilizando a Active Campaign Growth API (/campaign/full).
     */
    public Map<String, Object> buildGroupTemplatePayload(String toPhone, String templateName, String namespace, UUID groupId, String patientName) {
        String safePatientName = (patientName != null && !patientName.isBlank() && !"null".equalsIgnoreCase(patientName.trim()))
                ? patientName.trim()
                : "Paciente";

        String campaignName = "Aviso Grupo - " + (groupId != null ? groupId.toString() : UUID.randomUUID().toString());
        Map<String, String> paramValues = Map.of("1", safePatientName);
        List<String> paramKeys = List.of("1");

        return buildActiveCampaignCommandPayload(campaignName, toPhone, templateName, paramValues, paramKeys);
    }
}