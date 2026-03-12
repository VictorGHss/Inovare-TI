package br.dev.ctrls.inovareti.domain.audit;

import java.time.LocalDateTime;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Listener assíncrono que persiste registros de auditoria sem bloquear a transação principal.
 * O @Async garante execução em thread separada, isolando possíveis falhas de persistência
 * do log do fluxo de negócio que originou o evento.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditEventListener {

    private final AuditLogRepository auditLogRepository;

    @Async
    @EventListener
    public void onAuditEvent(AuditEvent event) {
        try {
            AuditLog log = AuditLog.builder()
                    .userId(event.getUserId())
                    .action(event.getAction())
                    .resourceType(event.getResourceType())
                    .resourceId(event.getResourceId())
                    .details(event.getDetails())
                    .ipAddress(event.getIpAddress())
                    .createdAt(LocalDateTime.now())
                    .build();

            auditLogRepository.save(log);
        } catch (Exception ex) {
            // Falha no log de auditoria nunca deve propagar para o chamador
            log.error("Falha ao persistir registro de auditoria [action={}]: {}",
                    event.getAction(), ex.getMessage());
        }
    }
}
