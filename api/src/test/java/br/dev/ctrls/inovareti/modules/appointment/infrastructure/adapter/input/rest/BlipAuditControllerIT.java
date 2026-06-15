package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.input.rest;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import br.dev.ctrls.inovareti.modules.appointment.domain.model.BlipDeliveryFailure;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.BlipDeliveryFailureRepositoryPort;

/**
 * Testes de integração para o controlador REST BlipAuditController.
 * Valida o comportamento de paginação, filtros, ordenação e regras de segurança (PreAuthorize).
 */
@SpringBootTest(properties = {
        "blip.webhook.token=test-token",
        "spring.scheduling.enabled=false"
})
public class BlipAuditControllerIT {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private BlipDeliveryFailureRepositoryPort failureRepository;

    @BeforeEach
    public void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    /**
     * Valida que requisições não autenticadas ao endpoint retornem 403 Forbidden
     * devido ao comportamento padrão do Spring Security.
     */
    @Test
    public void deveRetornar403QuandoNaoAutenticado() throws Exception {
        mockMvc.perform(get("/v1/audit/blip-failures")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    /**
     * Valida que usuários autenticados mas sem perfil administrativo ou técnico (ex: USER)
     * retornem 403 Forbidden.
     */
    @Test
    public void deveRetornar403QuandoNaoAutorizado() throws Exception {
        mockMvc.perform(get("/v1/audit/blip-failures")
                        .with(user("regular-user").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    /**
     * Valida a busca de falhas de entrega de forma paginada, filtrada e com ordenação
     * decrescente por data de criação (createdAt DESC).
     */
    @Test
    public void deveRetornarSucessoPaginadoEFiltradoParaAdmin() throws Exception {
        String apptId1 = "appt-" + UUID.randomUUID().toString().substring(0, 8);
        String apptId2 = "appt-" + UUID.randomUUID().toString().substring(0, 8);

        // Falha 1: erro de parâmetro inválido do template (100) -> categoria PARAMETRO_INVALIDO_TEMPLATE
        BlipDeliveryFailure failure1 = BlipDeliveryFailure.builder()
                .messageId(UUID.randomUUID().toString())
                .appointmentId(apptId1)
                .errorCode(100)
                .errorMessage("Parâmetro inválido de template no Blip")
                .traceId("trace-1")
                .createdAt(LocalDateTime.now().minusMinutes(10))
                .build();

        // Falha 2: erro de conta business bloqueada (131031) -> categoria CONTA_BUSINESS_BLOQUEADA
        BlipDeliveryFailure failure2 = BlipDeliveryFailure.builder()
                .messageId(UUID.randomUUID().toString())
                .appointmentId(apptId2)
                .errorCode(131031)
                .errorMessage("WABA bloqueada temporariamente")
                .traceId("trace-2")
                .createdAt(LocalDateTime.now().minusMinutes(5)) // Mais recente
                .build();

        failureRepository.save(failure1);
        failureRepository.save(failure2);

        // 1. Busca todas as falhas sem filtro: deve vir a mais recente (failure2) primeiro por causa do createdAt DESC
        mockMvc.perform(get("/v1/audit/blip-failures")
                        .with(user("admin-user").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].appointmentId").value(apptId2))
                .andExpect(jsonPath("$.content[1].appointmentId").value(apptId1));

        // 2. Busca filtrando por appointmentId
        mockMvc.perform(get("/v1/audit/blip-failures")
                        .param("appointmentId", apptId1)
                        .with(user("tech-user").roles("TECHNICIAN"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].appointmentId").value(apptId1));

        // 3. Busca filtrando por categoria
        mockMvc.perform(get("/v1/audit/blip-failures")
                        .param("category", "CONTA_BUSINESS_BLOQUEADA")
                        .with(user("admin-user").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].appointmentId").value(apptId2))
                .andExpect(jsonPath("$.content[0].errorCode").value(131031));
    }
}
