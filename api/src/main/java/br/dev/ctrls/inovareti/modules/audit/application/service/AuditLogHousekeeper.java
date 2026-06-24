package br.dev.ctrls.inovareti.modules.audit.application.service;

import java.time.LocalDateTime;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Componente responsável pelo housekeeping automatizado dos logs de auditoria (audit_logs).
 * Remove registros mais antigos que 30 dias de forma assíncrona, em lotes (chunks) discretos,
 * evitando sobrecarregar o banco de dados PostgreSQL ou manter travas longas nas tabelas.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditLogHousekeeper {

    private final AuditLogBatchDeleter deleter;

    /**
     * Agenda a limpeza de logs obsoletos para todo domingo às 03:00 da madrugada (horário de vale).
     */
    @Scheduled(cron = "0 0 3 * * SUN")
    public void executeHousekeeping() {
        log.info("Iniciando rotina semanal de limpeza de logs de auditoria (Housekeeping)...");
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(30);
        int totalDeleted = 0;
        int deletedInBatch;

        do {
            deletedInBatch = deleter.deleteBatch(cutoffDate);
            totalDeleted += deletedInBatch;
            log.info("Lote de housekeeping executado: {} logs de auditoria expurgados nesta iteração.", deletedInBatch);
            
            if (deletedInBatch > 0) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("A rotina de housekeeping de logs de auditoria foi interrompida.", e);
                    break;
                }
            }
        } while (deletedInBatch > 0);

        log.info("Rotina de housekeeping de logs finalizada. Total de registros removidos: {}.", totalDeleted);
    }
}
