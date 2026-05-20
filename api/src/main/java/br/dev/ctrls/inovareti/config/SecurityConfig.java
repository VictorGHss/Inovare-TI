package br.dev.ctrls.inovareti.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import lombok.RequiredArgsConstructor;

/**
 * Configuração do Spring Security — JWT sem estado (stateless).
 * Apenas os endpoints de autenticação são públicos.
 * Todas as demais rotas exigem um Bearer token válido.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String APPOINTMENT_ADMIN_PATH = "/v1/appointments/admin/**";

    private final SecurityFilter securityFilter;
    private final RawBodyLoggingFilter rawBodyLoggingFilter;
    private final AppCorsProperties corsProperties;

    /**
     * Define a cadeia de filtros de segurança:
     * - CSRF desabilitado globalmente (API stateless e webhooks externos)
     * - CORS habilitado (permite o servidor Vite em localhost:5173)
     * - Gerenciamento de sessão: STATELESS
     * - Rotas pública: POST /api/auth/login e POST /api/auth/reset-initial-password
     * - Demais rotas: autenticadas
     * - Filtro JWT executado antes do UsernamePasswordAuthenticationFilter
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // CSRF desabilitado globalmente (API stateless + webhooks externos).
        // Evita 403 em túneis (ex.: Pinggy) enquanto integrações não enviam token CSRF.
        http
                .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeHttpRequests(authorize -> authorize
                // Preflight OPTIONS liberado para CORS antes de qualquer outra regra
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // Webhooks e endpoints de integração estritamente autorizados publicamente.
                .requestMatchers(HttpMethod.POST, "/api/webhooks/blip", "/api/webhooks/blip/").permitAll()
                .requestMatchers(HttpMethod.POST, "/webhooks/blip", "/webhooks/blip/").permitAll()
                .requestMatchers(HttpMethod.POST, "/v1/webhook/blip", "/v1/webhook/blip/").permitAll()
                .requestMatchers(HttpMethod.POST, "/v1/appointments/blip/webhook", "/v1/appointments/blip/webhook/").permitAll()
                .requestMatchers(HttpMethod.GET, "/financeiro/contaazul/authorize", "/financeiro/contaazul/callback").permitAll()

                // Demais rotas administrativas/autenticação
                .requestMatchers(APPOINTMENT_ADMIN_PATH).hasRole("ADMIN")
                .requestMatchers("/auth/login", "/auth/reset-initial-password").permitAll()
                .requestMatchers("/auth/2fa/**").authenticated()
                
                // Restringir acesso aos endpoints do Actuator apenas para ADMIN
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                // Libera endpoints do Swagger UI e documentação da API
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/ws/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(rawBodyLoggingFilter, SecurityContextHolderFilter.class)
            .addFilterBefore(new ManualTriggerKeyFilter(), UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(securityFilter, ManualTriggerKeyFilter.class);

        return http.build();
    }

    /**
     * CorsFilter bean with highest precedence so CORS preflights are handled
     * before security filters (JWT, etc.).
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public CorsFilter corsFilter() {
        CorsConfiguration config = buildCorsConfiguration();

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }

    /**
     * Configuração de CORS — permite que o frontend (localhost e IPs do servidor) consuma a API.
     * Libera explicitamente requisitiões OPTIONS para preflight.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = buildCorsConfiguration();

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private CorsConfiguration buildCorsConfiguration() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(corsProperties.getAllowedOrigins());
        config.setAllowedOriginPatterns(corsProperties.getAllowedOriginPatterns());
        config.setAllowedMethods(corsProperties.getAllowedMethods());
        config.setAllowedHeaders(corsProperties.getAllowedHeaders());
        config.setExposedHeaders(corsProperties.getExposedHeaders());
        config.setAllowCredentials(corsProperties.isAllowCredentials());
        return config;
    }

    /**
     * Expõe o bean {@link AuthenticationManager} utilizado pelo {@code LoginUseCase}.
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    /**
     * Bean de codificador de senha BCrypt — utilizado para criar e verificar hashes de senha.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

