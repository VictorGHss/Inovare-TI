# Guia de Configuração e Uso do Dashboard Grafana

Este documento descreve como configurar, importar e interpretar os dashboards de monitoramento do Inovare-TI no Grafana, com foco na arquitetura hexagonal, resilência dos Circuit Breakers e nas Virtual Threads do Java 21.

---

## 1. Pré-requisitos

Certifique-se de que os seguintes serviços estão rodando via `docker compose up -d`:
- **API** (`inovareti_api`) — exportando métricas via `/api/actuator/prometheus`
- **Prometheus** (`inovareti_prometheus`) — coletando as métricas e avaliando as regras de alerta
- **Grafana** (`inovareti_grafana`) — acessível em `http://localhost:3001`

---

## 2. Provisionamento Automático (Zero Configuração Manual)

O projeto já está configurado com **provisionamento automático** do Grafana. Isso significa que ao subir o `docker compose`, o Grafana carrega automaticamente:

| O que é provisionado | Arquivo de origem | Destino no container |
|---|---|---|
| Datasource Prometheus | `docs/grafana/provisioning/datasources/prometheus.yml` | `/etc/grafana/provisioning/datasources/` |
| Provider de Dashboards | `docs/grafana/provisioning/dashboards/dashboards.yml` | `/etc/grafana/provisioning/dashboards/` |
| Dashboard ContaAzul | `docs/grafana/dashboards/inovare_dashboard.json` | `/var/lib/grafana/dashboards/` |
| **Dashboard Resiliência** *(novo)* | `docs/grafana/dashboards/inovare_resiliencia_dashboard.json` | `/var/lib/grafana/dashboards/` |

> **Não é necessário importar manualmente** o JSON. Basta (re)iniciar o container do Grafana.

---

## 3. Importando o Dashboard Manualmente (Alternativa)

Caso precise importar o dashboard em um Grafana existente ou remoto:

1. Acesse o Grafana em `http://localhost:3001` (ou sua URL de produção).
2. No menu lateral, clique em **Dashboards** → **Import**.
3. Clique em **Upload JSON file** e selecione o arquivo `docs/grafana/dashboards/inovare_resiliencia_dashboard.json`.
4. Na tela seguinte, selecione o **Datasource Prometheus** no campo correspondente.
5. Clique em **Import**. O dashboard estará disponível imediatamente.

---

## 4. Painéis do Dashboard `Inovare-TI — Resiliência & Threads`

O dashboard `inovare_resiliencia_dashboard.json` contém os seguintes painéis:

### Painel 1: Estado dos Circuit Breakers
- **Tipo**: Stat (indicador visual por cor)
- **Métricas**: `resilience4j_circuitbreaker_state{name=~".+"}`
- **Interpretação**:
  - 🟢 `CLOSED (0)` → Saudável, chamadas fluindo normalmente
  - 🟡 `HALF-OPEN (0.5)` → Testando recuperação após período aberto
  - 🔴 `OPEN (1)` → **Disjuntor aberto, chamadas à Feegow interrompidas!**

### Painel 2: Taxa de Erros Feegow
- **Tipo**: Time Series (gráfico temporal)
- **Métricas**:
  - `resilience4j_circuitbreaker_failure_rate{name="feegowApiCircuit"}` — percentual de falha atual
  - Taxa calculada: `rate(calls_count[failed]) / rate(calls_count) * 100` — taxa por janela de 5 minutos
- **Limiar crítico**: Acima de **50%**, o disjuntor abre automaticamente (conforme configuração em `application.properties`)

### Painel 3: Uso de Virtual Threads (Java 21)
- **Tipo**: Time Series (múltiplas séries)
- **Métricas utilizadas** (todas exportadas automaticamente pelo Micrometer):

  | Métrica | Significado |
  |---|---|
  | `jvm_threads_live_threads` | Contagem total de threads vivas (inclui virtuais e de plataforma) |
  | `jvm_threads_daemon_threads` | Threads daemon (geralmente as de plataforma do Spring) |
  | `jvm_threads_peak_threads` | Pico histórico de threads desde o início da JVM |
  | `jvm_threads_states_threads{state="runnable"}` | Threads em execução ativa no momento |
  | `jvm_threads_states_threads{state="blocked"}` | Threads bloqueadas (aguardando lock — sinal de contenção) |

