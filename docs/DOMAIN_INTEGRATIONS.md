**Domain — Integrações e Serviços Externos**

Este documento resume as classes no pacote `domain` responsáveis por integrações externas e pontos de contato com serviços fora da aplicação.

1) ContaAzul (Financeiro)
- Local: `api/src/main/java/br/dev/ctrls/inovareti/domain/financeiro/contaazul/`
- Principais classes:
  - `ContaAzulTokenService`: gerencia fluxo OAuth2 (authorization code, refresh), persiste tokens em `contaazul_oauth_tokens`, renovação proativa agendada (`@Scheduled`). Usa `RestTemplate` para POST do token.
  - `ContaAzulOAuthToken` + `ContaAzulOAuthTokenRepository`: entidade JPA que armazena `access_token`, `refresh_token`, `expires_at`, `refreshed_at`.
  - `ContaAzulClient`: cliente HTTP robusto (Java HttpClient + ObjectMapper) para consultar vendas, baixas, detalhes de parcela, download de PDFs de recibo e busca de clientes por e-mail. Implementa lógica de paginação, parsing flexível de JSON e retry on 401 via refresh de token.
  - `ContaAzulController`: endpoints públicos para iniciar fluxo OAuth (`/financeiro/contaazul/authorize`), callback, status e ações de teste.
  - `ContaAzulAutomationService`: job de automação que consulta vendas/parcelas e integra com o fluxo de envio de recibos (usa `ContaAzulClient` + `FinanceEmailService`).
  - Exceptions & DTOs: `NoReceiptAvailableException`, `ContaAzulAuthException`, DTOs para mapeamento.

2) Email (Financeiro)
- Local: `api/src/main/java/br/dev/ctrls/inovareti/domain/notification/FinanceEmailService.java`
- Responsabilidade: montar e enviar e-mails de recibo via `JavaMailSender` (Spring Boot Mail).
- Comportamento:
  - Suporta envio com anexo PDF (bytes) e fallback para modo de teste (`app.financeiro.test-mode`) que envia para o e-mail do desenvolvedor.
  - Valida configuração: `app.financeiro.smtp.from-email`, `app.financeiro.smtp.from-name` e `app.financeiro.dev-email` quando em `test-mode`.

3) Discord / Notificações
- Local: `api/src/main/java/br/dev/ctrls/inovareti/domain/notification/discord/` e `domain/notification`
- Principais classes:
  - `DiscordWebhookService`: roteia notificações de tickets para usuários com `discordUserId` via `DiscordDirectMessageService`.
  - `DiscordDirectMessageService`, `DiscordTicketService`, `DiscordUserLinkingService`, `DiscordBotConfig`, `DiscordEventListener`: integração JDA (bot) que implementa envio de DMs, escuta de eventos e linking de usuários entre app e Discord.
  - `CreateNotificationService` e `NotificationController`: persistem e expõem notificações internas.

4) Vault (Segurança de segredos e anexos)
- Local: `api/src/main/java/br/dev/ctrls/inovareti/domain/vault/` e `infra/security` + `infra/storage`.
- Principais classes:
  - `VaultService`: CRUD de itens do cofre, criptografa `secretContent` para `VaultItemType.CREDENTIAL` via `EncryptionService` (infra), armazena anexos via `LocalFileStorageService` (infra) e publica eventos de auditoria (`AuditLogService`).
  - Entidades: `VaultItem`, `VaultItemShare`, repositórios relacionados.

5) Observações de segurança e operação
- Tokens OAuth da ContaAzul são persistidos no banco; `ContaAzulTokenService` renova proativamente e trata falhas de refresh com logs.
- Downloads de PDFs e chamadas à ContaAzul fazem parsing defensivo do JSON e tratam casos sem anexos (lançam `NoReceiptAvailableException`).
- `FinanceEmailService` confia no `JavaMailSender` configurado pelo Spring — variáveis SMTP devem estar corretas no ambiente.

6) Referências de arquivos
- ContaAzul: `ContaAzulTokenService`, `ContaAzulClient`, `ContaAzulOAuthToken`, `ContaAzulOAuthTokenRepository`, `ContaAzulAutomationService`, `ContaAzulController`.
- Email: `FinanceEmailService`.
- Discord: `domain/notification/discord/*`.
- Vault: `domain/vault/*` e `infra/security/EncryptionService`, `infra/storage/LocalFileStorageService`.

Próximo: vou analisar o pacote `infra` e documentar os serviços de storage e segurança.
