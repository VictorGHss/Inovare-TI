package br.dev.ctrls.inovareti.config;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Filtro de segurança personalizado para autenticar requisições de webhooks do Blip
 * que acionam ações administrativas manuais (como trigger de confirmação de consultas).
 * 
 * Este filtro intercepta chamadas na rota "/webhooks/blip/manual-trigger" e valida
 * a presença de uma chave estática secreta no cabeçalho "X-Inovare-Token" contra
 * a variável de ambiente de produção "APP_BLIP_SECURITY_WEBHOOK_TOKEN".
 * 
 * Se o token coincidir, o filtro autentica a requisição com o papel "ROLE_ADMIN", 
 * permitindo o acionamento bem-sucedido de endpoints protegidos por roles no controlador.
 */
@Slf4j
public class ManualTriggerKeyFilter extends OncePerRequestFilter {

    private static final String MANUAL_TRIGGER_PATH = "/webhooks/blip/manual-trigger";
    private static final String HEADER_NAME = "X-Inovare-Token";

    private final String expectedKey = System.getenv("APP_BLIP_SECURITY_WEBHOOK_TOKEN");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !isProtectedPath(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        String method = request.getMethod();

        log.info("[TOKEN WEBHOOK] Validando token de webhook para {} {} (cabeçalho {} presente: {}).",
            method,
            path,
            HEADER_NAME,
            request.getHeader(HEADER_NAME) != null);

        if (expectedKey == null || expectedKey.isBlank()) {
            log.error("[TOKEN WEBHOOK] Acesso Negado: variável de ambiente APP_BLIP_SECURITY_WEBHOOK_TOKEN ausente. método={}, caminho={}, status=401",
                method,
                path);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        String headerValue = request.getHeader(HEADER_NAME);
        if (headerValue == null || !expectedKey.equals(headerValue)) {
            String reason = headerValue == null ? "cabeçalho_ausente" : "token_invalido";
            log.error("[TOKEN WEBHOOK] Acesso Negado: {}. método={}, caminho={}, cabeçalho={}, status=401",
                reason,
                method,
                path,
                HEADER_NAME);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        // Cria autenticação com o principal "blip-webhook" e concede autoridade administrativa ROLE_ADMIN
        // para que o acionador do webhook possa chamar os endpoints protegidos com segurança.
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        "blip-webhook", 
                        null, 
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                );
        authentication.setDetails(Map.of("blipWebhook", true));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        log.info("[TOKEN WEBHOOK] Acesso liberado pelo token para a URI: {}", request.getRequestURI());
        filterChain.doFilter(request, response);
    }

    private boolean isProtectedPath(String path) {
        return MANUAL_TRIGGER_PATH.equals(path)
            || ("/api" + MANUAL_TRIGGER_PATH).equals(path);
    }
}
