package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.input.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import br.dev.ctrls.inovareti.modules.appointment.domain.model.BlipDeliveryFailure;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.BlipDeliveryFailureRepositoryPort;

/**
 * Testes de integração para validação de observabilidade e captura de falhas de entrega do Blip.
 */
@SpringBootTest(properties = {
        "blip.webhook.token=test-token",
        "spring.scheduling.enabled=false"
})
public class BlipWebhookControllerIT {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private BlipDeliveryFailureRepositoryPort failureRepository;

    @BeforeEach
    public void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    /**
     * Valida se uma notificação de falha de entrega do Blip (LIME event failed)
     * é capturada com sucesso pelo Controller, retornando 200 OK e salvando os
     * registros correspondentes de erro no banco de dados.
     */
    @Test
    public void deveProcessarFalhaDeEntregaComSucesso() throws Exception {
        // Payload de simulação de falha de entrega (Falta de Saldo na conta Meta)
        String failurePayloadJson = """
                {
                  "id": "e4b52ca7-79b8-47fb-a764-8840c4ad0932",
                  "from": "postmaster@wa.gw.msging.net",
                  "to": "roteadorprincipal57@msging.net",
                  "event": "failed",
                  "reason": {
                    "code": 131042,
                    "description": "Payment/billing issue (out of balance or credit limit reached)"
                  },
                  "metadata": {
                    "appointmentId": "987654"
                  }
                }
                """;

        // Executa a requisição POST simulando a entrega do webhook do Blip
        mockMvc.perform(post("/v1/webhook/blip")
                        .header("X-Inovare-Token", "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(failurePayloadJson))
                .andExpect(status().isOk());

        // Recupera os registros de falha do repositório
        List<BlipDeliveryFailure> failures = failureRepository.findByMessageId("e4b52ca7-79b8-47fb-a764-8840c4ad0932");

        // Asserções para validar se a persistência ocorreu corretamente
        assertFalse(failures.isEmpty(), "Deveria ter registrado a falha de entrega no banco");
        BlipDeliveryFailure failure = failures.get(0);
        assertEquals("e4b52ca7-79b8-47fb-a764-8840c4ad0932", failure.getMessageId());
        assertEquals("987654", failure.getAppointmentId());
        assertEquals(131042, failure.getErrorCode());
        assertEquals("Payment/billing issue (out of balance or credit limit reached)", failure.getErrorMessage());
    }
}
