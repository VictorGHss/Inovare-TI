package br.dev.ctrls.inovareti.config;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

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

        log.info("[WEBHOOK TOKEN] Validating token for {} {} (header {} present: {}).",
            method,
            path,
            HEADER_NAME,
            request.getHeader(HEADER_NAME) != null);

        if (expectedKey == null || expectedKey.isBlank()) {
            log.error("[WEBHOOK TOKEN] Denied: missing APP_BLIP_SECURITY_WEBHOOK_TOKEN env var. method={}, path={}, status=401",
                method,
                path);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        String headerValue = request.getHeader(HEADER_NAME);
        if (headerValue == null || !expectedKey.equals(headerValue)) {
            String reason = headerValue == null ? "missing_header" : "token_mismatch";
            log.error("[WEBHOOK TOKEN] Denied: {}. method={}, path={}, header={}, status=401",
                reason,
                method,
                path,
                HEADER_NAME);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken("blip-webhook", null, List.of());
        authentication.setDetails(Map.of("blipWebhook", true));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        log.info("Acesso liberado pelo token para a URI: {}", request.getRequestURI());
        filterChain.doFilter(request, response);
    }

    private boolean isProtectedPath(String path) {
        return MANUAL_TRIGGER_PATH.equals(path)
            || ("/api" + MANUAL_TRIGGER_PATH).equals(path);
    }

}
