package br.dev.ctrls.inovareti.domain.security;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import java.util.List;

/**
 * Ouvvinte do Spring que intercepta de forma assíncrona o evento de detecção de
 * criptografia legada e executa a atualização automática no banco de dados.
 */
@Slf4j
@Component
public class LegacyCryptoMigrationListener {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Intercepta o evento de detecção de criptografia legada de forma assíncrona,
     * localiza o registro correspondente no banco de dados e salva-o novamente
     * para re-encriptar o valor com o novo padrão AES-GCM (Lazy Upgrade).
     */
    @Async
    @EventListener
    @Transactional
    public void onLegacyCryptoDetected(LegacyCryptoDetectedEvent event) {
        String dbData = event.getDbData();
        String decryptedValue = event.getDecryptedValue();

        try {
            // 1. Tenta buscar e atualizar na entidade User (totpSecret)
            List<?> users = entityManager.createQuery(
                "SELECT u FROM User u WHERE u.totpSecret = :dbData")
                .setParameter("dbData", dbData)
                .getResultList();

            if (!users.isEmpty()) {
                for (Object obj : users) {
                    br.dev.ctrls.inovareti.modules.user.domain.model.User user = 
                        (br.dev.ctrls.inovareti.modules.user.domain.model.User) obj;
                    log.info("[MIGRAÇÃO-CRIPTOGRAFIA] Migrando totp_secret do User ID: {} para o padrão AES-GCM", user.getId());
                    user.setTotpSecret(decryptedValue);
                    entityManager.merge(user);
                }
                return;
            }

            // 2. Tenta buscar e atualizar na entidade Ticket (anydeskCode)
            List<?> tickets = entityManager.createQuery(
                "SELECT t FROM Ticket t WHERE t.anydeskCode = :dbData")
                .setParameter("dbData", dbData)
                .getResultList();

            if (!tickets.isEmpty()) {
                for (Object obj : tickets) {
                    br.dev.ctrls.inovareti.modules.ticket.domain.model.Ticket ticket = 
                        (br.dev.ctrls.inovareti.modules.ticket.domain.model.Ticket) obj;
                    log.info("[MIGRAÇÃO-CRIPTOGRAFIA] Migrando anydesk_code do Ticket ID: {} para o padrão AES-GCM", ticket.getId());
                    ticket.setAnydeskCode(decryptedValue);
                    entityManager.merge(ticket);
                }
                return;
            }

            // 3. Tenta buscar e atualizar na entidade ContaAzulOAuthToken (accessToken ou refreshToken)
            List<?> tokens = entityManager.createQuery(
                "SELECT t FROM ContaAzulOAuthToken t WHERE t.accessToken = :dbData OR t.refreshToken = :dbData")
                .setParameter("dbData", dbData)
                .getResultList();

            if (!tokens.isEmpty()) {
                for (Object obj : tokens) {
                    br.dev.ctrls.inovareti.modules.finance.domain.model.ContaAzulOAuthToken token = 
                        (br.dev.ctrls.inovareti.modules.finance.domain.model.ContaAzulOAuthToken) obj;
                    log.info("[MIGRAÇÃO-CRIPTOGRAFIA] Migrando tokens do ContaAzulOAuthToken ID: {} para o padrão AES-GCM", token.getId());
                    if (dbData.equals(token.getAccessToken())) {
                        token.setAccessToken(decryptedValue);
                    }
                    if (dbData.equals(token.getRefreshToken())) {
                        token.setRefreshToken(decryptedValue);
                    }
                    entityManager.merge(token);
                }
                return;
            }

            log.debug("[MIGRAÇÃO-CRIPTOGRAFIA] Nenhum registro correspondente a '{}' foi encontrado para migração.", dbData);
        } catch (Exception e) {
            log.error("[MIGRAÇÃO-CRIPTOGRAFIA] Falha ao processar atualização e migração de criptografia legada", e);
        }
    }
}
