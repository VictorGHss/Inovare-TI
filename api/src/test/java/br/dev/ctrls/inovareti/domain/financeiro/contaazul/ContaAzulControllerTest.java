package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.Mockito.when;

class ContaAzulControllerTest {

    private MockMvc mvc;

    private ContaAzulTokenService tokenService;

    @Test
    void forceRefreshReturnsOk() throws Exception {
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

    @BeforeEach
    @SuppressWarnings("unused")
    void setup() {
        this.tokenService = Mockito.mock(ContaAzulTokenService.class);
        var client = Mockito.mock(ContaAzulClient.class);
        var automation = Mockito.mock(ContaAzulAutomationService.class);
        var controller = new ContaAzulController(this.tokenService, client, automation);
        this.mvc = MockMvcBuilders.standaloneSetup(controller)
            .build();
    }
}
