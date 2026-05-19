# Guia de Configuração de Alertas do Monitoramento (Prometheus & Discord)

Este documento descreve passo a passo como configurar o ecossistema de monitoramento e alertas para o Inovare-TI, permitindo que o time de TI seja notificado em tempo real no **Discord** sempre que o Circuit Breaker da Feegow entrar no estado **OPEN** (Aberto).

---

## 1. Configurando o Webhook no Discord

Para que o Alertmanager consiga enviar alertas formatados ao Discord, usaremos a compatibilidade nativa do Discord com payloads do Slack (sufixo `/slack`).

1. Abra o **Discord** e navegue até o servidor desejado.
2. Acesse as **Configurações do Canal** onde deseja receber os alertas (ex: `#ti-alerts` ou `#ti-channel`).
3. Clique na aba **Integrações** na barra lateral esquerda.
4. Clique em **Webhooks** e em **Criar Webhook** (ou "Novo Webhook").
5. Defina um nome descritivo (ex: `Prometheus Alertmanager`) e selecione o canal correto.
6. Clique em **Copiar URL do Webhook**.
7. **Importante para Compatibilidade (Estratégia Slack):**
   A URL copiada terá o formato:
   `https://discord.com/api/webhooks/1234567890/ABCDEFG_HIJKLMNOP`
   
   Você **DEVE** adicionar o sufixo `/slack` ao final dessa URL para utilizá-la no arquivo `alertmanager.yml`:
   `https://discord.com/api/webhooks/1234567890/ABCDEFG_HIJKLMNOP/slack`

---

## 2. Apontando o Prometheus para o Arquivo de Regras

Para que o Prometheus carregue a regra de alerta que criamos (`prometheus.rules.yml`), precisamos editar o arquivo principal de configuração do Prometheus (normalmente `prometheus.yml`).

1. No seu arquivo `prometheus.yml` (localizado em `./docs/prometheus/prometheus.yml` no projeto), verifique ou adicione a referência para o arquivo de regras sob a seção `rule_files`:

   ```yaml
   rule_files:
     - "alert.rules.yml"        # Regras gerais ja existentes
     - "prometheus.rules.yml"   # Regra de alertas do disjuntor Feegow criada
   ```

2. Certifique-se também de que o Prometheus está configurado para encaminhar os alertas gerados para a instância do Alertmanager:

   ```yaml
   alerting:
     alertmanagers:
       - static_configs:
           - targets:
               - "alertmanager:9093" # Servico ou host onde o Alertmanager esta rodando
   ```

---

## 3. Rodando o Alertmanager

O Alertmanager é responsável por agrupar, silenciar e enviar as notificações geradas pelo Prometheus aos receptores finais (neste caso, o Discord).

Para subir o Alertmanager usando Docker Compose, você pode adicionar a seguinte definição de serviço ao seu arquivo `docker-compose.yml`:

```yaml
  alertmanager:
    image: prom/alertmanager:latest
    container_name: inovareti_alertmanager
    restart: always
    ports:
      - "9093:9093"
    volumes:
      - ./api/alertmanager.yml:/etc/alertmanager/alertmanager.yml:ro
    networks:
      - inovare_network
```

No arquivo `api/alertmanager.yml` gerado na raiz da API, substitua a string `https://discord.com/api/webhooks/YOUR_DISCORD_WEBHOOK_ID/YOUR_DISCORD_WEBHOOK_TOKEN/slack` pela URL real do seu Webhook do Discord copiada no **Passo 1** (mantendo o sufixo `/slack`).

---

## 4. Como Simular uma Falha e Testar o Alerta

Para garantir que o fluxo de ponta a ponta está funcionando (Actuator ➔ Prometheus ➔ Alertmanager ➔ Discord), siga o roteiro de testes abaixo:

### Passo 4.1: Induzir Erros na Comunicação com a Feegow
O disjuntor da Feegow (`feegowApiCircuit`) é acionado se a taxa de falhas ultrapassar o limite configurado. Podemos simular isso alterando a chave da API para invalidar as requisições:

1. Localize o arquivo `.env` na raiz do projeto `Inovare-TI`.
2. Altere o valor de `APP_FEEGOW_API_KEY` para uma chave inválida (ex: `CHAVE_INVALIDA_TESTE_ALERTAS`).
3. Reinicie a API (`docker compose restart api` ou reinicie sua aplicação local).

### Passo 4.2: Acionar o Circuit Breaker (Taxa de Falhas)
Conforme as propriedades configuradas no `application.properties`:
- `sliding-window-size=10`
- `minimum-number-of-calls=5`
- `failure-rate-threshold=50`

Para forçar a mudança de estado do disjuntor para `OPEN`, precisamos realizar pelo menos **5 requisições** que falhem:
1. Abra um navegador ou utilize uma ferramenta como Insomnia/Postman.
2. Faça pelo menos 5 requisições consecutivas a um endpoint que chame os serviços da Feegow (ex: endpoints de listagem de profissionais ou agendamentos).
3. As requisições falharão com HTTP 401/500 devido à API Key inválida.
4. Após o 5º erro, o disjuntor entrará no estado **OPEN**.

### Passo 4.3: Monitorar a Mudança de Estado no Prometheus
1. Acesse o console do Prometheus (geralmente em `http://localhost:9095`).
2. Pesquise pela métrica:
   ```promql
   resilience4j_circuitbreaker_state{state="open"}
   ```
3. A métrica para a tag `state="open"` deve estar com o valor `1` (indicando ativo).
4. Vá para a aba **Alerts** no painel superior do Prometheus.
5. O alerta `FeegowCircuitBreakerOpen` estará listado como **PENDING** (Pendente) na cor amarela.

### Passo 4.4: Recebimento do Alerta no Discord
1. Aguarde **1 minuto** (tempo configurado na regra `for: 1m` para evitar falsos positivos).
2. O estado do alerta mudará para **FIRING** (Disparando) na cor vermelha.
3. Verifique o seu canal do Discord. O bot do Alertmanager terá enviado um card formatado e amigável em **PORTUGUÊS**:
   
   > 🚨 **[Inovare-TI Monitoramento] Alerta Operacional: Disjuntor Feegow Aberto (Chamadas Interrompidas)**
   > 
   > **Status do Alerta:** `FIRING`
   > **Gravidade:** `CRITICAL`
   > **Serviço:** `inovareti-api`
   > 
   > **Detalhes:**
   > O disjuntor da API Feegow ('feegowApiCircuit') entrou no estado 'OPEN' (aberto). As chamadas de integração externa estão temporariamente suspensas para evitar sobrecarga e falhas cascata.
   > 
   > **Horário do Disparo:** `19/05/2026 15:30:00`
   > 
   > ---
   > ⚠️ **Ação Recomendada:** Verificar a estabilidade da API Feegow ou os logs de conexão de rede da API do Inovare-TI para restabelecer a integração.

### Passo 4.5: Testar a Resolução do Alerta (Auto-Healing)
1. Restaure a API Key real no seu arquivo `.env`.
2. Reinicie a API.
3. Aguarde o tempo do estado `open` passar (`wait-duration-in-open-state=45s`) para que o disjuntor entre em `HALF-OPEN`.
4. Faça requisições bem-sucedidas aos endpoints da Feegow para que o disjuntor retorne para `CLOSED`.
5. O Prometheus detectará que a métrica retornou a `0` e o Alertmanager enviará automaticamente uma mensagem informando que o alerta foi **RESOLVED** (Resolvido), registrando o horário da resolução.