- **Como identificar o uso de Virtual Threads (Java 21)**:
  Com `spring.threads.virtual.enabled=true` ativado no `application.properties`, o Spring Boot passa a despachar requisições HTTP em Virtual Threads (também chamadas de "Green Threads"). A principal evidência no Grafana é:
  - `jvm_threads_live_threads` pode ter valores **muito maiores** que em aplicações tradicionais (centenas ou milhares de threads simultâneas sem aumento de CPU)
  - `jvm_threads_daemon_threads` permanece estável (são as threads de plataforma do pool interno do Spring)
  - A ausência de threads no estado `blocked` indica que as Virtual Threads estão sendo suspensas (`parked`) de forma eficiente pela JVM, sem bloquear threads de plataforma

### Painel 4: Latência de Webhooks Blip
- **Tipo**: Time Series (percentis p95 e p99)
- **Métricas**:
  - `http_server_requests_seconds_sum{uri=~"/api/webhook.*|/api/blip.*"}` — soma total das durações
  - `http_server_requests_seconds_count{uri=~"/api/webhook.*|/api/blip.*"}` — contagem das requisições
  - `histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m]))` — percentil 95 de latência
  - `histogram_quantile(0.99, rate(http_server_requests_seconds_bucket[5m]))` — percentil 99 de latência
- **Limiar recomendado**: Alertas se p95 > 500ms ou p99 > 2s

### Painel 5: Resumo de Saúde da JVM
- **Tipo**: Stat (indicadores horizontais coloridos)
- **Métricas**:
  - `jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"} * 100` — % de heap utilizado
  - `process_uptime_seconds / 3600` — uptime da aplicação em horas
  - `process_cpu_usage * 100` — percentual de CPU consumido pelo processo Java
  - `jvm_gc_pause_seconds_sum / jvm_gc_pause_seconds_count` — tempo médio de pause do Garbage Collector

---

## 5. Métricas do Micrometer para Virtual Threads (Referência Técnica)

O Spring Boot 3.x com Micrometer exporta automaticamente as seguintes métricas de threads da JVM via `/actuator/prometheus`:

```promql
# Contagem total de threads vivas na JVM
jvm_threads_live_threads

# Threads no estado daemon (threads de plataforma em background)
jvm_threads_daemon_threads

# Pico máximo de threads desde o start da JVM
jvm_threads_peak_threads

# Threads por estado (runnable, blocked, waiting, timed_waiting)
jvm_threads_states_threads{state="runnable"}
jvm_threads_states_threads{state="blocked"}
jvm_threads_states_threads{state="waiting"}
jvm_threads_states_threads{state="timed-waiting"}
```

> **Nota importante sobre Virtual Threads**: O Micrometer e a JVM ainda não distinguem explicitamente Virtual Threads de Platform Threads nos contadores acima (comportamento padrão do Java 21 LTS). A estratégia recomendada é correlacionar `jvm_threads_live_threads` alto com `process_cpu_usage` baixo — este padrão indica que a JVM está aproveitando eficientemente as Virtual Threads sem saturar a CPU.

---

## 6. Variáveis e Filtros Disponíveis no Dashboard

O dashboard possui uma variável de template **`${datasource}`** que permite alternar entre diferentes fontes de dados Prometheus sem precisar editar cada painel manualmente. Isso é útil em ambientes com múltiplos stacks (desenvolvimento, staging, produção).

---

## 7. Reconstruindo os Containers após Alterações

Após qualquer alteração nos arquivos de configuração do Grafana ou Prometheus, execute:

```bash
# Reiniciar apenas o Prometheus para recarregar as regras de alerta
docker compose restart prometheus

# Reiniciar apenas o Grafana para provisionar novos dashboards
docker compose restart grafana

# Ou reiniciar ambos de uma vez
docker compose restart prometheus grafana
```
