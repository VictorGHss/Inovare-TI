# Guia do Desenvolvedor e Operações — Inovare TI

Este documento contém as instruções para configuração local do ambiente, execução de testes, envio de e-mails, relatórios em PDF e monitoramento de incidentes do ecossistema Inovare TI.

---

## 1. Configuração do Ambiente e Execução Local

### 1.1 Requisitos Mínimos
*   **Java SE Development Kit (JDK) 21:** Necessário para compilação e execução do backend.
*   **Node.js (v20 ou superior)** e **npm:** Para gerenciar dependências e rodar o frontend React.
*   **Docker** e **Docker Compose:** Para rodar o banco de dados, cache e telemetria locais.
*   **Apache Maven (3.9+):** Opcional (o projeto inclui o Maven Wrapper `./mvnw`).

### 1.2 Inicialização da Infraestrutura (Docker Compose)
A API do backend aguarda a inicialização e o estado saudável do banco de dados PostgreSQL e do cache Redis antes de concluir seu bootstrap. 
Suba os serviços locais a partir do diretório raiz:

```bash
docker compose up -d db redis prometheus
```

Esse comando inicializa:
*   **PostgreSQL (`inovareti_db`):** Mapeado na porta local `5436:5432`, persistido no volume `postgres_data`.
*   **Redis (`inovareti_redis`):** Mapeado na porta local `6380:6379`.
*   **Prometheus (`inovareti_prometheus`):** Mapeado na porta local `9095:9090` para telemetria.

### 1.3 Parametrização dos Arquivos `.env`
As credenciais e integrações locais são geridas por variáveis de ambiente.
1. Copie o arquivo modelo da raiz e popule as chaves no arquivo `.env`:
   ```bash
   cp .env.example .env
   ```
2. Repita o processo na subpasta `api/`, garantindo que exista um arquivo `.env` preenchido na pasta de execução do Java.

*Parâmetros Críticos do `.env`:*
*   `POSTGRES_PASSWORD`: Senha de acesso ao banco (deve corresponder à configurada no Compose).
*   `JWT_SECRET`: Chave secreta de no mínimo 32 caracteres para assinatura dos tokens JWT.
*   `VAULT_ENCRYPTION_KEY`: Chave simétrica Base64 para criptografia dos dados do Vault (AES-GCM).
*   `SPRING_REDIS_HOST` e `SPRING_REDIS_PORT`: Conexão para o Redis (em ambiente local, use `localhost` e a porta mapeada `6380`).

---

## 2. Compilação, Testes e Inicialização das Camadas

### 2.1 Backend (API Spring Boot Java 21)
O código principal está localizado na pasta `api/`.

*   **Compilar o Projeto e Rodar Testes de Integração:**
    ```bash
    cd api
    ./mvnw clean test
    ```
*   **Executar a API Localmente (Porta 8085):**
    ```bash
    ./mvnw spring-boot:run
    ```

### 2.2 Frontend (React + Vite + TypeScript)
O código visual do sistema está na pasta `front/`.

*   **Instalar Dependências:**
    ```bash
    cd front
    npm install
    ```
*   **Iniciar o Servidor de Desenvolvimento (Porta 5173):**
    ```bash
    npm run dev
    ```

---

## 3. Configurações Globais do Backend

### 3.1 Timezone e Fuso Horário (America/Sao_Paulo)
O fuso horário padrão do sistema é definido como o de Brasília para evitar divergências na persistência e na geração de relatórios.
1. **Configuração da JVM (`InovareTiApplication.java`):**
   ```java
   @PostConstruct
   public void init() {
       TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));
   }
   ```
2. **Propriedades casadas no `application.properties`:**
   ```properties
   spring.jpa.properties.hibernate.jdbc.time_zone=America/Sao_Paulo
   spring.jackson.time-zone=America/Sao_Paulo
   ```

### 3.2 Manipulação de Erros — Padrão RFC 7807
O backend adota o `@RestControllerAdvice` na classe `GlobalExceptionHandler` para formatar e normalizar respostas de erro seguindo a especificação **RFC 7807 (Problem Details)**:

| Exceção Backend | Status HTTP | Código Recomendado | Descrição |
|-----------------|-------------|--------------------|-----------|
| `MethodArgumentNotValid` | `400 Bad Request` | `ERR_VALIDATION_FAILED` | Erro de validação nos campos do payload |
| `NotFoundException` | `404 Not Found` | `ERR_RESOURCE_NOT_FOUND` | Recurso solicitado não existe no banco |
| `ConflictException` | `409 Conflict` | `ERR_RESOURCE_CONFLICT` | Violação de unicidade ou estado concorrente |
| `IllegalStateException`| `422 Unprocessable`| `ERR_BUSINESS_RULE` | Violação de regra de negócio da aplicação |
| `AccessDeniedException`| `403 Forbidden` | `ERR_ACCESS_DENIED` | Usuário autenticado mas sem permissão (Role) |
| `MaxUploadSizeExceeded`| `413 Payload Too Large`| `ERR_UPLOAD_LIMIT` | Upload excede o tamanho configurado (5MB) |
| `SQLGrammarException` | `500 Internal Error` | `ERR_DATABASE_FAILURE` | Exceção de persistência mitigada para não vazar a DDL física |

### 3.3 SMTP de Cobrança e Modo de Teste
O envio de recibos e e-mails financeiros utiliza `spring-boot-starter-mail` (gerido pela classe `FinanceEmailService`).
*   **Modo de Teste Financeiro:** Quando `APP_FINANCEIRO_TEST_MODE=true`, todos os e-mails disparados pelo sistema (independente do e-mail do cliente cadastrado no ERP) são redirecionados para o endereço cadastrado em `APP_FINANCEIRO_DEV_EMAIL`, contendo o marcador técnico `[TESTE]` no assunto. Isso evita disparos acidentais para e-mails reais de clientes.

