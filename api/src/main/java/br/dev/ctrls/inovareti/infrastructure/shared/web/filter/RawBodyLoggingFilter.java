package br.dev.ctrls.inovareti.infrastructure.shared.web.filter;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RawBodyLoggingFilter extends OncePerRequestFilter {

    private static final int MAX_PAYLOAD_LENGTH = 256 * 1024;
    private static final String BLIP_WEBHOOK_PREFIX = "/api/webhooks/blip";
    private static final String WS_PATH_FRAGMENT = "/ws/";

    /**
     * Não envolve com {@link ContentCachingRequestWrapper} rotas em que o corpo deve ser lido intacto pelo
     * {@code @RequestBody} (ex.: manual-trigger Blip, {@code /v1/appointments/trigger-manual}).
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return false;
        }
        int q = uri.indexOf('?');
        String path = q > 0 ? uri.substring(0, q) : uri;
        return path.endsWith("/trigger-manual") || path.endsWith("/webhooks/blip/manual-trigger");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (uri != null && uri.contains(WS_PATH_FRAGMENT)) {
            filterChain.doFilter(request, response);
            return;
        }

        ContentCachingRequestWrapper wrapped = new ContentCachingRequestWrapper(request, MAX_PAYLOAD_LENGTH);
        filterChain.doFilter(wrapped, response);

        if (!isBlacklisted(uri)) {
            byte[] body = wrapped.getContentAsByteArray();
            String rawBody = body.length == 0
                    ? "<empty>"
                    : new String(body, resolveCharset(wrapped.getCharacterEncoding()));

            if ("GET".equalsIgnoreCase(request.getMethod())) {
                log.debug("[RAW BODY] {} {} -> {}", request.getMethod(), request.getRequestURI(), rawBody);
            } else {
                log.info("[RAW BODY] {} {} -> {}", request.getMethod(), request.getRequestURI(), rawBody);
            }
        }
    }

    private boolean isBlacklisted(String uri) {
        if (uri == null || uri.isBlank()) {
            return false;
        }

        return uri.startsWith(BLIP_WEBHOOK_PREFIX) 
            || uri.contains(WS_PATH_FRAGMENT)
            || uri.contains("/actuator")
            || uri.contains("/notifications");
    }

    private Charset resolveCharset(String encoding) {
        if (encoding == null || encoding.isBlank()) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(encoding);
        } catch (Exception ignored) {
            return StandardCharsets.UTF_8;
        }
    }
}
