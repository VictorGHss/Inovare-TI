package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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

        assertEquals("fluxov1@msging.net", campaign.get("masterState"));
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
}
