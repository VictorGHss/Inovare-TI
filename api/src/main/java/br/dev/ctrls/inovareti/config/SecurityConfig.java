package br.dev.ctrls.inovareti.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

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

    private final SecurityFilter securityFilter;

    /**
     * Define a cadeia de filtros de segurança:
     * - CSRF desabilitado (API stateless)
     * - CORS habilitado (permite o servidor Vite em localhost:5173)
     * - Gerenciamento de sessão: STATELESS
     * - Rotas pública: POST /api/auth/login e POST /api/auth/reset-initial-password
     * - Demais rotas: autenticadas
     * - Filtro JWT executado antes do UsernamePasswordAuthenticationFilter
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authorize -> authorize
                // Considerando que a aplicação define `server.servlet.context-path=/api`,
                // as rotas podem chegar ao Security com ou sem o prefixo "/api".
                // Liberamos ambas as formas para garantir que o endpoint de login funcione.
                .requestMatchers("/auth/**", "/api/auth/**").permitAll()
                // Permitir acesso público aos endpoints do Actuator para que coletores
                // de métricas (ex: Prometheus) possam ler /api/actuator/** sem JWT.
                .requestMatchers("/actuator/**", "/api/actuator/**").permitAll()
                .requestMatchers("/financeiro/contaazul/authorize", "/financeiro/contaazul/callback",
                                 "/api/financeiro/contaazul/authorize", "/api/financeiro/contaazul/callback").permitAll()
                .requestMatchers("/v1/webhook/blip", "/api/v1/webhook/blip").permitAll()
                .requestMatchers("/ws/**", "/api/ws/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(securityFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
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
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
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

