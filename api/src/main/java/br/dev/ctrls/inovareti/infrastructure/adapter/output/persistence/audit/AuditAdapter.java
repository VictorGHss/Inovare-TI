package br.dev.ctrls.inovareti.infrastructure.adapter.output.persistence.audit;

import java.time.LocalDateTime;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import br.dev.ctrls.inovareti.core.shared.domain.port.output.AuditPort;
import br.dev.ctrls.inovareti.domain.audit.AuditAction;
import br.dev.ctrls.inovareti.domain.audit.AuditLog;
import br.dev.ctrls.inovareti.domain.audit.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuditAdapter implements AuditPort {

    private static final String TRACE_ID_KEY = "traceId";
    private static final String TRACE_ID_FALLBACK_KEY = "trace_id";

    private final AuditLogRepository auditLogRepository;

    @Override
    public void record(String module, String action, String details, String traceId) {
        String normalizedAction = normalize(action);
        if (!StringUtils.hasText(normalizedAction)) {
            log.warn("Acao de auditoria ignorada por estar vazia. modulo={}, traceId={}", module, traceId);
            return;
        }

        AuditAction auditAction = resolveAction(normalizedAction);
        if (auditAction == null) {
            log.warn("Acao de auditoria desconhecida: {}. Registro ignorado. modulo={}, traceId={}",
                    normalizedAction, module, traceId);
            return;
        }

        String resolvedModule = StringUtils.hasText(module) ? module.trim() : "DESCONHECIDO";
        String resolvedDetails = StringUtils.hasText(details) ? details.trim() : null;
        String resolvedTraceId = StringUtils.hasText(traceId) ? traceId.trim() : resolveTraceIdFromMdc();

        try {
            AuditLog auditLog = AuditLog.builder()
                    .action(auditAction)
                    .resourceType(resolvedModule)
                    .details(resolvedDetails)
                    .traceId(resolvedTraceId)
                    .createdAt(LocalDateTime.now())
                    .build();

            auditLogRepository.save(auditLog);
        } catch (Exception ex) {
            log.error("Falha ao persistir registro de auditoria. action={}, modulo={}, traceId={}, erro={}",
                    normalizedAction, resolvedModule, resolvedTraceId, ex.getMessage());
        }
    }

    private AuditAction resolveAction(String action) {
        try {
            return AuditAction.valueOf(action);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim().toUpperCase();
    }

    private String resolveTraceIdFromMdc() {
        String traceId = MDC.get(TRACE_ID_KEY);
        if (!StringUtils.hasText(traceId)) {
            traceId = MDC.get(TRACE_ID_FALLBACK_KEY);
        }
        return traceId;
    }
}
