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

Verificações rápidas via terminal

Verificar métricas do Prometheus via terminal:

```bash
curl -u admin:admin123 http://localhost:8085/api/actuator/prometheus | grep contaazul
```

### Prometheus — Conexão e exemplo de scrape

- Observação: o Prometheus não está provisionado no `docker-compose.yml`. Em ambientes de staging/produção o coletor Prometheus deve ser implantado separadamente (por exemplo via Helm/Kubernetes ou instância dedicada) e configurado para fazer scrape do endpoint de métricas da API.

- Endpoint de métricas exposto pela aplicação: `/api/actuator/prometheus` (a aplicação define `server.servlet.context-path=/api`).

- Exemplo mínimo de configuração (`prometheus.yml`) para coletar métricas da API:

```yaml
scrape_configs:
  - job_name: 'inovare-ti'
    metrics_path: /api/actuator/prometheus
    static_configs:
      - targets: ['<API_HOST_OR_SERVICE>:8085']
    # se a API exigir autenticação, use 'basic_auth' ou 'bearer_token' conforme abaixo
    # basic_auth:
    #   username: 'prometheus'
    #   password: 'PROM_PASS'
    # ou
    # authorization: { type: Bearer, credentials: '<TOKEN>' }
```

- Kubernetes (exemplo com Service discovery): use o `metrics_path: /api/actuator/prometheus` e a service DNS interna, ex: `inovare-ti-service.namespace.svc.cluster.local:8085`.

- Segurança: por padrão o Spring Security exige autenticação para a maioria das rotas. Para permitir que o Prometheus acesse o endpoint de métricas sem autenticação, opte por uma destas abordagens:
  1. Permitir explicitamente `/actuator/prometheus` no `SecurityConfig` (apenas dentro da rede do cluster).
  2. Configurar Prometheus com `basic_auth` ou `bearer_token` para usar credenciais seguras.
  3. Executar o Prometheus em rede interna (via ServiceAccount/sidecar) para evitar exposição pública.

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
