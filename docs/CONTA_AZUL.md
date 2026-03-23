**Integração ContaAzul (Financeiro)**

Resumo técnico completo da integração com a ContaAzul implementada no backend.

Arquivos relevantes
- `api/src/main/java/br/dev/ctrls/inovareti/domain/financeiro/contaazul/ContaAzulTokenService.java` — gerenciamento OAuth2 (authorization_code, refresh), persistência, refresh proativo.
- `api/src/main/java/br/dev/ctrls/inovareti/domain/financeiro/contaazul/ContaAzulClient.java` — cliente HTTP para endpoints ContaAzul (vendas, parcelas, baixas, anexos).
- `api/src/main/java/br/dev/ctrls/inovareti/domain/financeiro/contaazul/ContaAzulOAuthToken.java` — entidade JPA `contaazul_oauth_tokens`.
- `api/src/main/java/br/dev/ctrls/inovareti/domain/financeiro/contaazul/ContaAzulOAuthTokenRepository.java` — repositório para tokens.
- `api/src/main/java/br/dev/ctrls/inovareti/domain/financeiro/contaazul/ContaAzulAutomationService.java` — job de automação que consulta vendas/parcelas e dispara envio de recibos.
- `api/src/main/java/br/dev/ctrls/inovareti/domain/financeiro/contaazul/ContaAzulController.java` — endpoints públicos: authorize, callback, status, testes.

