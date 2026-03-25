# Sugestões para o projeto Inovare-TI

Este documento resume recomendações prioritárias, ações práticas e próximos passos para melhorar confiabilidade, segurança, observabilidade e manutenibilidade da integração ContaAzul e da aplicação como um todo.

## 1. Prioridades imediatas (alta)
- Validar e colocar em produção a regra de alerta Prometheus para `contaazul_force_refresh_throttled_total` e conectar ao canal de incidentes.
- Habilitar rate-limiter distribuído (Redis) em staging/prod para proteger `POST /financeiro/contaazul/force-refresh`.
- Criar PR consolidando: throttling, métricas, testes, manifestos de monitoramento e documentação; incluir checklist de verificação.

## 2. Observability e alerting
- Métricas obrigatórias:
  - `contaazul_force_refresh_throttled_total` (contador de 429)
  - `contaazul_token_last_refresh` (timestamp)
  - `contaazul_token_expires_seconds` (seconds to expiry)
  - (Opcional) histogramas de latência das chamadas externas a ContaAzul
- Alerting sugerido:
  - Curto prazo (5m): `increase(contaazul_force_refresh_throttled_total[5m]) > 0` → `warning` (triagem)
  - Médio prazo (30m): aumento persistente → `critical` (investigação)
- Dashboards recomendados:
  - Visão rápida: número de throttles, taxa de erros 5xx do client ContaAzul, latência 95p, timestamp do último refresh.

## 3. Rate-limiting e Resiliência
- Implementação atual: fallback in-memory e componente `RedisRateLimiter` (SETNX + TTL) como opção distribuída.
- Recomendações práticas:
  - Provisionar Redis gerenciado em staging/prod e habilitar autenticação; usar TLS quando disponível.
  - Evoluir para token-bucket (Bucket4j + Redis) se for necessário controlar bursts e permitir policies por usuário/conta.
  - Enriquecer logs de throttle e exportar para ELK/Datadog (quem, ip, user-agent, timestamp) para auditoria.

## 4. Segurança
- Verificar que apenas `/financeiro/contaazul/authorize` e `/financeiro/contaazul/callback` sejam públicos; exigir autenticação/role para o restante.
- Garantir que as roles e mapeamentos (JWT/OAuth) suportem `hasRole('ADMIN')` corretamente.
- Recomendação de defesa em profundidade: aplicar rate-limiting também no ingress/API Gateway (NGINX/Kong) além do limiter na aplicação.

## 5. Testes e CI
- Incluir testes e2e/contract para o client ContaAzul (fluxo OAuth) e cenários de throttling.
- Pipeline CI recomendado:
  - `build` (compilação, static analysis)
  - `test` (unit + integration)
  - `integration-e2e` (opcional, em ambiente staging)
- Adicionar métrica de cobertura para áreas críticas: refresh, throttling e tratamento de erros.

## 6. Infra e Deploy
- Configurações essenciais:
  - `spring.redis.host`, `spring.redis.port`, `spring.redis.password` (via Secret)
  - `contaazul.redirect-uri` e `app.frontend.url` como env vars configuráveis por ambiente
- Helm/monitoring:
  - Adicionar `docs/monitoring/helm/contaazul-prometheusrule.yaml` ao repo de monitoramento e validar no staging antes de prod.

## 7. Documentação e i18n
- Manter mensagens visíveis ao usuário em português.
- Atualizar README do `api` com endpoints, variáveis de ambiente obrigatórias e exemplos de uso.
- Incluir HOWTO para desenvolvedores: `docker-compose` local com Redis e instruções para rodar testes que dependem de Redis.

## 8. Operações e Playbooks
- Criar e publicar playbook de triagem para `ContaAzulForceRefreshThrottled` (já gerado em `docs/PLAYBOOK_CONTAAZUL_THROTTLE.md`).
- Incluir checklist de ações imediatas e responsáveis (SRE, Dev) no runbook.

## 9. Próximos passos sugeridos (curto prazo)
1. Validar esta proposta internamente e abrir PR com checklist de verificação (throttling, métricas, testes, manifestos, docs).
2. Provisionar Redis em staging; ajustar `application-staging.yml` e validar `RedisRateLimiter` em integração.
3. Aplicar `PrometheusRule` no repo de monitoramento e testar entrega de alertas no Alertmanager/Slack.
4. Se necessário, planejar migração para Bucket4j/Redis token-bucket para controle avançado de burst.

---
Se quiser, eu posso:
- Gerar um `values.yaml` de exemplo para habilitar Redis e as `env` necessárias (feito: `docs/values/redis-values.yaml`).
- Preparar o PR com descrição, checklist e mudanças relevantes (branch criada e empurrada: `feat/contaazul-monitoring-redis`).
- Gerar um playbook em Markdown para o canal de SRE com procedimentos de triagem (feito: `docs/PLAYBOOK_CONTAAZUL_THROTTLE.md`).

Arquivo atualizado: [docs/PROJECT_SUGESTOES.md](docs/PROJECT_SUGESTOES.md)
