package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Testes dos endpoints públicos do controller ContaAzul.
 * Cada teste inicializa localmente os mocks e o controller para evitar
 * métodos de setup globais não utilizados sinalizados pela IDE.
 */
class ContaAzulPublicEndpointsTest {

    private MockMvc mvc;
    private ContaAzulTokenService tokenService;

    @Test
    void authorizeRedirectsToContaAzul() throws Exception {
        // Inicializa mocks e controller localmente para este teste
        this.tokenService = Mockito.mock(ContaAzulTokenService.class);
        var client = Mockito.mock(ContaAzulClient.class);
        var automation = Mockito.mock(ContaAzulAutomationService.class);
        var controller = new ContaAzulController(this.tokenService, client, automation);
        // definir valores @Value via reflection
        try {
            var f1 = controller.getClass().getDeclaredField("contaAzulRedirectUri");
            f1.setAccessible(true);
            f1.set(controller, "http://localhost/api/financeiro/callback");

            var f2 = controller.getClass().getDeclaredField("frontendUrl");
            f2.setAccessible(true);
            f2.set(controller, "http://localhost:5173/");
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }

        this.mvc = MockMvcBuilders.standaloneSetup(controller).build();

        when(tokenService.buildAuthorizationUrl("http://localhost/api/financeiro/callback"))
            .thenReturn("https://contaazul.example/authorize?x=1");

        mvc.perform(get("/financeiro/contaazul/authorize"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("https://contaazul.example/authorize?x=1"));

        verify(tokenService).buildAuthorizationUrl("http://localhost/api/financeiro/callback");
    }

    @Test
    void callbackExchangesCodeAndRedirectsToFrontend() throws Exception {
        // Inicializa mocks e controller localmente para este teste
        this.tokenService = Mockito.mock(ContaAzulTokenService.class);
        var client = Mockito.mock(ContaAzulClient.class);
        var automation = Mockito.mock(ContaAzulAutomationService.class);
        var controller = new ContaAzulController(this.tokenService, client, automation);
        // definir valores @Value via reflection
        try {
            var f1 = controller.getClass().getDeclaredField("contaAzulRedirectUri");
            f1.setAccessible(true);
            f1.set(controller, "http://localhost/api/financeiro/callback");

            var f2 = controller.getClass().getDeclaredField("frontendUrl");
            f2.setAccessible(true);
            f2.set(controller, "http://localhost:5173/");
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }

        this.mvc = MockMvcBuilders.standaloneSetup(controller).build();

        mvc.perform(get("/financeiro/contaazul/callback").param("code", "abc"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("http://localhost:5173/financeiro?success=true"));

        verify(tokenService).exchangeAuthorizationCode("abc", "http://localhost/api/financeiro/callback");
    }
}
