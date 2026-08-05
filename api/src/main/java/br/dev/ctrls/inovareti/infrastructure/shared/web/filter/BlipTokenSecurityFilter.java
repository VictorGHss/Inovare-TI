package br.dev.ctrls.inovareti.infrastructure.shared.web.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

/**
 * Filtro de Segurança HTTP para validação do cabeçalho X-Inovare-Token
 * em todos os endpoints consumidos pelo Take Blip (/v1/nlp/**, /v1/feegow/**, /v1/atendimento/**).
 */
@Slf4j
@Component
public class BlipTokenSecurityFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Inovare-Token";
    private static final String DEFAULT_TOKEN = "Wm2p9n6PlsaLvNuHWncxFlEXbknBB6XG";

    @Value("${blip.integration.token:Wm2p9n6PlsaLvNuHWncxFlEXbknBB6XG}")
    private String expectedToken;

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
        String headerValue = request.getHeader(HEADER_NAME);

        String effectiveExpectedToken = (expectedToken != null && !expectedToken.isBlank())
                ? expectedToken
                : DEFAULT_TOKEN;

        boolean tokenMatches = false;
        if (headerValue != null && !headerValue.isBlank()) {
            tokenMatches = MessageDigest.isEqual(
                    effectiveExpectedToken.getBytes(StandardCharsets.UTF_8),
                    headerValue.getBytes(StandardCharsets.UTF_8)
            );
        }

        if (!tokenMatches) {
            log.warn("[BLIP-SECURITY] Acesso Negado (HTTP 401). Método: {}, Path: {}, Header '{}': {}",
                    method, path, HEADER_NAME, headerValue != null ? "inválido" : "ausente");

            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\": \"Acesso não autorizado\"}");
            return;
        }

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "blip-integration",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_BLIP"), new SimpleGrantedAuthority("ROLE_USER"))
        );
        authentication.setDetails(Map.of("blipIntegration", true));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        log.debug("[BLIP-SECURITY] Acesso autorizado com sucesso para: {} {}", method, path);
        filterChain.doFilter(request, response);
    }

    private boolean isProtectedPath(String path) {
        if (path == null) return false;
        String cleanPath = path.toLowerCase();
        return cleanPath.startsWith("/v1/nlp")
                || cleanPath.startsWith("/api/v1/nlp")
                || cleanPath.startsWith("/v1/feegow")
                || cleanPath.startsWith("/api/v1/feegow")
                || cleanPath.startsWith("/v1/atendimento")
                || cleanPath.startsWith("/api/v1/atendimento");
    }
}
