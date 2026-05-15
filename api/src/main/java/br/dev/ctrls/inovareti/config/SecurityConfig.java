package br.dev.ctrls.inovareti.config;

import java.util.Arrays;
import java.util.List;

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

    private static final String BLIP_WEBHOOK_PATH = "/v1/webhook/blip";
    private static final String BLIP_WEBHOOK_ALIAS_PATH = "/webhooks/blip";
    private static final String BLIP_MANUAL_TRIGGER_PATH = "/webhooks/blip/manual-trigger";
    private static final String APPOINTMENT_BLIP_WEBHOOK_PATH = "/v1/appointments/blip/webhook";
    private static final String APPOINTMENT_ADMIN_PATH = "/v1/appointments/admin/**";
    private static final String APPOINTMENT_DEBUG_QUEUES_PATH = "/v1/appointments/admin/debug-queues";

    private final SecurityFilter securityFilter;
    private final RawBodyLoggingFilter rawBodyLoggingFilter;

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
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/admin/**").permitAll()
                .requestMatchers("/admin/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/v1/appointments/config/**").permitAll()
                .requestMatchers(HttpMethod.GET, APPOINTMENT_DEBUG_QUEUES_PATH).permitAll()
                .requestMatchers(APPOINTMENT_ADMIN_PATH).hasRole("ADMIN")
                .requestMatchers("/auth/**").permitAll()
                .requestMatchers(BLIP_WEBHOOK_PATH, BLIP_WEBHOOK_PATH + "/").permitAll()
                .requestMatchers(BLIP_WEBHOOK_ALIAS_PATH, BLIP_WEBHOOK_ALIAS_PATH + "/").permitAll()
                .requestMatchers(BLIP_MANUAL_TRIGGER_PATH, BLIP_MANUAL_TRIGGER_PATH + "/").permitAll()
                .requestMatchers("/api/webhooks/**").permitAll()
                .requestMatchers("/v1/webhook/**").permitAll()
                .requestMatchers("/api/v1/webhook/**").permitAll()
                // Permitir acesso público aos endpoints do Actuator para que coletores
                // de métricas (ex: Prometheus) possam ler /actuator/** sem JWT.
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/financeiro/contaazul/authorize", "/financeiro/contaazul/callback").permitAll()
                .requestMatchers(
                    HttpMethod.POST,
                    APPOINTMENT_BLIP_WEBHOOK_PATH,
                    APPOINTMENT_BLIP_WEBHOOK_PATH + "/")
                .permitAll()
                // Liberação temporária para desenvolvimento local dos endpoints de configuração.
                .requestMatchers("/v1/appointments/config/**").permitAll()
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
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:5173"));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "x-access-token"));
        config.setAllowCredentials(true);

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
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
            "http://localhost:5173",
            "http://172.25.0.171:5173",
            "http://172.25.0.171",
            "https://itsm-inovare.ctrls.dev.br"
        ));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "x-access-token", "X-Requested-With", "Accept", "Origin"));
        config.setExposedHeaders(Arrays.asList("Authorization", "Content-Type"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
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

