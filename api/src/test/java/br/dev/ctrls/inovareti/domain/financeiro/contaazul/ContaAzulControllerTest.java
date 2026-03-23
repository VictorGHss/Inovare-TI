package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.Mockito.when;

@SpringBootTest
@AutoConfigureMockMvc
class ContaAzulControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private ContaAzulTokenService tokenService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void forceRefreshReturnsOk() throws Exception {
        ContaAzulOAuthToken token = new ContaAzulOAuthToken();
        token.setId(UUID.randomUUID());
        token.setAccessToken("access-xyz");
        token.setRefreshToken("refresh-xyz");
        token.setExpiresAt(LocalDateTime.now().plusMinutes(60));
        token.setRefreshedAt(LocalDateTime.now());

        when(tokenService.forceRefreshAndReloadFromDatabase()).thenReturn(token);

        mvc.perform(post("/financeiro/contaazul/force-refresh")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorized").value(true));
    }
}
