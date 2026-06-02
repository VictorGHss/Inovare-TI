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
import br.dev.ctrls.inovareti.modules.auth.domain.port.output.TokenPort;
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
    private static final String BLIP_WEBHOOK_PATH = "/v1/webhook/blip";
    private static final String BLIP_WEBHOOK_ALIAS_PATH = "/webhooks/blip";
    private static final String BLIP_MANUAL_TRIGGER_PATH = "/webhooks/blip/manual-trigger";
    private static final String APPOINTMENT_BLIP_WEBHOOK_PATH = "/v1/appointments/blip/webhook";
    private static final String APPOINTMENT_DEBUG_QUEUES_PATH = "/v1/appointments/admin/debug-queues";

    private final TokenPort tokenService;
    private final UserRepository userRepository;

    // ADICIONADO: Construtor manual com a anotação @Lazy no repositório
    public SecurityFilter(TokenPort tokenService, @Lazy UserRepository userRepository) {
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
        // Ignora a validação JWT apenas para a rota de coleta pública de métricas do Prometheus.
        // Outros caminhos do Actuator (como /actuator/env, /actuator/beans) exigem a validação do token JWT do Admin.
        
        // CORREÇÃO DE SEGURANÇA: Substituição de checagens fracas por checagens de caminho estritas.
        boolean isPrometheusPath = requestUri.equals("/actuator/prometheus")
            || requestUri.equals("/actuator/prometheus/")
            || requestUri.startsWith("/api/actuator/prometheus");

        // Ignora a validação para o webhook comum do Blip, mas exige validação de autenticação
        // caso seja o endpoint de trigger manual administrativo (/webhooks/blip/manual-trigger).
        boolean isWebhooksPath = (requestUri.startsWith("/webhooks/") && !requestUri.equals(BLIP_MANUAL_TRIGGER_PATH) && !requestUri.equals(BLIP_MANUAL_TRIGGER_PATH + "/"))
            || (requestUri.startsWith("/api/webhooks/") && !requestUri.equals(BLIP_MANUAL_TRIGGER_PATH) && !requestUri.equals(BLIP_MANUAL_TRIGGER_PATH + "/"));

        boolean isAuthPath = requestUri.equals("/auth/login")
            || requestUri.equals("/auth/login/")
            || requestUri.equals("/api/auth/login")
            || requestUri.equals("/api/auth/login/")
            || requestUri.equals("/auth/reset-initial-password")
            || requestUri.equals("/auth/reset-initial-password/")
            || requestUri.equals("/api/auth/reset-initial-password")
            || requestUri.equals("/api/auth/reset-initial-password/");

        boolean isContaAzulPath = requestUri.equals(CONTA_AZUL_AUTHORIZE_PATH)
            || requestUri.equals(CONTA_AZUL_AUTHORIZE_PATH + "/")
            || requestUri.equals("/api" + CONTA_AZUL_AUTHORIZE_PATH)
            || requestUri.equals("/api" + CONTA_AZUL_AUTHORIZE_PATH + "/")
            || requestUri.equals(CONTA_AZUL_CALLBACK_PATH)
            || requestUri.equals(CONTA_AZUL_CALLBACK_PATH + "/")
            || requestUri.equals("/api" + CONTA_AZUL_CALLBACK_PATH)
            || requestUri.equals("/api" + CONTA_AZUL_CALLBACK_PATH + "/");

        boolean isWebSocketPath = requestUri.startsWith("/ws/")
            || requestUri.startsWith("/api/ws/")
            || requestUri.equals("/ws")
            || requestUri.equals("/api/ws")
            || requestUri.equals("/ws/")
            || requestUri.equals("/api/ws/");

        return isAuthPath
            || isContaAzulPath
            || requestUri.equals(BLIP_WEBHOOK_PATH)
            || requestUri.equals(BLIP_WEBHOOK_PATH + "/")
            || requestUri.equals("/api" + BLIP_WEBHOOK_PATH)
            || requestUri.equals("/api" + BLIP_WEBHOOK_PATH + "/")
            || requestUri.equals(BLIP_WEBHOOK_ALIAS_PATH)
            || requestUri.equals(BLIP_WEBHOOK_ALIAS_PATH + "/")
            || requestUri.equals("/api" + BLIP_WEBHOOK_ALIAS_PATH)
            || requestUri.equals("/api" + BLIP_WEBHOOK_ALIAS_PATH + "/")
            || isWebhooksPath
            || requestUri.equals(APPOINTMENT_BLIP_WEBHOOK_PATH)
            || requestUri.equals(APPOINTMENT_BLIP_WEBHOOK_PATH + "/")
            || requestUri.equals("/api" + APPOINTMENT_BLIP_WEBHOOK_PATH)
            || requestUri.equals("/api" + APPOINTMENT_BLIP_WEBHOOK_PATH + "/")
            || requestUri.equals(APPOINTMENT_DEBUG_QUEUES_PATH)
            || requestUri.equals("/api" + APPOINTMENT_DEBUG_QUEUES_PATH)
            || isPrometheusPath
            || isWebSocketPath;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        
        // Se o contexto de segurança já possui uma autenticação (ex.: preenchida pelo ManualTriggerKeyFilter),
        // passa para o próximo filtro sem sobrescrever nem re-validar.
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        // CORREÇÃO DE SEGURANÇA: Libera a passagem direta para os Webhooks comuns da Blip com checagem estrita de caminhos.
        if (path.equals("/v1/appointments/blip/webhook")
            || path.equals("/v1/appointments/blip/webhook/")
            || path.equals("/v1/webhook/blip")
            || path.equals("/v1/webhook/blip/")
            || path.equals(BLIP_WEBHOOK_ALIAS_PATH)
            || path.equals(BLIP_WEBHOOK_ALIAS_PATH + "/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = extractToken(request);

        if (token == null || tokenService.isTokenBlacklisted(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Dá preferência para extrair o userId diretamente do claim do token JWT.
        String userIdClaim = tokenService.getUserIdFromToken(token);
        if (userIdClaim != null && !userIdClaim.isBlank()) {
            try {
                UUID userId = UUID.fromString(userIdClaim);
                userRepository.findById(userId).ifPresent(user -> {
                    // Utiliza o ID (String) como principal para manter a compatibilidade com o código legado que usa `getPrincipal().toString()`.
                    var principal = user.getId() != null ? user.getId().toString() : null;
                    var authentication = new UsernamePasswordAuthenticationToken(
                        principal, null, user.getAuthorities());
                    authentication.setDetails(Map.of(
                        "twoFactorVerified", tokenService.isTwoFactorVerified(token)
                    ));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                });
            } catch (IllegalArgumentException ex) {
                // UUID inválido no claim - fallback para a resolução baseada em e-mail abaixo
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