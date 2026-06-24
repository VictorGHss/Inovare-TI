# Guia de Observabilidade do Blip: Grafana e Alertas de Produção

Este guia descreve as configurações técnicas recomendadas para provisionamento do Dashboard no Grafana e definições de regras de alertas críticos no Prometheus Alertmanager, baseados nas métricas coletadas pelo componente de observabilidade de falhas de entrega do Blip (`blip_delivery_failures_total`).

---

## 1. Métricas e Tags Disponíveis

A aplicação expõe a métrica no formato Prometheus sob o nome:
* `blip_delivery_failures_total_total` (Contador gerado pelo Micrometer)

### Tags Associadas:
* `error_code`: Código numérico de erro retornado pela Meta (ex: `131042`, `131026`).
* `reason_category`: Categoria simplificada interna da aplicação.
* `category`: Classificação detalhada do domínio do erro (gerada pelo `BlipErrorClassifier`):
  * `ERRO_INTERNO_OU_GATEWAY`
  * `RATE_LIMIT_EXCEDIDO`
  * `PARAMETRO_INVALIDO_TEMPLATE`
  * `DESTINATARIO_INVALIDO_WHATSAPP`
  * `CONTA_BUSINESS_BLOQUEADA`
  * `MEDIA_OU_TIPO_INCOMPATIVEL`
  * `EXPERIMENTO_META_BLOQUEADO`
  * `CONFLITO_ATENDIMENTO_ATIVO`
  * `FALHA_DESCONHECIDA`

---

## 2. Consultas PromQL para Painéis do Grafana

### A. Taxa de Erro por Categoria (Painel Time Series)
Mapeia a taxa de erro por segundo calculada em janelas móveis de 5 minutos, permitindo visualizar tendências de falhas.
```promql
sum(rate(blip_delivery_failures_total_total[5m])) by (category)
```
* **Visualização recomendada:** Time Series (Linhas).
* **Unidade:** ops/sec.

### B. Painel de Status da Conta Business (Painel Stat/Gauge)
Monitora o volume acumulado de bloqueios ou suspensões na conta de negócios da Meta (WABA).
```promql
sum(increase(blip_delivery_failures_total_total{category="CONTA_BUSINESS_BLOQUEADA"}[1h]))
```
* **Visualização recomendada:** Stat ou Bar Gauge.
* **Configuração de Cores (Thresholds):**
  * `0`: Verde (Sem problemas)
  * `> 0`: Vermelho Piscante (Bloqueio detectado - Ação urgente necessária)

---

## 3. Configurações de Alertas no Prometheus

Abaixo está o arquivo descritivo contendo as regras de disparo para o Prometheus Alertmanager.

```yaml
groups:
  - name: blip-delivery-alerts
    rules:
      # -----------------------------------------------------------------------
      # ALERTA CRÍTICO: Bloqueio de Conta Business ou Esgotamento de Rate Limits
      # -----------------------------------------------------------------------
      - alert: BlipCriticalDeliveryFailure
        expr: sum(increase(blip_delivery_failures_total_total{category=~"CONTA_BUSINESS_BLOQUEADA|RATE_LIMIT_EXCEDIDO"}[1m])) > 3
        for: 0m # Disparo imediato (sem tempo de espera por gravidade técnica)
        labels:
          severity: critical
          tier: integrations
        annotations:
          summary: "Falha Crítica de Entrega no Blip: {{ $value }} ocorrências"
          description: "Detectado bloqueio da conta empresarial WhatsApp (WABA) ou esgotamento severo de Rate Limit (Meta/Blip) nos últimos 60 segundos. O envio de confirmações de consultas está paralisado."
          action_plan: "1. Verificar o painel Meta Business Suite para checar pendências financeiras ou suspensões de segurança. 2. Avaliar se o volume de requisições excedeu as cotas limites contratadas com a Blip."

      # -----------------------------------------------------------------------
      # ALERTA WARNING: Alto índice de destinatários inválidos (Erro Humano)
      # -----------------------------------------------------------------------
      - alert: BlipHighInvalidRecipientsWarning
        expr: sum(increase(blip_delivery_failures_total_total{category="DESTINATARIO_INVALIDO_WHATSAPP"}[10m])) > 20
        for: 5m
        labels:
          severity: warning
          tier: operational
        annotations:
          summary: "Pico de Números Inválidos no WhatsApp: {{ $value }} falhas"
          description: "Detectada uma alta taxa de números de telefone inválidos ou não cadastrados no WhatsApp nas confirmações disparadas nos últimos 10 minutos. Isso indica falhas de digitação ou cadastro desatualizado no Feegow."
          action_plan: "1. Ajustar os números de telefone com DDI e DDD no sistema Feegow. 2. Verificar o processo de higienização de cadastros automáticos."
