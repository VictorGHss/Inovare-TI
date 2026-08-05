package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.dev.ctrls.inovareti.modules.appointment.application.service.BlipPayloadBuilder;

class BlipActiveCampaignMigrationTest {

    private final BlipPayloadBuilder payloadBuilder = new BlipPayloadBuilder();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Deveria formatar telefone no padrão E.164 com o prefixo '+' e remover o sufixo @wa.gw.msging.net")
    void shouldFormatE164RecipientCorrectly() {
        assertEquals("+5542999999999", BlipPayloadBuilder.formatE164Recipient("5542999999999@wa.gw.msging.net"));
        assertEquals("+5542999999999", BlipPayloadBuilder.formatE164Recipient("5542999999999"));
        assertEquals("+5542999999999", BlipPayloadBuilder.formatE164Recipient("+5542999999999"));
    }

    @Test
    @DisplayName("Deveria omitir stateId e flowId do nó campaign quando nulos ou em branco para evitar Code 61 no Blip")
    void shouldOmitStateIdAndFlowIdWhenBlank() {
        UUID groupId = UUID.randomUUID();
        Map<String, Object> payload = payloadBuilder.buildGroupTemplatePayload(
                "5542999999999@wa.gw.msging.net",
                "aviso_agendamento_grupo",
                "waba_namespace",
                groupId,
                "João da Silva"
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> resource = (Map<String, Object>) payload.get("resource");
        @SuppressWarnings("unchecked")
        Map<String, Object> campaign = (Map<String, Object>) resource.get("campaign");

        assertFalse(campaign.containsKey("masterState"), "masterState deveria ser omitido se nulo/em branco");
        assertFalse(campaign.containsKey("stateId"), "stateId deveria ser omitido se nulo/em branco para evitar erro Code 61 no Blip");
        assertFalse(campaign.containsKey("flowId"), "flowId deveria ser omitido se nulo/em branco");
    }

    @Test
    @DisplayName("Deveria incluir stateId e flowId no nó campaign apenas quando válidos")
    void shouldIncludeStateIdAndFlowIdWhenValid() {
        Map<String, Object> payload = payloadBuilder.buildActiveCampaignCommandPayload(
                "Confirmacao - 123",
                "+5542999999999",
                "confirmacao_consulta_v6_itsm",
                Map.of("1", "Paciente"),
                List.of("1"),
                "fluxov1@msging.net",
                "a0776d9c-6486-42f3-8a4f-2706f0185908",
                "test-flow-id"
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> resource = (Map<String, Object>) payload.get("resource");
        @SuppressWarnings("unchecked")
        Map<String, Object> campaign = (Map<String, Object>) resource.get("campaign");

        assertEquals("fluxov1@msging.net", campaign.get("masterState"));
        assertEquals("a0776d9c-6486-42f3-8a4f-2706f0185908", campaign.get("stateId"));
        assertEquals("test-flow-id", campaign.get("flowId"));
    }

    @Test
    @DisplayName("Deveria omitir 100% o campo messageParams no JSON para template estático aviso_agendamento_grupo")
    void shouldOmitMessageParamsForStaticGroupTemplate() throws Exception {
        UUID groupId = UUID.fromString("8400b9bc-0000-0000-0000-000000000000");
        Map<String, Object> payload = payloadBuilder.buildGroupTemplatePayload(
                "+5542988113109",
                "aviso_agendamento_grupo",
                "waba_namespace",
                groupId,
                "João da Silva",
                "fluxov1@msging.net",
                "a0776d9c-6486-42f3-8a4f-2706f0185908",
                "fluxov1@msging.net"
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> resource = (Map<String, Object>) payload.get("resource");
        @SuppressWarnings("unchecked")
        Map<String, Object> audience = (Map<String, Object>) resource.get("audience");
        @SuppressWarnings("unchecked")
        Map<String, Object> message = (Map<String, Object>) resource.get("message");
        @SuppressWarnings("unchecked")
        Map<String, Object> campaign = (Map<String, Object>) resource.get("campaign");

        assertFalse(audience.containsKey("messageParams"), "audience não deveria conter a chave messageParams quando nula");
        assertFalse(message.containsKey("messageParams"), "message não deveria conter a chave messageParams quando nula");

        assertEquals("fluxov1@msging.net", campaign.get("masterState"));
        assertEquals("a0776d9c-6486-42f3-8a4f-2706f0185908", campaign.get("stateId"));
        assertEquals("fluxov1@msging.net", campaign.get("flowId"));

        String jsonOutput = objectMapper.writeValueAsString(payload);
        assertFalse(jsonOutput.contains("messageParams"), "O JSON final gerado não pode conter a chave 'messageParams'");
        assertTrue(jsonOutput.contains("\"messageTemplate\":\"aviso_agendamento_grupo\""));
    }

    @Test
    @DisplayName("Deveria incluir messageParams quando o template possui variáveis dinâmicas")
    void shouldIncludeMessageParamsWhenTemplateHasVariables() throws Exception {
        Map<String, Object> payload = payloadBuilder.buildActiveCampaignCommandPayload(
                "Confirmacao - 123",
                "+5542999999999",
                "confirmacao_consulta_v6_itsm",
                Map.of("1", "Paciente"),
                List.of("1")
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> resource = (Map<String, Object>) payload.get("resource");
        @SuppressWarnings("unchecked")
        Map<String, Object> audience = (Map<String, Object>) resource.get("audience");
        @SuppressWarnings("unchecked")
        Map<String, Object> message = (Map<String, Object>) resource.get("message");

        assertTrue(audience.containsKey("messageParams"), "audience deveria conter messageParams quando fornecidos");
        assertTrue(message.containsKey("messageParams"), "message deveria conter messageParams quando fornecidos");

        String jsonOutput = objectMapper.writeValueAsString(payload);
        assertTrue(jsonOutput.contains("messageParams"), "O JSON final gerado deve conter a chave 'messageParams' se houver variáveis");
    }

    @Test
    @DisplayName("Deveria validar conformidade estrita com o padrão Active Campaign Growth (/campaign/full)")
    void shouldValidateFullActiveCampaignGrowthSpecification() {
        Map<String, Object> payload = payloadBuilder.buildActiveCampaignCommandPayload(
                "Campanha Teste",
                "+5542999998888",
                "template_teste_v1",
                Map.of("1", "Carlos", "2", "15/08 às 14:00"),
                List.of("1", "2")
        );

        assertEquals("postmaster@activecampaign.msging.net", payload.get("to"));
        assertEquals("set", payload.get("method"));
        assertEquals("/campaign/full", payload.get("uri"));
        assertEquals("application/vnd.iris.activecampaign.full-campaign+json", payload.get("type"));

        @SuppressWarnings("unchecked")
        Map<String, Object> resource = (Map<String, Object>) payload.get("resource");
        @SuppressWarnings("unchecked")
        Map<String, Object> campaign = (Map<String, Object>) resource.get("campaign");
        @SuppressWarnings("unchecked")
        Map<String, Object> audience = (Map<String, Object>) resource.get("audience");
        @SuppressWarnings("unchecked")
        Map<String, Object> message = (Map<String, Object>) resource.get("message");

        assertEquals("Campanha Teste", campaign.get("name"));
        assertEquals("Individual", campaign.get("campaignType"));
        assertEquals("WhatsApp", campaign.get("channelType"));
        assertEquals("Inovare-ITSM", campaign.get("sourceApplication"));

        assertEquals("+5542999998888", audience.get("recipient"));
        @SuppressWarnings("unchecked")
        Map<String, String> audienceParams = (Map<String, String>) audience.get("messageParams");
        assertEquals("Carlos", audienceParams.get("1"));
        assertEquals("15/08 às 14:00", audienceParams.get("2"));

        assertEquals("template_teste_v1", message.get("messageTemplate"));
        assertEquals("pt_BR", message.get("messageTemplateLanguage"));
        assertEquals("WhatsApp", message.get("channelType"));
        @SuppressWarnings("unchecked")
        List<String> messageParams = (List<String>) message.get("messageParams");
        assertEquals(List.of("1", "2"), messageParams);
    }
}
