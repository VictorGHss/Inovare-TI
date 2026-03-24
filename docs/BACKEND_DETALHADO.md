# Documentação Backend Detalhado — Inovare-TI

Resumo técnico e pontos operacionais relevantes para manutenção e evolução do módulo `api`.

## Visão geral
- Linguagem: Java 21
- Framework: Spring Boot 4 (WebMVC, Data JPA, Security, Actuator)
- Observability: Micrometer + Prometheus
- Banco de dados: PostgreSQL (Flyway para migrations)
- Build: Maven

## Pacotes principais
- `br.dev.ctrls.inovareti.domain.financeiro.contaazul` — integração ContaAzul: clients, serviços de token, controlador, métricas, health indicator.
- `br.dev.ctrls.inovareti.config` — configuração de segurança, filtros e beans centrais.
- `br.dev.ctrls.inovareti.infra` — implementações de armazenamento local, email e utilitários.

## Endpoints relevantes (resumido)
- `GET /financeiro/contaazul/authorize` — inicia fluxo OAuth (público).
- `GET /financeiro/contaazul/callback` — callback OAuth (público).
- `GET /financeiro/contaazul/status` — status de autorização (admin).
- `POST /financeiro/contaazul/force-refresh` — forçar refresh do token (admin, throttled).

## Throttling e rate-limiter
- Implementado fallback in-memory por `principal:ip` e opção Redis distribuído (`RedisRateLimiter`).
- TTL padrão: 60s. Persiste chave no Redis via `StringRedisTemplate` com `setIfAbsent(key, 1, ttl)`.

## Métricas e alertas
- Métrica: `contaazul_force_refresh_throttled_total` — contador incrementado quando o `force-refresh` é bloqueado.
- Arquivo de regra Prometheus em `docs/monitoring/helm/contaazul-prometheusrule.yaml`.

## Configurações importantes
- `contaazul.redirect-uri` — URI de callback registrado no ContaAzul.
- `app.frontend.url` — URL base do frontend (usada para redirecionar após callback).
- `spring.redis.*` — habilitar para usar Redis como rate-limiter distribuído.

## Operações e runbook rápido
1. Verificar alertas em Prometheus/Alertmanager.
2. Caso throttles altos: checar logs (`ContaAzulController`); identificar usuário/IP.
3. Se falso positivo, ajustar TTL/threshold; se ataque, bloquear no WAF/ingress.

## Observações para desenvolvedores
- Mantenha mensagens visíveis ao usuário em português apenas (conforme política do projeto).
- Ao alterar regras de segurança, revise `SecurityConfig` e `SecurityFilter` para manter exclusões do OAuth.

---
Documento gerado automaticamente por assistente; atualizar conforme mudanças arquiteturais.
