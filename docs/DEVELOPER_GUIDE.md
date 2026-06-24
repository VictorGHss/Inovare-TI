# Guia do Desenvolvedor e Operações — Inovare TI

Este documento contém as instruções para configuração local do ambiente, execução de testes, envio de e-mails, relatórios em PDF e monitoramento de incidentes.

---

## Configuração do Ambiente e Execução Local

Esta seção descreve os passos para configurar e executar a plataforma localmente.

### 1) Requisitos Mínimos
Antes de iniciar, certifique-se de ter instalado e configurado em sua estação de trabalho:
*   **Java SE Development Kit (JDK) 21**: Necessário para compilação e execução do backend.
*   **Node.js (v20 ou superior)** e **npm**: Necessários para gerenciar dependências e rodar o servidor de desenvolvimento do frontend.
*   **Docker** e **Docker Compose**: Necessários para orquestrar e rodar os serviços locais de banco de dados, cache e observabilidade.
*   **Apache Maven (3.9+)**: Opcional (o projeto inclui o Maven Wrapper `./mvnw`).
*   **Banco de Dados PostgreSQL (v16)** e **Redis (v6+)**: Opcionais caso opte por executá-los fora do Docker.

### 2) Inicialização dos Serviços de Infraestrutura (Containers Docker)
O ecossistema local depende dos contêineres de banco de dados PostgreSQL e cache Redis estarem saudáveis.
Na raiz do projeto (onde está o arquivo [docker-compose.yml](file:///C:/Projeto/Inovare-TI/docker-compose.yml)), execute o seguinte comando no terminal para subir os serviços em segundo plano:

```bash
docker compose up -d db redis prometheus
```

Esse comando inicializa:
*   **PostgreSQL (`inovareti_db`)**: Mapeado na porta local `5436:5432`, com persistência ligada ao volume `postgres_data`.
*   **Redis (`inovareti_redis`)**: Mapeado na porta local `6380:6379`.
*   **Prometheus (`inovareti_prometheus`)**: Mapeado na porta local `9095:9090` para telemetria.

Para verificar se os contêineres e seus respectivos healthchecks estão saudáveis (`healthy`), execute:
```bash
docker compose ps
```
> [!IMPORTANT]
> A API do backend possui um mecanismo de dependência que aguarda o banco de dados e o Redis estarem completamente ativos e saudáveis antes de concluir seu bootstrap. Isso evita estouro de conexões e erros no carregamento inicial de agendadores.

### 3) Configuração Inicial dos Arquivos de Ambiente (`.env`)
A parametrização de segurança, credenciais e integrações locais é gerida por variáveis de ambiente.
1. Na raiz do projeto, realize uma cópia do arquivo modelo [.env.example](file:///C:/Projeto/Inovare-TI/.env.example) renomeando-o para `.env`:
   ```bash
   cp .env.example .env
   ```
2. Abra o arquivo `.env` criado e customize as chaves e caminhos para o seu ambiente de desenvolvimento local.
3. Repita o processo na subpasta [api](file:///C:/Projeto/Inovare-TI/api), garantindo que exista um arquivo `.env` populado na pasta de execução do Java.

*Variáveis críticas de atenção na primeira configuração:*
*   `POSTGRES_PASSWORD`: Senha de acesso ao banco (deve corresponder à configurada no Compose).
*   `JWT_SECRET`: Chave secreta de no mínimo 32 caracteres para assinatura e cifragem dos tokens JWT de autenticação.
*   `VAULT_ENCRYPTION_KEY`: Chave simétrica em formato Base64 para descriptografia dos dados e credenciais do Vault.
*   `SPRING_REDIS_HOST` e `SPRING_REDIS_PORT`: Configurações de conexão para o Redis (em ambiente local fora do contêiner, mude para `localhost` e porta `6380` ou `6379` conforme mapeado).

---

## Compilação, Testes e Inicialização das Camadas

Após a subida dos serviços auxiliares no Docker e configuração das variáveis em `.env`, siga os passos para rodar a aplicação.

### 1) Backend (API Spring Boot Java 21)
O código principal está localizado na pasta [api](file:///C:/Projeto/Inovare-TI/api).

*   **Compilar o Projeto e Rodar Testes de Integração**:
    Para rodar a suíte completa de validações unitárias e testes de integração de ponta a ponta (E2E), execute a diretiva do Maven a partir do diretório do backend:
    ```bash
    cd api
    ./mvnw clean test
    ```
*   **Rodar um Teste Isolado**:
    Caso queira executar apenas uma classe de testes específica (ex. `IngestAppointmentsE2ETest`):
    ```bash
    ./mvnw -Dtest=IngestAppointmentsE2ETest test
    ```
*   **Executar a API Localmente**:
    Para iniciar o servidor backend em modo de desenvolvimento (escutando na porta `8085` configurada em `API_PORT`):
    ```bash
    ./mvnw spring-boot:run
    ```

### 2) Frontend (React + Vite + TypeScript)
O código visual do sistema está na pasta [front](file:///C:/Projeto/Inovare-TI/front).

*   **Instalar as Dependências do Node**:
    Antes do primeiro boot, navegue para o diretório do frontend e instale os pacotes npm necessários:
    ```bash
    cd front
    npm install
    ```
*   **Iniciar o Servidor de Desenvolvimento**:
    Para rodar o frontend com suporte a Hot Module Replacement (HMR) escutando na porta `5173`:
    ```bash
    npm run dev
    ```
    O console apresentará o link para acesso local: `http://localhost:5173/`.

---

## Mecanismos de Segurança e Autenticação (JWT + 2FA)

O sistema utiliza um fluxo de autenticação em duas etapas:

### 1. Fluxo de Escalonamento de Privilégios
1. **Etapa 1 (Login Comum)**: O usuário fornece e-mail e senha. O backend valida as credenciais e emite um JWT padrão que concede acesso a chamados básicos e visualização comum do inventário. Tentativas de acesso ao cofre ou configurações financeiras serão sumariamente bloqueadas.
2. **Etapa 2 (Desafio TOTP / MFA)**: O usuário envia o código dinâmico de 6 dígitos gerado no aplicativo autenticador para `/api/auth/2fa/verify`. Caso o código seja verificado com sucesso, o backend emite um novo token JWT contendo a claim `two_factor_verified: true`. Este token concede acesso pleno a recursos e escritas sensíveis.

### 2. O Guardião do Vault (`TwoFactorSessionGuard`)
* Antes de descriptografar qualquer segredo (usando o padrão **AES-256-GCM**), o backend intercepta a requisição e verifica se o JWT possui a claim `two_factor_verified` ativa.
* Adicionalmente, se o 2FA de um usuário for resetado (seja por um `ADMIN` ou via fluxo Discord), o segredo TOTP é apagado da tabela `users`. O `SecurityFilter` realiza essa validação de forma ativa a cada requisição, invalidando a sessão imediatamente mesmo que o JWT possua uma data de validade ativa.

---

## Configuração de Timezone e Fuso Horário (GMT-3 Brasília)

Para evitar divergências de datas na persistência e na geração de relatórios, o fuso horário padrão do sistema é definido como o de Brasília (**America/Sao_Paulo**).

Esta configuração é feita em três frentes:

1. **Congelamento da JVM (`@PostConstruct`)**: Na inicialização do Spring Boot, em `InovareTiApplication.java`, forçamos explicitamente o fuso horário padrão do sistema:
   ```java
   @PostConstruct
   public void init() {
       TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));
   }
   ```
2. **Propriedades Casadas do Jackson & Hibernate**: Encontradas no arquivo `application.properties`, alinhamos os fusos de conversão JSON e conexões JDBC:
   ```properties
   spring.jpa.properties.hibernate.jdbc.time_zone=America/Sao_Paulo
   spring.jackson.time-zone=America/Sao_Paulo
   ```
3. **Persistência de Datas**: Todas as datas no banco de dados utilizam o tipo `timestamp` sem fuso no Postgres, mas são lidas e gravadas com as compensações corretas em Brasília, assegurando relatórios de auditoria e logs consistentes.

---

## Manipulação de Erros — Padrão RFC 7807

O projeto adota o `@RestControllerAdvice` na classe `GlobalExceptionHandler` para formatar e normalizar respostas de erro seguindo a especificação **RFC 7807 (Problem Details)**:

| Exceção Backend | Status HTTP | Código Recomendado | Descrição |
|-----------------|-------------|--------------------|-----------|
| `MethodArgumentNotValid` | `400 Bad Request` | `ERR_VALIDATION_FAILED` | Erro de validação nos campos do payload |
| `NotFoundException` | `404 Not Found` | `ERR_RESOURCE_NOT_FOUND` | Recurso solicitado não existe no banco |
| `ConflictException` | `409 Conflict` | `ERR_RESOURCE_CONFLICT` | Violação de unicidade ou estado concorrente |
| `IllegalStateException`| `422 Unprocessable`| `ERR_BUSINESS_RULE` | Violação de regra de negócio da aplicação |
| `AccessDeniedException`| `403 Forbidden` | `ERR_ACCESS_DENIED` | Usuário autenticado mas sem permissão (Role) |
| `MaxUploadSizeExceeded`| `413 Payload Too Large`| `ERR_UPLOAD_LIMIT` | Upload excede o tamanho configurado (5MB) |
| `SQLGrammarException` / `DataAccessException` | `500 Internal Error` | `ERR_DATABASE_FAILURE` | Exceção de persistência mitigada e envelopada de forma segura para não vazar a DDL física |

---

## Configurações do SMTP de Cobrança

O envio de recibos e notificações financeiras por e-mail utiliza a infraestrutura do `spring-boot-starter-mail` (gerido pela classe `FinanceEmailService`):

*   **Modo de Teste Financeiro**: Quando `APP_FINANCEIRO_TEST_MODE=true`, todos os e-mails disparados pelo sistema (independente do e-mail do cliente cadastrado no ERP) são forçadamente redirecionados para o endereço cadastrado em `APP_FINANCEIRO_DEV_EMAIL`, contendo o marcador técnico `[TESTE]` no assunto. Isso evita disparos de testes acidentais para médicos ou clientes reais.

---

## Geração e Layout de Relatórios em PDF

Para a exportação de relatórios corporativos, o sistema utiliza a biblioteca **OpenPDF** (API livre compatível com iText):
*   **Renderização**: A formatação visual utiliza a classe `PdfPTable` para estruturar colunas reais com alinhamentos coerentes (valores financeiros e numéricos alinhados à direita, textos à esquerda).
*   **Identidade Visual**: O cabeçalho dos relatórios adota a cor primária da marca Inovare (`#feb56c`), fontes Helvetica-Bold em branco para contraste, e renderização dinâmica do logotipo empresarial (`src/main/resources/images/logo.png`).
*   **Rodapé Financeiro**: Linha de somatório total destacado com fundo cinza claro e resumo descritivo de custos ao final do documento.

---

## Operações, Monitoramento e Runbooks (SRE)

Esta seção traz informações para suporte e monitoramento em ambiente produtivo.

### 1. Stack de Observabilidade Local
O ecossistema Docker Compose disponibiliza ferramentas completas de triagem local:
*   **Prometheus**: Disponível em `http://localhost:9095` (porta interna `9090`).
*   **Grafana**: Disponível em `http://localhost:3001` (porta interna `3000`).
*   **Endpoint de Coleta da API**: `/api/actuator/prometheus` (expõe métricas Micrometer).

> [!NOTE]
> Para suportar o embedding do painel de monitoramento diretamente no frontend do sistema, o Grafana está pré-configurado com a variável `GF_SECURITY_ALLOW_EMBEDDING=true` e suporte a provisionamento automático de datasources em `docs/grafana/`.

### 2. Monitoramento de Saúde das Dependências (Healthcheck)
Os contêineres de banco de dados e cache possuem monitoramento automático:
*   **PostgreSQL Health**: `pg_isready -U ${POSTGRES_USER} -d ${POSTGRES_DB}`
*   **Redis Health**: `redis-cli ping`
*   A API só conclui a inicialização após o Postgres e o Redis reportarem `healthy`. Isso previne crashes e race conditions no boot dos agendadores e do rate-limiter.

---

### 3. Cronograma de Agendamentos e Schedulers

O sistema executa tarefas agendadas em background para auditoria, automação financeira e relatórios:

*   **Automação ContaAzul**: Varreduras programadas que processam baixas e enviam recibos.
*   **Weekly Digest Scheduler (`WeeklyDigestScheduler.java`)**: Configurado com `@Scheduled(cron = "0 0 17 * * FRI")` (toda sexta-feira às 17:00). Ele consolida métricas de chamados resolvidas, conformidade de SLA e envia um resumo diretamente para o Discord.

---

### 4. Runbook: Re-autorização Manual da ContaAzul
Caso os tokens OAuth2 sejam revogados ou corrompidos na tabela `contaazul_oauth_tokens`, siga o procedimento seguro de re-autorização:

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
3. Se o retorno for `authorized: false`, acesse a interface administrativa da aplicação (Menu Financeiro → ContaAzul → Conectar) para conceder permissão na URL oficial do ERP.
4. Caso precise expurgar ou depurar tokens antigos diretamente no banco de dados:
```sql
-- Consultar histórico de renovações
SELECT id, expires_at, refreshed_at, updated_at FROM contaazul_oauth_tokens ORDER BY updated_at DESC LIMIT 3;

-- Forçar limpeza para nova autorização limpa (use apenas em emergências)
DELETE FROM contaazul_oauth_tokens;
```

---

### 5. Runbook: Triagem do Alerta `ContaAzulForceRefreshThrottled`
Este alerta dispara quando há tentativas consecutivas e excessivas de atualização de tokens, acionando o rate-limiter.

1. Acesse o Prometheus (`http://localhost:9095`) ou o Grafana e avalie o volume de bloqueios:
```promql
increase(contaazul_force_refresh_throttled_total[5m])
```
2. Analise os logs da API em busca de abusos no endpoint administrativo:
```bash
docker logs inovareti_api --tail=100 | grep "ContaAzul force-refresh"
```
3. **Mitigação**: Se for decorrente de um comportamento anormal de cliques no frontend, instrua o usuário ou revise as regras do `RedisRateLimiter` (`FORCE_REFRESH_COOLDOWN_MS`). Se for um ataque externo de brute-force, aplique o bloqueio do IP na borda (Cloudflare WAF).

---

### 6. Runbook: Alerta de Falha na Captura de Recibo (20 Tentativas Excedidas)
A captura do PDF do recibo emitido na ContaAzul é realizada de forma assíncrona. Caso a automação falhe por 20 tentativas consecutivas:

1. Colete o ID da baixa/venda (`baixaId` ou `saleId`) contido no corpo do alerta.
2. Acesse o ERP ContaAzul e confirme se a baixa de fato possui um documento anexo e se a categoria do anexo está marcada como `RECIBO` ou `RECIBO_DIGITAL`.
3. Caso o anexo esteja válido mas não capturado, dispare o reprocessamento histórico (Backfill) dos últimos 30 dias usando credenciais de `ADMIN`:
```bash
curl -X POST http://localhost:8085/api/financeiro/backfill \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json"
```
4. Se o erro de captura persistir por falha na API do ERP, baixe o PDF manualmente e faça o upload direto na interface administrativa da Inovare TI para encerrar a pendência e notificar o cliente por e-mail.

### 7. Validação de Alertas em Testes (Simulação Crítica)
Para testar o fluxo completo de disparo de incidentes (Alerta → Evento → Listener → Notificação via Embed do Discord):
```bash
curl -X POST http://localhost:8085/api/financeiro/test/simulate-critical-alert \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json"
```

---

## Histórico de Desenvolvimento e Planejamento

### Cronologia de Fases do Projeto

*   **Fase 1 — Modelagem Base**: Entidades fundamentais (`Sector`, `User`, `TicketCategory`) e repositórios Spring Data JPA estruturados.
*   **Fase 2 — Perímetro de Segurança**: Configurações do Spring Security, JWT stateless, tratamento centralizado de exceções (RFC 7807).
*   **Fase 3 — Módulo de Inventário**: Cadastro de insumos (`Item`), categorizações e divisão por lotes de aquisição.
*   **Fase 4 — Motor de Chamados**: Abertura de tickets com vínculo transacional de baixa atômica de estoque ao encerrar chamados.
*   **Fase 5 — Duplo Fator (2FA)**: Integração obrigatória do TOTP (Google Authenticator) e fluxos seguros de verificação.
*   **Fase 6 — Armazenamento e Anexos**: Upload de notas fiscais e arquivos salvos localmente sob volumes protegidos.
*   **Fase 7 — Canal de Recuperação**: Criação do Bot do Discord corporativo (JDA 5) para suporte a reset autônomo de 2FA por chaves efêmeras geradas via direct message (DM).
*   **Fase 8 — Trilha de Auditoria (Compliance)**: Eventos assíncronos que registram em logs de auditoria imutáveis as ações de leitura e escritas no sistema.
*   **Fase 9 — Otimização PWA**: Suporte completo a Progressive Web Application, permitindo a instalação nativa em celulares e leitura offline de QR Codes de ativos.
*   **Fase 10 — Dashboard Premium & Hub Financeiro**:
    *   Implementação do algoritmo **FIFO (First-In, First-Out)** de estoque de maneira transacional e segura.
    *   Refatoração completa do `ReportService` para OpenPDF (`PdfPTable`) com design corporativo e tratamento de fuso horário (`America/Sao_Paulo`).
    *   Integração do Grafana e dashboards de saúde com visualização integrada e embedding.
*   **Fase 11 — Consolidação de Documentação & Deploy**: Redução de arquivos redundantes e polimento geral de segurança do backend e frontend para publicação.

---

## Ferramentas Auxiliares

### Rodar Interface Swagger localmente para APIs:
```bash
docker run --rm -p 8080:8080 -e SWAGGER_JSON=/usr/share/nginx/html/openapi.json -v "%CD%/docs":/usr/share/nginx/html:ro swaggerapi/swagger-ui
```
A especificação Swagger-UI ficará disponível em `http://localhost:8080`, facilitando a consulta dos endpoints expostos.
