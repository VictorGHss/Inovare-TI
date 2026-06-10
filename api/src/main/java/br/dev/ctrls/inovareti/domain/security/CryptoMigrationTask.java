package br.dev.ctrls.inovareti.domain.security;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import br.dev.ctrls.inovareti.modules.user.infrastructure.adapter.output.jpa.repository.SpringDataUserRepository;
import br.dev.ctrls.inovareti.modules.finance.domain.port.ContaAzulOAuthTokenRepository;
import br.dev.ctrls.inovareti.modules.ticket.infrastructure.adapter.output.jpa.repository.TicketJpaRepository;

import br.dev.ctrls.inovareti.modules.user.domain.model.User;
import br.dev.ctrls.inovareti.modules.finance.domain.model.ContaAzulOAuthToken;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.Ticket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.List;

/**
 * Task executada no startup da aplicação que realiza uma varredura pró-ativa
 * para forçar a migração de registros legados. A simples leitura das colunas
 * aciona o CryptoConverter e dispara o evento assíncrono de Lazy Upgrade.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CryptoMigrationTask implements CommandLineRunner {

    private final SpringDataUserRepository userRepository;
    private final ContaAzulOAuthTokenRepository contaAzulTokenRepository;
    private final TicketJpaRepository ticketRepository;

    @Override
    public void run(String... args) throws Exception {
        log.info("[MIGRAÇÃO-CRIPTOGRAFIA] Iniciando varredura preventiva de inicialização para migração de registros legados...");

        try {
            // 1. Varredura de Usuários
            List<User> users = userRepository.findAll();
            int userCount = 0;
            for (User user : users) {
                // A leitura do método força o acionamento do conversor
                if (user.getTotpSecret() != null) {
                    userCount++;
                }
            }
            log.info("[MIGRAÇÃO-CRIPTOGRAFIA] Varredura de Usuários concluída. {} registros verificados.", userCount);

            // 2. Varredura de Chamados (Tickets)
            List<Ticket> tickets = ticketRepository.findAll();
            int ticketCount = 0;
            for (Ticket ticket : tickets) {
                if (ticket.getAnydeskCode() != null) {
                    ticketCount++;
                }
            }
            log.info("[MIGRAÇÃO-CRIPTOGRAFIA] Varredura de Chamados concluída. {} registros verificados.", ticketCount);

            // 3. Varredura de Tokens da Conta Azul
            List<ContaAzulOAuthToken> tokens = contaAzulTokenRepository.findAll();
            int tokenCount = 0;
            for (ContaAzulOAuthToken token : tokens) {
                if (token.getAccessToken() != null || token.getRefreshToken() != null) {
                    tokenCount++;
                }
            }
            log.info("[MIGRAÇÃO-CRIPTOGRAFIA] Varredura de Tokens Conta Azul concluída. {} registros verificados.", tokenCount);

            log.info("[MIGRAÇÃO-CRIPTOGRAFIA] Varredura de inicialização concluída. O banco de dados está processando a migração para o padrão AES-GCM em segundo plano.");
        } catch (Exception e) {
            log.error("[MIGRAÇÃO-CRIPTOGRAFIA] Falha na execução da varredura de migração automática", e);
        }
    }
}
