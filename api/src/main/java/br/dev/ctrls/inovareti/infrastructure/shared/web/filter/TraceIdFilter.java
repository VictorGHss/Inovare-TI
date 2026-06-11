package br.dev.ctrls.inovareti.infrastructure.shared.web.filter;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_KEY = "traceId";
    private static final String TRACE_ID_FALLBACK_KEY = "trace_id";
    private static final String RESPONSE_HEADER = "X-Trace-Id";

    private static final String[] TRACE_HEADERS = {
            "X-Trace-Id",
            "X-TraceId",
            "traceId",
            "trace_id"
    };

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String traceId = resolveTraceId(request);
        MDC.put(TRACE_ID_KEY, traceId);
        MDC.put(TRACE_ID_FALLBACK_KEY, traceId);
        response.setHeader(RESPONSE_HEADER, traceId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID_KEY);
            MDC.remove(TRACE_ID_FALLBACK_KEY);
        }
    }

    private String resolveTraceId(HttpServletRequest request) {
        for (String header : TRACE_HEADERS) {
            String value = request.getHeader(header);
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return UUID.randomUUID().toString();
    }
}
