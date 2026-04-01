# Operações & Runbooks — Inovare TI

Este documento agrega procedimentos operacionais para administração e triagem, com foco nas integrações financeiras (ContaAzul), métricas Prometheus, throttling e runbooks de emergência.

---

## Re-autorização Manual — ContaAzul

### Objetivo
- Procedimento para re-autorização manual quando tokens expirarem, forem revogados ou corrompidos.

### Passo-a-passo (seguro)

1. Obtenha um JWT de administrador (exemplo):

```bash
curl -s -X POST http://localhost:8085/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@inovare.med.br","password":"admin123"}'
```

2. Cheque o status da ContaAzul (substitua `$TOKEN`):

```bash
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8085/api/financeiro/contaazul/status
```

Resposta esperada: `{ "authorized": true|false, "expiresAt": "...", "refreshedAt": "..." }`.

3. Se `authorized:false` ou `expiresAt` expirado, re-autorize via UI (Financeiro → ContaAzul → Conectar).

4. Se o frontend não estiver disponível, abra no navegador `http://<API_HOST>:8085/financeiro/contaazul/authorize` e conclua o consentimento.

5. Verifique a tabela `contaazul_oauth_tokens`:

```sql
SELECT id, substring(access_token from 1 for 10) AS preview, expires_at, refreshed_at, updated_at
FROM contaazul_oauth_tokens
ORDER BY updated_at DESC
LIMIT 5;
```

6. Em casos extremos, remover entradas antigas (use com cautela):

```sql
DELETE FROM contaazul_oauth_tokens;
```

### Forçar refresh manual

Opções:

1. Ajustar `expires_at` para data passada para que o job agendado provoque refresh:

```sql
UPDATE contaazul_oauth_tokens
SET expires_at = now() - interval '1 hour'
WHERE id = (SELECT id FROM contaazul_oauth_tokens ORDER BY updated_at DESC LIMIT 1);
```

2. Reiniciar a aplicação (quando reinício dispara validação/refresh).
3. Ideal: expor endpoint admin `POST /financeiro/contaazul/force-refresh` (ROLE_ADMIN).

---

## Health-checks e Métricas Recomendadas

- Implementar `HealthIndicator` que verifique existência de token válido (`expires_at > now()`) e retorne UP/DOWN.
- Métricas Prometheus sugeridas:
  - `contaazul_token_expires_at_timestamp` (gauge)
  - `contaazul_last_refresh_success_timestamp` (gauge)
  - `contaazul_automation_last_run_success` (gauge 1/0)

Expor `management.endpoints.web.exposure.include=prometheus,health` em ambientes de monitoramento.

Redis Healthcheck:

- O `docker-compose.yml` agora inclui um `healthcheck` para o serviço `redis` que executa `redis-cli ping` periodicamente. Isso protege a subida do sistema garantindo que a API e outros serviços dependentes não iniciem antes que o cache esteja pronto para uso.
- A API foi configurada para depender do Redis via `depends_on: condition: service_healthy`, evitando que o processo de polling/automação (ContaAzul) inicie antes do Redis estar totalmente operacional.

Importância operacional:

- Evita que a aplicação tente usar o Redis durante o bootstrap, reduzindo erros de conexão (ex.: refused/connection reset) e exceções na inicialização dos jobs agendados.
- Previne que o `ContaAzulAutomationService` execute antes do cache/distribuição de rate-limiter estar pronta, o que poderia causar falhas de throttling e tentativas duplicadas.

Configuração recomendada (desenvolvimento local):

- `interval`: 5s
- `timeout`: 3s
- `retries`: 5

Essas configurações equilibram sensibilidade e robustez para detecção rápida de readiness em ambiente de desenvolvimento local.


Verificações rápidas via terminal

Verificar métricas do Prometheus via terminal:

```bash
curl -u admin:admin123 http://localhost:8085/api/actuator/prometheus | grep contaazul
```

### Prometheus — Conexão e exemplo de scrape


O repositório agora inclui um serviço `prometheus` no `docker-compose.yml` para uso local (porta 9095 no host -> 9090 no container). A configuração usada está em `docs/prometheus/prometheus.yml` e as regras em `docs/prometheus/alert.rules.yml`.

- Endpoint de métricas exposto pela aplicação: `/api/actuator/prometheus` (a aplicação define `server.servlet.context-path=/api`).

Como executar localmente (exemplo mínimo):

```bash
# sobe API, Redis, Front e Prometheus (no background)
docker-compose up -d api redis front prometheus

# ou sobe todos os serviços
docker-compose up -d
```

Depois de iniciados os serviços, abra o UI do Prometheus em:

  http://localhost:9095

Passos rápidos na UI do Prometheus:

1. Acesse `Status -> Targets` e verifique o job `inovare-ti` com target `api:8085` e STATUS `UP`.
2. Use `Graph` para testar uma consulta, ex.: `contaazul_force_refresh_throttled_total`.

Exemplo via curl para a API do Prometheus (consulta instantânea):

```bash
curl "http://localhost:9095/api/v1/query?query=contaazul_force_refresh_throttled_total"
```

Observações de segurança e deploy:

- Em produção prefira um Prometheus provisionado via Helm/Kubernetes; o serviço local é apenas para desenvolvimento e validação.
- Se a API tiver autenticação, configure `basic_auth` ou `bearer_token` no `prometheus.yml` ou permita acesso ao endpoint de métricas apenas pela rede interna do cluster.