### 3.4 Geração e Layout de Relatórios em PDF (OpenPDF)
Para a exportação de relatórios corporativos, utiliza-se a biblioteca OpenPDF.
*   **Formatação:** Utiliza `PdfPTable` estruturando colunas com alinhamentos coerentes (valores financeiros e numéricos alinhados à direita, textos à esquerda).
*   **Identidade Visual:** Adota a cor primária da marca Inovare (`#feb56c`), fontes Helvetica-Bold em branco para contraste, e renderização dinâmica do logotipo empresarial (`src/main/resources/images/logo.png`).

---

## 4. Monitoramento e Schedulers (SRE)

### 4.1 Stack de Observabilidade Local
*   **Prometheus:** Disponível em `http://localhost:9095` (porta interna `9090`).
*   **Grafana:** Disponível em `http://localhost:3001` (porta interna `3000`). O Grafana está pré-configurado com a variável `GF_SECURITY_ALLOW_EMBEDDING=true` para permitir a exibição de gráficos e telemetria diretamente no frontend.
*   **Endpoint de Coleta da API:** `/api/actuator/prometheus` (métricas do Micrometer).

### 4.2 Cronograma de Agendamentos e Schedulers
*   **Automação ContaAzul:** Polling de baixas e processamento automático de recibos executado periodicamente.
*   **Weekly Digest Scheduler (`WeeklyDigestScheduler.java`):** Configurado com `@Scheduled(cron = "0 0 17 * * FRI")` (toda sexta-feira às 17h). Consolida métricas de chamados resolvidos, conformidade de SLA e envia um resumo técnico no Discord.

---

## 5. Runbooks Técnicos de Operações

### 5.1 Runbook: Re-autorização Manual da ContaAzul
Se os tokens OAuth2 forem revogados ou corrompidos na tabela `contaazul_oauth_tokens`:
1. Obtenha um token JWT de administrador:
   ```bash
   curl -s -X POST http://localhost:8085/api/auth/login \
     -H "Content-Type: application/json" \
     -d '{"email":"admin@inovare.med.br","password":"admin123"}'
   ```
2. Salve o token retornado na variável `$TOKEN` e consulte o status da integração:
   ```bash
   curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8085/api/financeiro/contaazul/status
   ```
3. Se o retorno for `authorized: false`, acesse o painel administrativo da aplicação (Menu Financeiro -> ContaAzul -> Conectar) para conceder permissão na URL do ERP parceiro.
4. Caso precise expurgar os tokens antigos via banco de dados para forçar nova autorização limpa:
   ```sql
   DELETE FROM contaazul_oauth_tokens;
   ```

### 5.2 Runbook: Alerta de Excesso de Tentativas de Token (`ContaAzulForceRefreshThrottled`)
Este alerta dispara quando há tentativas consecutivas de atualização manual de tokens no banco, ultrapassando os limites do `RedisRateLimiter`.
1. Acesse o Prometheus (`http://localhost:9095`) e avalie o volume de bloqueios:
   ```promql
   increase(contaazul_force_refresh_throttled_total[5m])
   ```
2. Analise os logs da API em busca de acessos recorrentes no endpoint:
   ```bash
   docker logs inovareti_api --tail=100 | grep "ContaAzul force-refresh"
   ```
3. **Mitigação:** Se for devido a cliques excessivos de usuários no frontend, oriente a equipe a aguardar o período de cooldown. Se o comportamento persistir por instabilidade na rede ou ataque, valide a regra do rate limit (`FORCE_REFRESH_COOLDOWN_MS`) ou restrinja o IP na borda do Cloudflare WAF.

### 5.3 Runbook: Alerta de Falha na Captura de Recibo (20 Tentativas Excedidas)
A captura do PDF do recibo emitido na ContaAzul ocorre de forma assíncrona. Se falhar por 20 tentativas consecutivas:
1. Obtenha o ID da baixa/venda (`baixaId` ou `saleId`) no corpo do alerta do Discord.
2. Acesse o ERP ContaAzul e confirme se a baixa de fato possui um arquivo anexo válido do tipo recibo.
3. Dispare o reprocessamento histórico (Backfill) via API com credenciais de administrador para tentar reaver o anexo:
   ```bash
   curl -X POST http://localhost:8085/api/financeiro/backfill \
     -H "Authorization: Bearer $TOKEN" \
     -H "Content-Type: application/json"
   ```
4. Se o erro de captura persistir por falha na API da Conta Azul, faça o download do recibo manualmente no ERP e realize o upload direto na interface administrativa da Inovare TI para encerrar a pendência.

### 5.4 Validação de Alertas em Testes (Simulação Crítica)
Para validar o fluxo de ponta a ponta de disparo de incidentes e envio de alertas formatados no Discord, dispare a rota de simulação:
```bash
curl -X POST http://localhost:8085/api/financeiro/test/simulate-critical-alert \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json"
```

---

## 6. Ferramentas Auxiliares

### Execução da Interface Swagger Localmente
```bash
docker run --rm -p 8080:8080 -e SWAGGER_JSON=/usr/share/nginx/html/openapi.json -v "%CD%/docs":/usr/share/nginx/html:ro swaggerapi/swagger-ui
```
A interface Swagger estará disponível em `http://localhost:8080` com a especificação das rotas do projeto.
