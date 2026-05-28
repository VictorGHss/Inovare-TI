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

- Nota técnica: A geração do PDF do recibo após a baixa é assíncrona. O sistema realiza até 20 tentativas automáticas antes de gerar um alerta operacional.

### ⚡ Redesenho de Performance e Concorrência Paralela
Para sanar o gargalo crítico de rede que causava tempos de carregamento de até 12 segundos nas rotas financeiras, implementamos uma nova arquitetura de alta concorrência:
- **Execução Paralela**: Refatoramos o orquestrador `fetchSummary` no `ContaAzulFinancialSummaryService` para disparar as buscas de recebidos (`status=RECEBIDO`/`QUITADO`), contas em aberto (`status=EM_ABERTO`) e saldos consolidados de contas financeiras simultaneamente em background.
- **CompletableFuture + Virtual Threads**: Cada consulta é envolvida em `CompletableFuture.supplyAsync()` rodando no executor de *Virtual Threads* (`Executors.newVirtualThreadPerTaskExecutor()`). As chamadas são aguardadas de forma não-bloqueante e síncrona com `CompletableFuture.allOf(...).join()`, reduzindo o tempo de latência de rede em mais de 50%.
- **Mecanismo de Cache**: Protegemos a API da ContaAzul anotando o método principal com `@Cacheable(value = "contaAzulSummary", key = "'dashboard'")` para cachear os dados do resumo financeiro por 10 minutos (TTL), economizando custos de requisições redundantes a cada troca de tela no frontend React.

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

### ⚡ Isolamento de Concorrência e Resiliência (Discord Bot)
Para mitigar a queda silenciosa do Bot e prevenir bloqueios na thread principal causados por **Carrier Thread Pinning** (comum no uso direto de Virtual Threads nos loops internos do JDA), implementamos uma separação rígida de concorrência no `DiscordBotConfig.java`:
- **Pool Nativo para Infraestrutura**: Deixamos o JDA utilizar estritamente seus pools nativos e padronizados para gerenciar o tráfego de WebSocket (Gateway), Heartbeats e Rate-Limits com as APIs do Discord.
- **Virtual Threads isoladas para Regras de Negócio**: Injetamos o pool customizado de Virtual Threads (`discordExecutor`) estritamente para a execução assíncrona de nossas regras e UseCases, mantendo o ciclo de vida de conexão do JDA completamente isolado e livre de pinagem.
- **Limpeza e Registro de Comandos**: No boot do Bot, acionamos `updateCommands().queue()` para expurgar comandos globais antigos ou obsoletos, forçando o registro imediato e consistente apenas dos comandos definidos na inicialização.

### Variáveis de ambiente (Discord)

- `DISCORD_WEBHOOK_URL`: webhook utilizado para notificações operacionais (canal de SRE/ops). Preferir webhook para alertas automáticos.
- `DISCORD_BOT_TOKEN`: token do bot JDA (usado para DMs, linking de usuários e eventos bidirecionais).
- `DISCORD_BOT_ENABLED`: `true|false` — ativa inicialização do bot JDA.

Observação: o sistema suporta tanto notificações via webhook (unidirecional, recomendado para alertas operacionais) quanto via bot (JDA) para interações/DMs. Configure o webhook operacional em `DISCORD_WEBHOOK_URL` antes de validar o fluxo de alertas.

---

## Take Blip (Integração WhatsApp / Chatbot)

O ecossistema Inovare-TI integra-se nativamente com a plataforma Take Blip para automação de lembretes e triagem via WhatsApp (Chatbot):

### 1. Comportamento do Webhook e Agrupamento em Lote
- O webhook processa os retornos e interações do paciente de forma resiliente.
- Implementa lógica para agrupamento em lote (*batch ingestion*) de múltiplos agendamentos em um curto espaço de tempo para o mesmo paciente. Isso evita múltiplos envios de lembretes concorrentes e melhora o fluxo de mensagens enviadas.

### 2. Barreira de Atendimento Humano (Desk)
- Antes de disparar lembretes e *nudges* sequenciais automatizados, o motor valida ativamente o estado de interações do paciente.
- Implementamos a verificação `hasActiveTicket` na API do Blip (verificando status `Open` ou `Waiting` de atendimentos humanos ativos). Se houver um atendimento humano em curso no Blip Desk, a esteira incremental de nudges automáticos é imediatamente suspensa para evitar ruído com o paciente.

### 3. Interceptação de Respostas e Redirecionamento Determinista
- Quando o paciente responde ao lembrete clicando em "Manter Agendamento" ou "Cancelar Consulta", a resposta é interceptada no webhook do backend.
- **Manter Agendamento**: Atualiza a sessão local para `CONFIRMED` e dispara a baixa correspondente de confirmação na API do Feegow.
- **Cancelar Consulta**: Atualiza a sessão local para `CANCELED` e dispara o cancelamento da consulta correspondente na API do Feegow.
- **Roteamento Puro via MASTER_STATE (LIME)**: Removemos o uso da variável poluente `attendanceQueueToRedirect` (que causava conflitos na distribuição do portal do Blip). O redirecionamento para o Desk é realizado estritamente de forma pura e nativa usando o comando LIME `MASTER_STATE` apontando diretamente para o bloco do Desk (`desk:644d54dd-aefd-478b-93eb-10081acdd387`), deixando a Take Blip distribuir a fila de forma padrão.

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
