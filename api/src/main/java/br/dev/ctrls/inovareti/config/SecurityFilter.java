package br.dev.ctrls.inovareti.config;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import br.dev.ctrls.inovareti.domain.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Filtro de autenticação JWT executado uma vez por requisição.
 * Extrai o Bearer token do cabeçalho Authorization, valida-o,
 * carrega o usuário correspondente e popula o {@link SecurityContextHolder}.
 */
@Component
// REMOVIDO: @RequiredArgsConstructor
public class SecurityFilter extends OncePerRequestFilter {

    // Usar os mesmos caminhos expostos pelos controllers (sem prefixo "/api").
    private static final String CONTA_AZUL_AUTHORIZE_PATH = "/financeiro/contaazul/authorize";
    private static final String CONTA_AZUL_CALLBACK_PATH = "/financeiro/contaazul/callback";

    private final TokenService tokenService;
    private final UserRepository userRepository;

    // ADICIONADO: Construtor manual com a anotação @Lazy no repositório
    public SecurityFilter(TokenService tokenService, @Lazy UserRepository userRepository) {
        this.tokenService = tokenService;
        this.userRepository = userRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        // Não aplicar o filtro para rotas de autenticação ou endpoints do ContaAzul.
        // Somente pular o filtro para endpoints públicos de autenticação (login, reset inicial).
        // Não pular rotas de 2FA que precisam do SecurityContext preenchido.
        // Além dos endpoints de auth, também pular o filtro para endpoints do
        // Actuator (ex.: /api/actuator/prometheus) para permitir que Prometheus
        // colete métricas sem exigir um token JWT.
        return requestUri.contains("/auth/login")
            || requestUri.contains("/auth/reset-initial-password")
            || requestUri.contains(CONTA_AZUL_AUTHORIZE_PATH)
            || requestUri.contains(CONTA_AZUL_CALLBACK_PATH)
            || requestUri.contains("/v1/webhook/blip")
            || requestUri.contains("/api/v1/webhook/blip")
            || requestUri.contains("/actuator/")
            || requestUri.startsWith("/api/actuator/")
            || requestUri.contains("/ws/")
            || requestUri.startsWith("/api/ws/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String token = extractToken(request);

        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        // Preferir extrair o userId diretamente do claim do token.
        String userIdClaim = tokenService.getUserIdFromToken(token);
        if (userIdClaim != null && !userIdClaim.isBlank()) {
            try {
                UUID userId = UUID.fromString(userIdClaim);
                userRepository.findById(userId).ifPresent(user -> {
                    // Use o ID (String) como principal para compatibilidade com o código
                    // existente que chama `getPrincipal().toString()`.
                    var principal = user.getId() != null ? user.getId().toString() : null;
                    var authentication = new UsernamePasswordAuthenticationToken(
                        principal, null, user.getAuthorities());
                    authentication.setDetails(Map.of(
                        "twoFactorVerified", tokenService.isTwoFactorVerified(token)
                    ));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                });
            } catch (IllegalArgumentException ex) {
                // invalid UUID in claim - fallback to email-based resolution below
            }
        } else {
            String email = tokenService.validateToken(token);
            if (email == null || email.isBlank()) {
                filterChain.doFilter(request, response);
                return;
            }

                userRepository.findByEmail(email).ifPresent(user -> {
                var principal = user.getId() != null ? user.getId().toString() : null;
                var authentication = new UsernamePasswordAuthenticationToken(
                    principal, null, user.getAuthorities());
                authentication.setDetails(Map.of(
                    "twoFactorVerified", tokenService.isTwoFactorVerified(token)
                ));
                SecurityContextHolder.getContext().setAuthentication(authentication);
                });
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}