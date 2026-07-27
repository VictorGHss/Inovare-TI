package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.dev.ctrls.inovareti.modules.appointment.application.service.BlipPayloadBuilder;

class BlipActiveCampaignMigrationTest {

    private final BlipPayloadBuilder payloadBuilder = new BlipPayloadBuilder();

    @Test
    @DisplayName("Deveria formatar telefone no padrão E.164 com o prefixo '+' e remover o sufixo @wa.gw.msging.net")
    void shouldFormatE164RecipientCorrectly() {
        assertEquals("+5542999999999", BlipPayloadBuilder.formatE164Recipient("5542999999999@wa.gw.msging.net"));
        assertEquals("+5542999999999", BlipPayloadBuilder.formatE164Recipient("5542999999999"));
        assertEquals("+5542999999999", BlipPayloadBuilder.formatE164Recipient("+5542999999999"));
    }

    @Test
    @DisplayName("Deveria construir o payload LIME Command no formato Active Campaign /campaign/full")
    void shouldBuildActiveCampaignFullCommandPayload() {
        UUID groupId = UUID.randomUUID();
        Map<String, Object> payload = payloadBuilder.buildGroupTemplatePayload(
                "5542999999999@wa.gw.msging.net",
                "aviso_agendamento_grupo",
                "waba_namespace",
                groupId,
                "João da Silva"
        );

        assertEquals("postmaster@activecampaign.msging.net", payload.get("to"));
        assertEquals("set", payload.get("method"));
        assertEquals("/campaign/full", payload.get("uri"));
        assertEquals("application/vnd.iris.activecampaign.full-campaign+json", payload.get("type"));

        assertNotNull(payload.get("resource"));
        @SuppressWarnings("unchecked")
        Map<String, Object> resource = (Map<String, Object>) payload.get("resource");

        @SuppressWarnings("unchecked")
        Map<String, Object> campaign = (Map<String, Object>) resource.get("campaign");
        assertEquals("Aviso Grupo - " + groupId, campaign.get("name"));
        assertEquals("Individual", campaign.get("campaignType"));
        assertEquals("WhatsApp", campaign.get("channelType"));
        assertEquals("Inovare-ITSM", campaign.get("sourceApplication"));
        assertEquals("fluxov1@msging.net", campaign.get("masterState"));
        assertEquals("a0776d9c-6486-42f3-8a4f-2706f0185908", campaign.get("stateId"));

        @SuppressWarnings("unchecked")
        Map<String, Object> audience = (Map<String, Object>) resource.get("audience");
        assertEquals("+5542999999999", audience.get("recipient"));
        @SuppressWarnings("unchecked")
        Map<String, String> messageParamsMap = (Map<String, String>) audience.get("messageParams");
        assertEquals("João da Silva", messageParamsMap.get("1"));

        @SuppressWarnings("unchecked")
        Map<String, Object> message = (Map<String, Object>) resource.get("message");
        assertEquals("aviso_agendamento_grupo", message.get("messageTemplate"));
        assertEquals("pt_BR", message.get("messageTemplateLanguage"));
        @SuppressWarnings("unchecked")
        List<String> paramKeys = (List<String>) message.get("messageParams");
        assertTrue(paramKeys.contains("1"));
        assertEquals("WhatsApp", message.get("channelType"));
    }
}
