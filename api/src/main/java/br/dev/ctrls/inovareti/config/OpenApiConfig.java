package br.dev.ctrls.inovareti.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Configuração do OpenAPI (Swagger) para documentação interativa da API Inovare-TI.
 * Configura suporte nativo para autenticação Bearer JWT nos endpoints protegidos por RBAC.
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "BearerAuth";

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Inovare-TI API")
                        .version("2.0 - Hexagonal")
                        .description("Documentação interativa das rotas do motor de agendamentos e faturamento financeiro da Inovare-TI."))
                // Adiciona a exigência de segurança global para todos os endpoints no Swagger UI
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        // Configura o esquema do tipo HTTP Bearer (JWT)
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Insira o Token JWT gerado no endpoint de login (/auth/login) para acessar recursos restritos por papel (RBAC).")));
    }
}
