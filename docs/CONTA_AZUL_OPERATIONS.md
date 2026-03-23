ContaAzul — Operações: Re-autorização Manual e Health-Checks

Objetivo
- Fornecer procedimentos passo-a-passo para: 1) re-autorização manual quando credenciais/tokens expirarem ou forem revogados; 2) checagens de saúde (health checks) operacionais da integração.

Referências de código
- `ContaAzulController` — endpoints: `/financeiro/contaazul/authorize`, `/financeiro/contaazul/callback`, `/financeiro/contaazul/status`, `/financeiro/contaazul/check-customer/{email}`.
- `ContaAzulTokenService` — métodos: `buildAuthorizationUrl()`, `exchangeAuthorizationCode()`, `getValidAccessToken()`, `forceRefresh()`, `forceRefreshAndReloadFromDatabase()`, `getAuthorizationStatus()`, agendamento `refreshTokenProactively()`.
- Entidade/tabela: `ContaAzulOAuthToken` → `contaazul_oauth_tokens`.

1) Re-autorização manual (quando a conta for desconectada ou refresh token perder validade)

Cenários:
- Usuário revogou a autorização no painel da ContaAzul.
- Refresh token expirou/foi rotacionado fora do app.
- Tokens corrompidos/ausentes no banco.

Passo-a-passo (recomendado, seguro):
1. Verifique status atual via endpoint (requer token ADMIN):

   - Autentique e obtenha JWT (login):

```bash
curl -s -X POST http://localhost:8085/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@inovare.med.br","password":"admin123"}'
```

   - Use o JWT obtido (substitua $TOKEN) para checar status:

```bash
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8085/financeiro/contaazul/status
```

   - Resposta: `{ "authorized": true|false, "expiresAt": "...", "refreshedAt": "..." }`.

2. Se `authorized:false` ou `expiresAt` já expirado, re-autorize via UI (recomendado):
   - No frontend, vá para a seção Financeiro → ContaAzul → Conectar (ou clique em "Conectar ContaAzul").
   - Isso abrirá o fluxo OAuth2 (ContaAzul) e, após consentimento, a ContaAzul chamará `/financeiro/contaazul/callback?code=...` no backend.

3. Se você não tem o frontend disponível, inicie manualmente no backend:
   - Abra no navegador: `http://<API_HOST>:8085/financeiro/contaazul/authorize` — isso fará redirect para a ContaAzul.
   - Complete o consentimento na ContaAzul; o provedor irá redirecionar para o `contaazul.redirect-uri` (configurado) que chama o backend `/callback` e salva o token.

4. Verifique que a tabela `contaazul_oauth_tokens` foi populada:

```sql
SELECT id, substring(access_token from 1 for 10) AS preview, expires_at, refreshed_at, updated_at
FROM contaazul_oauth_tokens
ORDER BY updated_at DESC
LIMIT 5;
```

5. Em casos mais drásticos (re-autorização imediata necessária):
   - Remover entradas antigas para forçar novo fluxo de autorização (EXECUTE com cuidado):

```sql
DELETE FROM contaazul_oauth_tokens;
```

   - Em seguida, repita o passo 2 (abrir `/authorize` no navegador e concluir consentimento).

Avisos:
- Não edite `access_token`/`refresh_token` manualmente a não ser que saiba exatamente o que está inserindo.
- Manter backups antes de deletar registros em produção.

2) Forçar refresh manual
- Não existe um endpoint público específico no controlador para `forceRefresh()` — métodos `forceRefresh()`/`forceRefreshAndReloadFromDatabase()` existem no serviço.
- Opções para forçar refresh:
  1. Temporariamente alterar `expires_at` no DB para uma data passada para que o job agendado (`refreshTokenProactively`) provoque um refresh em curto prazo.

