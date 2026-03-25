**Visão Geral**
- **Módulo:** api/
- **Linguagem:** Java 21, Spring Boot 4
- **Propósito:** backend REST da plataforma Inovare-TI — integrações, regras de negócio, persistência, segurança e observabilidade.

**Como rodar localmente (rápido)**
- Compilar e rodar testes do módulo api:

```bash
cd api
mvn test
```

**Endpoints principais (resumo)**
- `POST /api/financeiro/contaazul/force-refresh` — força atualização do token da Conta Azul (endpoint admin).
- Actuator: `/api/actuator/health` — indicadores de saúde.
- Actuator Prometheus: `/api/actuator/prometheus` — métricas exportadas (Micrometer + Prometheus).

**Integração com Conta Azul**
- Serviço central: `ContaAzulTokenService` — obtém e renova tokens OAuth da Conta Azul.
- Repositório: `ContaAzulOAuthTokenRepository` — persiste `ContaAzulOAuthToken` com timestamps (`expiresAt`, `refreshedAt`, `updatedAt`).
- Automação: `ContaAzulAutomationService` — polling/operação assíncrona (desabilitada em testes).

**Métricas e observabilidade**
- Componente: `ContaAzulMetrics` (condicional via `app.contaazul.metrics.enabled=true`).
- Métricas expostas:
  - `contaazul_token_expires_at` (gauge) — timestamp de expiração do token.
  - `contaazul_last_refresh_timestamp` (gauge) — timestamp do último refresh.
- Recomendações: habilitar `management.endpoints.web.exposure.include=prometheus,health` no ambiente de monitoramento.

**Testes**
- Testes unitários e de integração usam H2 em memória e desabilitam agendamentos em `src/test/resources/application.properties`.
- Para executar apenas um teste específico:

```bash
cd api
mvn -Dtest=NomeDoTeste test
```

**Comentários e idioma**
- A equipe pediu que todos os comentários e textos adicionados pelo agente estejam em português. Vou revisar e traduzir todos os comentários e recursos que o agente adicionou (tests, docs e strings internas modificadas). Se você quiser, eu aplico a tradução automática nos arquivos alterados pelo agente primeiro, e depois faço revisão manual.

**Próximos passos sugeridos**
- Revisão de segurança (roles e `@PreAuthorize`) nos endpoints administrativos.
- Tradução dos comentários gerados pelo agente para português (vou iniciar agora nos arquivos de teste e documentação que criei/editei).
- Opcional: adicionar um profile de testes CI que libera o endpoint `/actuator/prometheus` para um teste end-to-end via HTTP.

--
Documentação gerada em 24/03/2026 — se quiser, atualizo outros arquivos (`ARCHITECTURE.md`, `API_DOCS.md`) para incluir trechos desta página.
