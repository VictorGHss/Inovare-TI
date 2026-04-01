# Histórico do Projeto & Roadmap — Inovare TI

Este documento consolida o histórico de fases, tarefas pendentes, sugestões de projeto, alterações de segurança e auditoria de mensagens visíveis ao usuário.

---

## Roadmap e Fases (resumo)

### ✅ Fase 1 — Entidades e Repositórios (Concluída)
- Entidades: `Sector`, `User`, `TicketCategory`, `ItemCategory`, etc.

### ✅ Fase 2 — CRUD Básico + Segurança Inicial (Concluída)
- `SecurityConfig`, `GlobalExceptionHandler`, `NotFoundException`, `ConflictException`.

### ✅ Fase 3 — Inventário (Concluída)
- `Item`, `StockBatch`, DTOs e controllers relacionados.

### ✅ Fase 4 — Motor de Chamados + Baixa de Estoque (Concluída)
- Fluxo completo de tickets e baixa atômica de estoque.

### ✅ Fase 5 — Autenticação JWT (Concluída)
- `POST /api/auth/login`, filtro JWT e integração com 2FA.

### ✅ Fase 6 — Módulo de Chamados Avançado (Concluída)
- Uploads, anexos, gestão avançada e cofre seguro.

### ✅ Fase 7 — Integrações e Extras (Concluída)
- 2FA via TOTP, recuperação via Discord, ContaAzul integrado.

### ✅ Fase 8 — Auditoria e Compliance (Concluída)
- `audit_logs`, `AuditEvent`, `AuditEventListener` e endpoint de consulta.

### ✅ Fase 9 — Otimização Mobile (PWA) (Concluída)

### ✅ Fase 10 — Dashboard Premium (Concluída)

### ✅ Módulo Financeiro e Relatórios (Concluído — 01/04/2026)

- Implementação de lógica FIFO (First-In, First-Out) real para saídas de inventário: os lotes agora são consumidos em ordem cronológica de entrada, garantindo coerência contábil, rastreabilidade e consistência das quantidades baixadas.
- Refatoração completa do `ReportService`: migração para `PdfPTable` com layout profissional, suporte a fontes Helvetica/Helvetica-Bold, inclusão do logotipo empresarial, cabeçalho com a cor da marca (`#feb56c`), alinhamento numérico à direita e linha de TOTAL no rodapé do relatório.
- Correção de filtros de data e tratamento de fuso: padronização de conversões `America/Sao_Paulo` → UTC e inclusão do fim-do-dia nas consultas para garantir captura completa das transações do dia corrente.
- Integração de Rich Embeds nas notificações Discord com identidade visual da Inovare (cor `#feb56c`), incluindo sumário financeiro e links/contexto nos embeds para facilitar triagem.
- Limpeza e modernização de código: remoção de logs de depuração usados durante testes de fuso horário, higienização de strings para geração de PDFs (`sanitizeForPdf`), e modernização de chamadas de URL (`URI.create(...).toURL()`). Avisos de compilação relativos a unboxing/imports foram resolvidos.

Observação: o botão `Testar Agora` (`trigger-test`) na interface web foi preservado e permanece funcional para validações manuais rápidas.

### ✅ Observability & Rate-limiting (Concluído — 01/04/2026)
- Redis: o serviço `redis` foi adicionado ao `docker-compose.yml` e integrado via variáveis de ambiente (`SPRING_REDIS_HOST`, `SPRING_REDIS_PORT`). Foi implementado o componente `RedisRateLimiter` (comportamento condicional com `@ConditionalOnBean(StringRedisTemplate.class)`) utilizado pelo endpoint `POST /financeiro/contaazul/force-refresh` para aplicar rate-limiting distribuído. O algoritmo usa `INCR` + `EXPIRE` para contagem atômica na janela; o controller implementa fallback em memória quando o Redis não está disponível.
- Prometheus & Micrometer: as dependências `micrometer-registry-prometheus` e `spring-boot-starter-actuator` foram adicionadas ao projeto. O endpoint de métricas está exposto em `/api/actuator/prometheus` (configuração: `management.endpoints.web.exposure.include=health,metrics,prometheus`). O componente `ContaAzulMetrics` registra um contador de throttles (`contaazul.force.refresh.throttled` — exposto como `contaazul_force_refresh_throttled_total` no Prometheus) e gauges para timestamps/expiração do token (`contaazul_last_refresh_timestamp`, `contaazul_token_expires_at`), atualizados periodicamente via `@Scheduled`.
- Regras de alerta: existe uma regra Prometheus dedicada em `docs/prometheus/contaazul-throttle-alerts.yml` (alerta `ContaAzulForceRefreshThrottled`, expressão `increase(contaazul_force_refresh_throttled_total[5m]) > 0`).
- Observação operacional: o `docker-compose.yml` provisiona o `redis`, porém o Prometheus não está incluído no compose e deve ser provisionado separadamente (por exemplo via Helm/Kubernetes ou instância dedicada) para coleta e alerting em staging/produção.

### 🔲 Fase 11 — Documentação e Deploy (Em andamento)
- Documentar endpoints restantes, atualizar CI/CD e validar playbook de rollback.

---

## Backlog e Sugestões Prioritárias

- Itens relacionados a Redis e Prometheus foram concluídos e movidos para a seção "✅ Observability & Rate-limiting (Concluído — 01/04/2026)" acima.
- Tarefas pendentes (prioritárias): criar PR com checklist de integração contínua e validações automatizadas de monitoramento (CI), revisão de thresholds em produção.

### Observability & Alerting (recomendações)

- Métricas essenciais: `contaazul_force_refresh_throttled_total` (counter), `contaazul_last_refresh_timestamp` (gauge), `contaazul_token_expires_at` (gauge).
- Alertas: curto prazo (5m) e médio prazo (30m) com thresholds claros.

---

## Alterações de Segurança (resumo)

- Ajustes em `SecurityConfig.java` para liberar rotas com/sem prefixo `/api` (ex.: `/auth/**`, `/financeiro/contaazul/**`).
- `SecurityFilter.shouldNotFilter` atualizado para pular filtro JWT em `/auth/` e rotas ContaAzul.

Teste rápido:

1. Reiniciar aplicação.
2. Validar `POST https://<host>/api/auth/login` retorna 200/401 ao invés de 403.

---

## Auditoria de Mensagens Visíveis ao Usuário

- Política: todas as mensagens exibidas ao usuário devem estar em português.
- Verificar strings literais em `src/main/resources` e consolidar em `messages.properties` se necessário.

Exemplo (retido):
- `ContaAzulController.forceRefresh` retorna mensagens em português e foi mantido.

---

## Notas Finais

- Este arquivo reúne o histórico e sugestões — é recomendado que seja revisado periodicamente e vinculado a issues no tracker do time.
