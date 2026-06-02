package br.dev.ctrls.inovareti.modules.finance.infrastructure.adapter.input;

import io.micrometer.observation.annotation.Observed;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.Mockito.when;

import br.dev.ctrls.inovareti.modules.finance.application.service.ContaAzulTokenService;
import br.dev.ctrls.inovareti.modules.finance.application.service.ContaAzulAutomationService;
import br.dev.ctrls.inovareti.modules.finance.infrastructure.adapter.output.ContaAzulClient;
import br.dev.ctrls.inovareti.modules.finance.domain.model.ContaAzulOAuthToken;

class ContaAzulControllerTest {

    private MockMvc mvc;

    private ContaAzulTokenService tokenService;

    @Test
    void forceRefreshReturnsOk() throws Exception {
        // Inicializa mocks localmente para evitar mÃ©todo de setup global
        this.tokenService = Mockito.mock(ContaAzulTokenService.class);
        var client = Mockito.mock(ContaAzulClient.class);
        var automation = Mockito.mock(ContaAzulAutomationService.class);
        var properties = Mockito.mock(br.dev.ctrls.inovareti.modules.finance.infrastructure.config.ContaAzulProperties.class);
        var frontendProperties = Mockito.mock(br.dev.ctrls.inovareti.config.FrontendProperties.class);
        var controller = new ContaAzulController(this.tokenService, client, automation, properties, frontendProperties);
        this.mvc = MockMvcBuilders.standaloneSetup(controller).build();

        ContaAzulOAuthToken token = new ContaAzulOAuthToken();
        token.setId(UUID.randomUUID());
        token.setAccessToken("access-xyz");
        token.setRefreshToken("refresh-xyz");
        token.setExpiresAt(LocalDateTime.now().plusMinutes(60));
        token.setRefreshedAt(LocalDateTime.now());

        when(tokenService.forceRefreshAndReloadFromDatabase()).thenReturn(token);

        // as 3 primeiras chamadas devem retornar OK
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/financeiro/contaazul/force-refresh")
                .with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autorizado").value(true));
        }

        // a quarta chamada imediata deve ser throttled (429)
        mvc.perform(post("/financeiro/contaazul/force-refresh")
            .with(user("admin").roles("ADMIN"))
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isTooManyRequests());
    }

    // MÃ©todo de setup removido; os testes inicializam mocks localmente.
}