```sql
UPDATE contaazul_oauth_tokens
SET expires_at = now() - interval '1 hour'
WHERE id = (SELECT id FROM contaazul_oauth_tokens ORDER BY updated_at DESC LIMIT 1);
```

  2. Reiniciar a aplicação (em cenários onde reiniciar dispara validações/refresh na inicialização — observe logs).
  3. Implementar/usar um endpoint administrativo que invoque `contaAzulTokenService.forceRefresh()` (recomendado se quiser operação segura via API). Atualmente não existe — se desejar, posso adicionar um endpoint `POST /financeiro/contaazul/force-refresh` protegido por ROLE_ADMIN.

3) Health-checks operacionais (verificações simples e recomendações)

A) Verificações imediatas que você pode executar agora:
- Status via API (requer ADMIN token):

```bash
curl -H "Authorization: Bearer $TOKEN" http://localhost:8085/financeiro/contaazul/status
```

- Verificar entradas na tabela `contaazul_oauth_tokens` (ver SQL acima).
- Procurar logs de sucesso/erro nos logs da aplicação:
  - Mensagens esperadas: `Token salvo. expires_at=... access_token_preview=...` e `ContaAzul token refreshed successfully.`
  - Mensagens de erro: `ContaAzul proactive token refresh failed.` ou `Invalid ContaAzul token response.`

B) Health-checks automáticos recomendados (melhoria a implementar)
- Adicionar um `HealthIndicator` do Spring Boot que retorne:
  - status UP se `contaazul_oauth_tokens` existe e `expires_at` > now() e `access_token` presente.
  - status DOWN se não houver token ou último refresh falhou.
- Expor métricas Prometheus:
  - `contaazul_token_expires_at_timestamp` (gauge)
  - `contaazul_last_refresh_success_timestamp` (gauge)
  - `contaazul_last_refresh_failure_timestamp` (gauge)
  - `contaazul_automation_last_run_success` (gauge 1/0)
- Monitorar logs e criar alertas quando houver falha de refresh ou quando `expires_at` estiver próximo (< 10 min).

C) Checklists operacionais para diagnóstico rápido
1. Status API retorna `authorized: true` e `expiresAt` no futuro → OK.
2. `authorized:false` → iniciar re-autorização (passo de re-autorização manual).
3. `authorized:true` mas envio de recibos falha com 401 → verificar refresh automático, logs de refresh e tentar forçar refresh (ver passo 2 acima).
4. Se refresh sempre falha com 400/401 → provavelmente `client_id`/`client_secret` inválidos ou ContaAzul revogou a aplicação — re-autenticar via `/authorize`.

4) Procedimento de emergência (passo a passo, pronto para enviar ao time de operação)
- Sintoma: geração/entrega de recibos parou e logs mostram 401 ao chamar ContaAzul.
- Ação imediata:
  1. Checar `financeiro/contaazul/status` (ADMIN).
  2. Se `authorized:false` → executar re-autorização via browser `/financeiro/contaazul/authorize`.
  3. Se `authorized:true` mas 401 persistir → executar SQL para setar `expires_at` em passado (ver seção "Forçar refresh manual"), aguardar job ou reiniciar app.
  4. Se ainda falhar → coletar logs (`last 200 lines`), exportar última `contaazul_oauth_tokens` row (sem expor tokens completos) e abrir chamado para suporte ContaAzul.

5) Melhorias recomendadas (curto prazo)
- Implementar endpoint ADMIN `POST /financeiro/contaazul/force-refresh` que chame `contaAzulTokenService.forceRefresh()` (retornando novo `expiresAt`).
- Implementar `HealthIndicator` e métricas Prometheus para token/automation.
- Adicionar testes de integração que validem parsing de respostas e fallback em 401.

---

Arquivo de referência no código:
- `api/src/main/java/br/dev/ctrls/inovareti/domain/financeiro/contaazul/` (token service, client, controller, automation, repository)

Se quiser, eu:
- Implemento o endpoint `POST /financeiro/contaazul/force-refresh` protegido por ROLE_ADMIN agora.
- Ou adiciono um `HealthIndicator` simples que verifica a presença e validade do token e expõe via Actuator.
