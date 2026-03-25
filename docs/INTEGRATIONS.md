# Integrações — Inovare TI

Este documento descreve as integrações externas suportadas pela plataforma: ContaAzul (financeiro), envio de e-mails, Discord e Vault.

---

## ContaAzul (Financeiro)

### Arquivos e classes relevantes

- `domain/financeiro/contaazul/ContaAzulTokenService` — gerenciamento OAuth2 (authorization_code, refresh), persistência e refresh proativo.
- `ContaAzulClient` — cliente HTTP para endpoints ContaAzul (vendas, parcelas, baixas, anexos).
- `ContaAzulOAuthToken` / `ContaAzulOAuthTokenRepository` — persistência em `contaazul_oauth_tokens`.
- `ContaAzulAutomationService` — job de automação que processa vendas/parcelas e envia recibos.
- `ContaAzulController` — endpoints: authorize, callback, status, testes.

### Variáveis de configuração

- `app.contaazul.client-id`
- `app.contaazul.client-secret`
- `app.contaazul.authorization-url` (ex.: https://auth.contaazul.com/oauth2/authorize)
- `app.contaazul.token-url` (ex.: https://auth.contaazul.com/oauth2/token)
- `contaazul.redirect-uri` — callback
- `contaazul.automation.enabled`, `contaazul.automation.fixed-delay-ms`

### Banco de dados — tabela de tokens

Tabela: `contaazul_oauth_tokens`

Colunas relevantes:

- `id` UUID (PK)
- `access_token` TEXT
- `refresh_token` TEXT
- `token_type` VARCHAR
- `scope` VARCHAR
- `expires_at` TIMESTAMP
- `refreshed_at` TIMESTAMP
- `created_at`, `updated_at`

### Fluxo OAuth2 (Authorization Code)

1. Usuário clica em "Conectar ContaAzul" no frontend → backend chama `buildAuthorizationUrl()`.
2. ContaAzul redireciona com `code` e `state` para o `redirect_uri`.
3. Backend troca `code` por tokens via `exchangeAuthorizationCode()` e persiste em `contaazul_oauth_tokens`.
4. Serviços utilizam `getValidAccessToken()` que faz refresh automático quando necessário.

### Exemplos (curl)

Montar URL de autorização (exemplo retornado pela API):

```
https://auth.contaazul.com/oauth2/authorize?response_type=code&client_id=YOUR_CLIENT_ID&redirect_uri=http://localhost:5173/contaazul/callback&state=RANDOM
```

Trocar code por token (curl):

```bash
curl -X POST "https://auth.contaazul.com/oauth2/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=authorization_code&code=THE_CODE&client_id=YOUR_CLIENT_ID&client_secret=YOUR_CLIENT_SECRET&redirect_uri=http://localhost:5173/contaazul/callback"
```

Refresh token (curl):

```bash
curl -X POST "https://auth.contaazul.com/oauth2/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=refresh_token&refresh_token=REFRESH_TOKEN&client_id=YOUR_CLIENT_ID&client_secret=YOUR_CLIENT_SECRET"
```

### Automação e envio de recibos

- `ContaAzulAutomationService` realiza polling de vendas/parcelas e determina se já foram processadas.
- Faz download de PDF (quando disponível) e aciona `FinanceEmailService` para envio de recibos.
- Registra `ProcessedReceipt`/`ProcessedSale` para garantir idempotência.

### Erros comuns e troubleshooting

- `Invalid ContaAzul token response.` — verificar `client_id`/`client_secret` e `redirect_uri`.
- `NoReceiptAvailableException` — baixar manualmente no painel ContaAzul.
- 401 após refresh — verificar possível revogação de credenciais.

---

## Email financeiro / `FinanceEmailService`

- Local: `domain/notification/FinanceEmailService`.
- Usa `JavaMailSender` para enviar emails com anexos (PDFs de recibo).
- Suporta `app.financeiro.test-mode` para redirecionar envios para email de desenvolvedor.
- Variáveis: `app.financeiro.smtp.*`, `app.financeiro.dev-email`.

---

## Discord — notificações e recuperação

- Pacote: `domain/notification/discord` e `infra/discord`.
- Componentes: `DiscordWebhookService`, `DiscordDirectMessageService`, `DiscordTicketService`, `DiscordUserLinkingService`, `DiscordBotConfig`, `DiscordEventListener`.
- Bot (JDA 5) inicializado assíncronamente para evitar impacto no startup.
- Usado para: notificações de tickets, DMs de recuperação de 2FA e notificações operacionais.

---

## Vault — armazenamento de segredos

- `VaultService` realiza CRUD de itens do cofre e criptografa `secretContent` via `EncryptionService`.
- Arquivos anexos ao Vault são armazenados via `LocalFileStorageService`.
- Criptografia: AES-256/GCM com IV aleatório; chave derivada de `app.vault.encryption-key`.

---

## Observações e Boas Práticas

- Tratar `app.contaazul.client-secret` e `app.vault.encryption-key` como segredos críticos.
- Não logar tokens completos em produção; use previews quando necessário.
- Implementar métricas e health indicators para tokens e automações.
