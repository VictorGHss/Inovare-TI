package br.dev.ctrls.inovareti.modules.appointment.application.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.dev.ctrls.inovareti.modules.appointment.application.dto.AudienceDto;
import br.dev.ctrls.inovareti.modules.appointment.application.dto.CampaignDto;
import br.dev.ctrls.inovareti.modules.appointment.application.dto.MessageDto;

@Component
public class BlipPayloadBuilder {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
     * Quando o template não possuir variáveis (ex: mapa/lista vazios ou estático), messageParams é atribuído como null
     * e totalmente omitido do JSON final via @JsonInclude(Include.NON_NULL) nos DTOs.
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

        String effectiveFlowId = (flowId != null && !flowId.isBlank())
                ? flowId.trim()
                : (masterState != null && !masterState.isBlank() ? masterState.trim() : null);

        String finalMasterState = (masterState != null && !masterState.isBlank()) ? masterState.trim() : null;
        String finalStateId = (stateId != null && !stateId.isBlank()) ? stateId.trim() : null;

        String finalFlowId = null;
        if (effectiveFlowId != null && !effectiveFlowId.isBlank()) {
            finalFlowId = effectiveFlowId;
        } else if (finalMasterState != null || finalStateId != null) {
            finalFlowId = "fluxov1@msging.net";
        }

        CampaignDto campaignDto = CampaignDto.builder()
                .name(campaignName != null && !campaignName.isBlank() ? campaignName : "Notificacao - " + UUID.randomUUID())
                .campaignType("Individual")
                .channelType("WhatsApp")
                .sourceApplication("Inovare-ITSM")
                .masterState(finalMasterState)
                .stateId(finalStateId)
                .flowId(finalFlowId)
                .build();

        String rawPhone = recipientPhone != null ? recipientPhone.replaceAll("\\D", "") : "";
        String plainPhone = (rawPhone.startsWith("55") && rawPhone.length() > 11) ? rawPhone.substring(2) : rawPhone;

        Map<String, String> contextVars = new java.util.HashMap<>();
        if (!plainPhone.isBlank()) {
            contextVars.put("phoneNumber", plainPhone);
            contextVars.put("contact.phoneNumber", plainPhone);
            contextVars.put("telefone", plainPhone);
        }

        Map<String, String> finalAudienceParams = (messageParamValues != null && !messageParamValues.isEmpty())
                ? messageParamValues
                : null;

        List<String> finalMessageParamKeys = (messageParamKeys != null && !messageParamKeys.isEmpty())
                ? messageParamKeys
                : null;

        AudienceDto audienceDto = AudienceDto.builder()
                .recipient(recipientE164)
                .messageParams(finalAudienceParams)
                .contextVariables(!contextVars.isEmpty() ? contextVars : null)
                .build();

        MessageDto messageDto = MessageDto.builder()
                .messageTemplate(templateName)
                .messageTemplateLanguage("pt_BR")
                .messageParams(finalMessageParamKeys)
                .channelType("WhatsApp")
                .build();

        @SuppressWarnings("unchecked")
        Map<String, Object> campaign = OBJECT_MAPPER.convertValue(campaignDto, Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> audience = OBJECT_MAPPER.convertValue(audienceDto, Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> message = OBJECT_MAPPER.convertValue(messageDto, Map.class);

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
                null, null, null
        );
    }

    /**
     * Constrói o mapa de dados para envio do template de grupo (ex: aviso_agendamento_grupo)
     * utilizando a Active Campaign Growth API (/campaign/full).
     * Templates de grupo (_grupo) ou estáticos enviam messageParams omitidos (null).
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
            paramValues = null;
            paramKeys = null;
        } else {
            paramValues = Map.of("1", safePatientName);
            paramKeys = List.of("1");
        }

        String effectiveFlowId = (flowId != null && !flowId.isBlank()) 
                ? flowId.trim() 
                : (masterState != null && !masterState.isBlank() ? masterState.trim() : null);

        return buildActiveCampaignCommandPayload(campaignName, toPhone, templateName, paramValues, paramKeys, masterState, stateId, effectiveFlowId);
    }

    public Map<String, Object> buildGroupTemplatePayload(String toPhone, String templateName, String namespace, UUID groupId, String patientName, String masterState, String stateId) {
        return buildGroupTemplatePayload(toPhone, templateName, namespace, groupId, patientName, masterState, stateId, null);
    }

    public Map<String, Object> buildGroupTemplatePayload(String toPhone, String templateName, String namespace, UUID groupId, String patientName) {
        return buildGroupTemplatePayload(toPhone, templateName, namespace, groupId, patientName, null, null, null);
    }
}