- Métricas importantes expostas pela aplicação:
  - `contaazul_force_refresh_throttled_total`
  - `contaazul_last_refresh_timestamp`
  - `contaazul_token_expires_at`


Forçar o Refresh de Token via API (Admin):

```bash
curl -X POST http://localhost:8085/api/financeiro/contaazul/force-refresh \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json"
```

### Backfill — sincronizar recibos (últimos 30 dias)

Em cenários em que a API da ContaAzul oscila ou houve perda de processamento, use o endpoint de backfill para reprocessar recibos históricos (padrão: últimos 30 dias).

Exemplo (Admin):

```bash
curl -X POST http://localhost:8085/api/financeiro/backfill \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json"
```

---

## Triagem de Alerta — ContaAzulForceRefreshThrottled

Playbook para SRE/Dev quando o alerta `ContaAzulForceRefreshThrottled` disparar.

1) Identificação
- Verificar alerta no Alertmanager/Slack e anotar timestamp.

2) Consultar métricas no Prometheus:

```promql
increase(contaazul_force_refresh_throttled_total[5m])
```

3) Logs da API — caso Kubernetes:

```bash
kubectl logs -l app=inovare-ti -n namespace --tail=200 | grep "ContaAzul force-refresh"
```

4) Ações imediatas
- Se usuário legítimo e volume pequeno: avisar e aguardar.
- Se automação/ataque: bloquear IP no WAF/Ingress e ajustar rate-limiter.

5) Mitigação
- Ajustar TTL/threshold do `RedisRateLimiter` (`FORCE_REFRESH_COOLDOWN_MS`) se necessário.
- Se falso positivo por deploy/testes, revisar pipelines/CI.

6) Auditoria
- Registrar o incidente com logs e métricas para follow-up.

### Triagem de Alerta — Falha na Captura de Recibo (20 tentativas)

Playbook para operador quando o alerta indicar repetidas falhas na captura do recibo (sistema realizou 20 tentativas):

1) Identificação
- Verifique o alerta no Alertmanager/Slack e anote o timestamp e o payload do alerta (procure por `baixaId` ou `saleId`).

2) Verificação no ERP
- Localize a `baixaId` informada no painel financeiro do ERP.
- Confirme se existe um anexo associado à baixa e se o tipo do anexo é `RECIBO` ou `RECIBO_DIGITAL`.

3) Se o anexo estiver presente
- Verifique se o PDF está íntegro/baixável. Se estiver válido, reprocessar ou marcar manualmente como processado no sistema operacional, conforme procedimento local.

4) Se o anexo NÃO estiver presente
- Tente acionar o endpoint de backfill (reprocessamento) via API admin para forçar recuperação de recibos históricos:

```bash
curl -X POST http://localhost:8085/api/financeiro/backfill \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json"
```

- Se o backfill não gerar o PDF automaticamente, anexe manualmente o recibo no ERP (ou use a interface administrativa do sistema para anexar o PDF), certificando-se de que o tipo do anexo seja `RECIBO` ou `RECIBO_DIGITAL`.

5) Notificação e Escalonamento
- Registre as ações tomadas no ticket/Slack e escale para o time financeiro se o problema persistir após o backfill e tentativa de anexo manual.

### Simulação de Alerta Crítico (validação do fluxo de notificações)

Há um endpoint temporário utilizado para validar o fluxo end-to-end (alerta → evento → listener → Discord). Este endpoint deve ser usado apenas em ambientes de teste e removido após validação.

- **Endpoint:** `POST /api/financeiro/test/simulate-critical-alert`
- **Autorização:** requer `ROLE_ADMIN` (Bearer JWT).

Exemplo de uso (substitua `$TOKEN` por um JWT de admin):

```bash
curl -X POST http://localhost:8085/api/financeiro/test/simulate-critical-alert \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json"
```

O endpoint registra um alerta do tipo `FINANCEIRO_RECEIPT_CRITICAL` com severidade `HIGH` e publica o evento; o `AlertEventListener` encaminha a notificação para o webhook configurado (`DISCORD_WEBHOOK_URL`). Antes de executar, verifique que a variável de ambiente `DISCORD_WEBHOOK_URL` esteja preenchida e que a API esteja rodando e acessível.

Remova ou proteja este endpoint após os testes para evitar uso indevido em produção.

---

## Throttling e Rate-limiter

- Implementado fallback in-memory por `principal:ip` e opcional Redis distribuído (`RedisRateLimiter`).
- TTL padrão: 60s (configurável).
- Métrica de throttles: `contaazul_force_refresh_throttled_total`.

Recomendações:
- Provisionar Redis em staging/prod e habilitar limiter distribuído.
- Considerar Bucket4j+Redis para controle de burst se necessário.

---

## Operações Rápidas e Checklist

1. Verificar health endpoint da API e Prometheus.
2. Checar `contaazul_oauth_tokens` (query acima).
3. Inspecionar logs de `ContaAzulAutomationService` e `ContaAzulClient`.
4. Se necessário, executar procedimento de re-autorização.

---

## Contatos e Responsáveis

- Time Plataforma: slack `#plataforma`
- Devs responsáveis: `@lead-dev` (adapte conforme o canal interno)