Variáveis de ambiente / propriedades (ver `application.properties` / `.env.example`)
- `app.contaazul.client-id`
- `app.contaazul.client-secret`
- `app.contaazul.authorization-url` (padrão https://auth.contaazul.com/oauth2/authorize)
- `app.contaazul.token-url` (padrão https://auth.contaazul.com/oauth2/token)
- `contaazul.redirect-uri` (callback frontend)
- `app.contaazul.payments-url`, `app.contaazul.sales-v2-url`, `app.contaazul.customers-v1-url` (endpoints API ContaAzul)
- `contaazul.automation.enabled` / `CONTAAZUL_AUTOMATION_ENABLED` (habilita job de polling)
- `contaazul.automation.fixed-delay-ms` (intervalo)

Banco de dados — tabela de tokens
- Tabela: `contaazul_oauth_tokens` (definida pela entidade `ContaAzulOAuthToken`)
- Colunas relevantes:
  - `id` UUID (PK)
  - `access_token` TEXT
  - `refresh_token` TEXT
  - `token_type` VARCHAR
  - `scope` VARCHAR
  - `expires_at` TIMESTAMP
  - `refreshed_at` TIMESTAMP
  - `created_at`, `updated_at`

Fluxo OAuth2 (Authorization Code)
1. Usuário/operador clica em "Conectar ContaAzul" na UI → backend aciona `ContaAzulTokenService.buildAuthorizationUrl()` que monta URL com `response_type=code`, `client_id` e `redirect_uri`.
2. ContaAzul redireciona o usuário para o `redirect_uri` com `code` e `state`.
3. Frontend repassa `code` para `ContaAzulController` callback endpoint (ou o backend trata o callback direto). Backend chama `ContaAzulTokenService.exchangeAuthorizationCode(code, redirectUri)`.
4. `ContaAzulTokenService` faz POST form-encoded para o token endpoint (`grant_type=authorization_code`) e chama `persistToken(response)` — salva `access_token` e `refresh_token` em `contaazul_oauth_tokens`.
5. A partir daí, serviços usam `ContaAzulTokenService.getValidAccessToken()` para obter token válido (faz refresh automático se expirar).

Renovação de token e refresh proativo
- `ContaAzulTokenService` executa `refreshTokenProactively()` agendado (`@Scheduled`) para renovar o token antes de expirar.
- Ao detectar `401` em chamadas a ContaAzul, `ContaAzulClient` pede refresh via `contaAzulTokenService.forceRefreshAndReloadFromDatabase()` e re-tenta a chamada.

Consumo da API ContaAzul
- `ContaAzulClient` usa Java HttpClient + `ObjectMapper` para:
  - Buscar eventos financeiros (contas a receber) e extrair `sale_id` / `baixa_id`.
  - Fetch de detalhes de parcela, busca de cliente por e-mail e download de PDFs de recibo.
  - Lógica de paginação (PAGE_SIZE=100, MAX_PAGES) e parsing defensivo (vários caminhos JSON possíveis).
- Erros HTTP específicos (ex.: 401) são tratados para forçar refresh; outras falhas são logadas e propagadas via `ContaAzulHttpException` ou `IllegalStateException`.

Automação e envio de recibos
- `ContaAzulAutomationService` é responsável por:
  - Polling de vendas/parcelas adquiridas dentro do intervalo configurado.
  - Determinar se uma parcela já foi processada consultando `ProcessedReceipt` / `ProcessedSale`.
  - Baixar PDF do recibo (se presente) e chamar `ReceiptDispatcher` / `FinanceEmailService` para enviar o e-mail.
  - Registrar `ProcessingAttempt` e status (`ProcessedReceiptStatus`) para evitar duplicação.

Endpoints públicos importantes
- `GET /financeiro/contaazul/authorize` — retorna redirect para ContaAzul (URL de autorização).
- `GET /financeiro/contaazul/callback?code=...` — endpoint callback que troca o code pelo token e persiste.
- `GET /financeiro/contaazul/status` — retorna status de autorização (token presente/expiração).
- `POST /financeiro/contaazul/force-refresh` — endpoint administrativo para forçar refresh manual (se existir).

Exemplos práticos
- Montar URL de autorização (exemplo retornado pela API):
  - https://auth.contaazul.com/oauth2/authorize?response_type=code&client_id=YOUR_CLIENT_ID&redirect_uri=http://localhost:5173/contaazul/callback&state=RANDOM

- Trocar código por token (exemplo curl):

```bash
curl -X POST "https://auth.contaazul.com/oauth2/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=authorization_code&code=THE_CODE&client_id=YOUR_CLIENT_ID&client_secret=YOUR_CLIENT_SECRET&redirect_uri=http://localhost:5173/contaazul/callback"
```

- Refresh token (exemplo curl):

```bash
curl -X POST "https://auth.contaazul.com/oauth2/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=refresh_token&refresh_token=REFRESH_TOKEN&client_id=YOUR_CLIENT_ID&client_secret=YOUR_CLIENT_SECRET"
```

Erros comuns e troubleshooting
- `Invalid ContaAzul token response.` — verifique `client_id`/`client_secret` e `redirect_uri` configurados no portal ContaAzul.
- `NoReceiptAvailableException` — a baixa existe mas nenhum anexo de recibo foi publicado; aguardar geração do recibo ou verificar no painel ContaAzul.
- 401 repetido após refresh — checar se a ContaAzul revogou credenciais ou se o refresh token foi rotacionado fora do controle; inspecionar registros em `contaazul_oauth_tokens`.

Testes e validação
- Unit tests: mockar `RestTemplate`/`HttpClient` para validar `postTokenRequest()` e parsing `ContaAzulTokenResponse`.
- Integration tests: rodar com sandbox ContaAzul (se disponível) ou usar fixtures JSON para `ContaAzulClient` parsing behavior.
- E2E: validar fluxo completo com frontend (authorize → callback → job automation executando um processamento de recibo).

Segurança e recomendações
- Tratar `app.contaazul.client-secret` como segredo (armazenar em vault/secret manager).
- Auditoria: logs já registram previews de token (`previewToken`) — não logar tokens completos em produção.
- Rotação de refresh tokens: documentar procedimento caso seja necessário reautorizar a conta (tela de admin para re-conectar).

Próximos passos sugeridos
1. Gerar exemplos de payloads de teste e cassetes para `ContaAzulClient` (fixtures JSON).  
2. Adicionar health-check/metrics para a automação (`ContaAzulAutomationService`) (ex.: lastRun, lastSuccess, lastError).
3. Documentar procedimento de re-autorização manual (passo a passo no frontend + backend).

Referências no código
- `ContaAzulTokenService`, `ContaAzulClient`, `ContaAzulOAuthToken`, `ContaAzulOAuthTokenRepository`, `ContaAzulAutomationService`, `ContaAzulController`.
