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

### 🔲 Fase 11 — Documentação e Deploy (Em andamento)
- Documentar endpoints restantes, atualizar CI/CD e validar playbook de rollback.

---

## Backlog e Sugestões Prioritárias

1. Validar e aplicar regra de alerta Prometheus para `contaazul_force_refresh_throttled_total`.
2. Habilitar rate-limiter distribuído (Redis) para `POST /financeiro/contaazul/force-refresh`.
3. Criar PR com checklist: throttling, métricas, testes e manifestos de monitoramento.
4. Provisionar Redis em staging e validar `RedisRateLimiter`.

### Observability & Alerting (recomendações)

- Métricas essenciais: `contaazul_force_refresh_throttled_total`, `contaazul_token_last_refresh`, `contaazul_token_expires_seconds`.
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
