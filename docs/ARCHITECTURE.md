# Arquitetura do Sistema e Modelo de Dados — Inovare TI

Este documento descreve a arquitetura técnica global do sistema ITSM Inovare TI, decisões de design estrutural, o mapeamento detalhado dos pacotes de domínio no backend, o dicionário completo do banco de dados e o planejamento de infraestrutura para produção.

---

## 📋 Visão Geral: Arquitetura em 3 Camadas

O sistema é dividido em três camadas independentes, cada uma rodando de forma isolada dentro de contêineres Docker:

```
┌────────────────────────────────────────────────────────────────┐
│                         CLIENTE (Navegador)                    │
└────────────────────────┬───────────────────────────────────────┘
                         │ HTTP (porta 5173)
┌────────────────────────▼───────────────────────────────────────┐
│           CAMADA 1 — Frontend SPA (React + Nginx)              │
│  • React 19 + TypeScript + Vite 6 + Tailwind CSS               │
│  • React Router v7 com Lazy Loading por rota                   │
│  • Gerenciamento de estado via React Context (AuthContext)     │
│  • Camada de serviços modular por domínio sobre Axios, com     │
│    core HTTP central em services/api.ts                        │
└────────────────────────┬───────────────────────────────────────┘
                         │ HTTP/REST + JWT (porta 8085)
┌────────────────────────▼───────────────────────────────────────┐
│           CAMADA 2 — Backend API (Spring Boot)                 │
│  • Java 21 + Spring Boot 4 + Spring Security                   │
│  • Autenticação stateless via JWT                              │
│  • 2FA via TOTP integrada ao fluxo de autenticação             │
│  • Arquitetura por domínio (tickets, inventory, vault, etc.)   │
│  • Uploads salvos em volume Docker (/app/uploads)              │
│  • Discord Bot (JDA 5) inicializado assintronamente            │
└────────────────────────┬───────────────────────────────────────┘
                         │ JDBC / PostgreSQL protocol (porta 5432)
┌────────────────────────▼───────────────────────────────────────┐
│           CAMADA 3 — Banco de Dados (PostgreSQL 16)            │
│  • Schema gerenciado pelo Flyway (V1, V2, ... )                │
│  • Migrações versionadas para controle em produção             │
│  • Persistência via volume Docker (postgres_data)              │
└────────────────────────────────────────────────────────────────┘
```

---

## 🛠️ Infraestrutura Docker Compose

O `docker-compose.yml` define os serviços e a rede `inovare_network` (driver `bridge`). A comunicação entre API e DB usa o hostname `db` internamente.

### Serviços (Resumo)

| Serviço       | Container               | Build / Imagem              | Porta local                                 |
|---------------|-------------------------|-----------------------------|---------------------------------------------|
| `db`          | `inovareti_db`          | `postgres:16-alpine`        | `5436:5432`                                 |
| `api`         | `inovareti_api`         | Build local `./api`         | `8085:8085`                                 |
| `front`       | `inovareti_front`       | Build local `./front`       | `5173:80`                                   |
| `redis`       | `inovareti_redis`       | `redis:alpine`              | `6380:6379` (acesso interno: `redis:6379`)  |
| `prometheus`  | `inovareti_prometheus`  | `prom/prometheus:latest`    | `9095:9090`                                 |

### Rede

```yaml
networks:
  inovare_network:
    driver: bridge
```

### Volumes

| Volume          | Finalidade                                                    |
|-----------------|---------------------------------------------------------------|
| `postgres_data` | Dados do PostgreSQL — sobrevive a restarts e rebuilds         |
| `./uploads`     | Arquivos enviados pelos usuários (NFs, anexos de chamados)    |

> [!NOTE]
> O volume `./uploads` é montado como bind mount (`./uploads:/app/uploads`) para facilitar o backup local em ambiente de desenvolvimento.

### Componentes de Infraestrutura Adicionais

* **Redis**: O serviço `redis` é utilizado como camada de estabilidade para caching e para o rate-limiter distribuído (`RedisRateLimiter`) usado pelo endpoint de refresh e autenticação. A aplicação obtém host/porta via variáveis de ambiente (`SPRING_REDIS_HOST`, `SPRING_REDIS_PORT`) e registra o bean `StringRedisTemplate`.
* **Prometheus**: O projeto expõe métricas de SRE via Micrometer/Actuator em `/api/actuator/prometheus`. O serviço local `prometheus` permite visualizar dados e avaliar regras de alertas (regras em `docs/prometheus/alert.rules.yml`).
* **Healthcheck e Readiness**: O serviço `api` depende do banco de dados `db` e do cache `redis` estarem totalmente saudáveis antes de iniciar o bootstrap da aplicação, evitando falhas de conexão prematuras.

---

## 📈 Evolução Arquitetural e Refatoração Modular

O projeto passou por uma profunda reorganização para obedecer a boas práticas e reduzir o acoplamento:

### 1. Camada de Serviços do Frontend
A comunicação HTTP do frontend React foi refatorada e dividida em módulos focados em domínios específicos para centralizar requisições:
* `services/api.ts`: Client HTTP base (Axios), interceptors de autenticação e tratamento global de tokens.
* `services/ticketService.ts`: Chamados, envio de anexos e comentários.
* `services/inventoryService.ts`: Categorias, controle de estoque e lotes.
* `services/financeService.ts`: Configurações financeiras, sincronizações e dashboard ContaAzul.
* `services/userService.ts`: Gestão de usuários, setores e perfis administrativos.

### 2. Arquitetura Backend de Integrações (ContaAzul)
A integração com o ERP ContaAzul foi reorganizada para desacoplar as responsabilidades:
* `ContaAzulClient`: Fachada estável para integração de alto nível com o sistema.
* `ContaAzulSalesClient`: Centraliza pesquisas, vendas e fluxos de dados de faturamento.
* `ContaAzulFinancialClient`: Controla baixas de pagamento, download de recibos e geração de PDFs.
* `ContaAzulCustomerClient`: Responsável pela busca de pessoas e conversão cadastral.
* `ContaAzulRequestExecutor`: Componente técnico para envio seguro e retentativas HTTP.
* `ContaAzulResponseParser`: Normaliza e traduz payloads recebidos de APIs externas.

### 3. Divisão de Responsabilidades no Gerador de Relatórios
O `ReportService` foi desacoplado segundo o princípio de responsabilidade única (SRP):
* `ReportService`: Orquestrador central e fachada de exportação de relatórios.
* `ReportPdfExporter`: Renderização visual avançada e geração do arquivo PDF.
* `ReportExcelExporter`: Exportação de planilhas formatadas em Excel.
* `InventoryPricingService`: Algoritmo matemático para cálculo de custos e valores médios do estoque.

---

## 📂 Mapeamento Técnico de Domínios e Pacotes do Backend

A estrutura interna do backend Spring Boot (`api/src/main/java/br/dev/ctrls/inovareti`) está modularizada em pacotes de domínio coesos. Abaixo estão os mapeamentos funcionais de cada domínio:

### 1. `domain.auth` (Autenticação e Segurança Básica)
* `AuthController.java` — Endpoints públicos e seguros para fluxos de login, TOTP e troca de senhas temporárias.
* `LoginUseCase.java` — Validação de e-mail/senha e geração do token JWT via `TokenService`.
* `TwoFactorAuthService.java` — Lógica para geração de chaves secretas TOTP, QR Code e verificação ativa do código de 6 dígitos.
* `TwoFactorResetService.java` — Controla o reset de chaves de duplo fator de segurança por ação administrativa ou autônoma.

### 2. `domain.user` (Gestão de Usuários e Setores)
* `User.java` — Entidade do usuário que mapeia credenciais, nível de acesso (`ADMIN`, `TECHNICIAN`, `USER`), segredos de 2FA e chaves de integração do Discord.
* `Sector.java` — Setores funcionais (ex. Cardiologia, Recepção) associados aos usuários para mapear demandas de chamados.
* `UserController.java` & `SectorController.java` — CRUDs e endpoints administrativos.

### 3. `domain.ticket` (Motor de Chamados e Helpdesk)
* `Ticket.java` — Representação das solicitações do usuário final. Contém título, descrição, AnyDesk, status, prioridade, prazos de SLA e relacionamentos.
* `TicketCategory.java` — Categorias de chamados vinculadas a prazos de SLA específicos de suporte.
* `TicketComment.java` & `TicketAttachment.java` — Logs de conversações internas e anexos enviados por técnicos e solicitantes.

### 4. `domain.inventory` (Gestão de Inventário e Algoritmo FIFO)
* `Item.java` — Cadastro físico de insumos e hardware da clínica. Contém um campo estruturado em `specifications` mapeado como JSONB no PostgreSQL.
* `StockBatch.java` — Lotes de estoque com datas de aquisição e valores de compra específicos.
* `StockDeductionService.java` — Implementa a lógica transacional do algoritmo **FIFO (First-In, First-Out)** para dar saídas de estoque priorizando lotes mais antigos.
* `StockMovement.java` — Rastreamento de entradas e saídas de itens para auditoria financeira e conferência de inventário.

### 5. `domain.asset` (Patrimônio e Ativos de Hardware)
* `Asset.java` — Ativos físicos individuais (ex. Computador ID 45) vinculados a setores ou usuários específicos.
* `AssetMaintenance.java` — Registro de manutenções preventivas ou corretivas nos ativos de TI.

### 6. `domain.financeiro` (Automação de Recibos e Hub ContaAzul)
* `FinanceiroController.java` — Gerencia endpoints protegidos para consulta de alertas de SRE, reinício de jobs e painel ContaAzul.
* `ContaAzulAutomationService.java` — Cron job operacional de processamento de baixas no ERP e envio automático de e-mails.
* `FinanceEmailService.java` — Lógica dedicada para formatação e envio de e-mails com anexos PDF por SMTP corporativo.

### 7. `domain.vault` (Cofre Eletrônico Criptografado)
* `VaultItem.java` & `VaultItemShare.java` — Armazena senhas, documentos e anotações. Dados confidenciais são encriptados com **AES-256-GCM** em nível de aplicação.
* `VaultController.java` — Endpoints protegidos pelo `TwoFactorSessionGuard` que exigem 2FA ativo no JWT antes de expor ou salvar chaves.

### 8. `domain.audit` (Auditoria e Trilha de Compliance)
* `AuditLog.java` — Tabela que grava de maneira imutável ações cruciais no sistema (ex. leitura de segredos do Vault, logins malsucedidos).
* `AuditEventListener.java` — Listener assíncrono que escuta eventos de domínio publicados pela API e persiste logs em background.

---

## 💾 Modelo de Banco de Dados (Dicionário de Schema)

O banco de dados é o **PostgreSQL 16**. O schema é atualizado incrementalmente via **Flyway** (`api/src/main/resources/db/migration/`). Todas as tabelas seguem a convenção `snake_case` e as chaves primárias são UUIDs gerados na aplicação (RFC 4122).

---

### Domínio: Controle de Usuários e Acessos

#### Tabela: `sectors` (Setores Corporativos)
| Coluna | Tipo | Restrições | Descrição |
|--------|------|------------|-----------|
| `id` | `uuid` | PK, NOT NULL | Identificador único do setor |
| `name` | `varchar(100)` | NOT NULL, UNIQUE | Nome descritivo do setor |

#### Tabela: `users` (Usuários e Operadores)
| Coluna | Tipo | Restrições | Descrição |
|--------|------|------------|-----------|
| `id` | `uuid` | PK, NOT NULL | Identificador do usuário |
| `name` | `varchar(150)` | NOT NULL | Nome completo |
| `email` | `varchar(255)` | NOT NULL, UNIQUE | E-mail de login do usuário |
| `password_hash` | `varchar(255)` | NOT NULL | Hash BCrypt de senha |
| `must_change_password` | `boolean` | NOT NULL, default `false` | Forçar alteração de senha no próximo acesso |
| `role` | `varchar(20)` | NOT NULL | Nível de acesso: `ADMIN`, `TECHNICIAN`, `USER` |
| `sector_id` | `uuid` | NOT NULL, FK → `sectors.id` | Setor do usuário |
| `location` | `varchar(150)` | NOT NULL | Localização física na clínica (sala, andar) |
| `discord_user_id` | `varchar(50)` | NULLABLE | ID associado do Discord para notificações |
| `totp_secret` | `varchar(500)` | NULLABLE | Segredo TOTP criptografado com AES-GCM |
| `recovery_code_hash` | `varchar(255)` | NULLABLE | Hash BCrypt do código de emergência 2FA |
| `recovery_code_expires_at`| `timestamp` | NULLABLE | Expiração da chave temporária do Discord |

---

### Domínio: Central de Chamados (Helpdesk)

#### Tabela: `ticket_categories` (Categorias e Acordo de SLA)
| Coluna | Tipo | Restrições | Descrição |
|--------|------|------------|-----------|
| `id` | `uuid` | PK, NOT NULL | Identificador da categoria |
| `name` | `varchar(100)` | NOT NULL, UNIQUE | Nome descritivo (ex. Telefonia) |
| `base_sla_hours` | `integer` | NOT NULL, >= 1 | Horas previstas de SLA padrão |

#### Tabela: `tickets` (Registro de Solicitações)
| Coluna | Tipo | Restrições | Descrição |
|--------|------|------------|-----------|
| `id` | `uuid` | PK, NOT NULL | Identificador do chamado |
| `title` | `varchar(200)` | NOT NULL | Título da demanda |
| `description` | `text` | NULLABLE | Descrição do problema |
| `anydesk_code` | `varchar(50)` | NULLABLE | Código AnyDesk para suporte remoto |
| `status` | `varchar(20)` | NOT NULL | Status: `OPEN`, `IN_PROGRESS`, `RESOLVED`, `CLOSED` |
| `priority` | `varchar(10)` | NOT NULL | Prioridade: `LOW`, `NORMAL`, `HIGH`, `URGENT` |
| `requester_id` | `uuid` | NOT NULL, FK → `users.id` | Usuário solicitante |
| `assigned_to_id` | `uuid` | NULLABLE, FK → `users.id` | Técnico responsável |
| `category_id` | `uuid` | NOT NULL, FK → `ticket_categories.id` | Categoria de SLA |
| `requested_item_id` | `uuid` | NULLABLE, FK → `items.id` | Item de estoque requisitado para baixa |
| `requested_quantity` | `integer` | NULLABLE | Quantidade requisitada para o chamado |
| `sla_deadline` | `timestamp` | NOT NULL | Data limite calculada para conclusão |
| `created_at` | `timestamp` | NOT NULL | Criação da solicitação |
| `closed_at` | `timestamp` | NULLABLE | Encerramento do chamado |

---

### Domínio: Controle de Estoque (Inventário)

#### Tabela: `item_categories` (Categorias de Itens)
| Coluna | Tipo | Restrições | Descrição |
|--------|------|------------|-----------|
| `id` | `uuid` | PK, NOT NULL | Identificador da categoria |
| `name` | `varchar(100)` | NOT NULL, UNIQUE | Nome da categoria (ex. Toner, Periféricos) |
| `is_consumable` | `boolean` | NOT NULL | Indica se é descartado no consumo |

#### Tabela: `items` (Insumos de TI)
| Coluna | Tipo | Restrições | Descrição |
|--------|------|------------|-----------|
| `id` | `uuid` | PK, NOT NULL | Identificador do item |
| `item_category_id` | `uuid` | NOT NULL, FK → `item_categories.id` | Categoria do item |
| `name` | `varchar(150)` | NOT NULL | Nome comercial do produto |
| `current_stock` | `integer` | NOT NULL, >= 0 | Quantidade total disponível |
| `specifications` | `jsonb` | NULLABLE | Dados em formato JSON livre (marca, modelo) |

#### Tabela: `stock_batches` (Lotes de Aquisição - FIFO)
| Coluna | Tipo | Restrições | Descrição |
|--------|------|------------|-----------|
| `id` | `uuid` | PK, NOT NULL | Identificador do lote |
| `item_id` | `uuid` | NOT NULL, FK → `items.id` | Item associado |
| `original_quantity` | `integer` | NOT NULL, > 0 | Quantidade comprada originalmente |
| `remaining_quantity`| `integer` | NOT NULL, >= 0 | Quantidade disponível no lote |
| `unit_price` | `numeric(12,2)` | NOT NULL | Preço unitário pago |
| `entry_date` | `timestamp` | NOT NULL | Data de entrada do lote no estoque |

#### Tabela: `stock_movements` (Histórico de Movimentações)
| Coluna | Tipo | Restrições | Descrição |
|--------|------|------------|-----------|
| `id` | `uuid` | PK, NOT NULL | Identificador do movimento |
| `item_id` | `uuid` | NOT NULL, FK → `items.id` | Item afetado |
| `quantity` | `integer` | NOT NULL, >= 1 | Quantidade movimentada |
| `type` | `varchar(20)` | NOT NULL | Tipo do fluxo: `IN` (entrada) ou `OUT` (saída) |
| `reference` | `varchar(150)` | NOT NULL | Identificador de referência (ex: `TICKET:{id}`) |
| `unit_price_at_time`| `numeric(12,2)` | NULLABLE | Valor de aquisição praticado |
| `created_at` | `timestamp` | NOT NULL | Data da movimentação |

---

### Domínio: Cofre Eletrônico (Vault)

#### Tabela: `vault_items` (Itens do Cofre)
| Coluna | Tipo | Restrições | Descrição |
|--------|------|------------|-----------|
| `id` | `uuid` | PK, NOT NULL | Identificador do segredo |
| `title` | `varchar(150)` | NOT NULL | Título indicador |
| `description` | `text` | NULLABLE | Notas funcionais complementares |
| `item_type` | `varchar(20)` | NOT NULL | Tipo: `CREDENTIAL`, `DOCUMENT`, `NOTE` |
| `secret_content` | `text` | NULLABLE | Dados confidenciais (Criptografados com AES-GCM) |
| `file_path` | `varchar(500)` | NULLABLE | Caminho físico de arquivo de anexo seguro |
| `owner_id` | `uuid` | NOT NULL, FK → `users.id` | Dono proprietário do segredo |
| `sharing_type` | `varchar(20)` | NOT NULL | Acesso: `PRIVATE`, `ALL_TECH_ADMIN`, `CUSTOM` |
| `created_at` | `timestamp` | NOT NULL | Registro inicial |
| `updated_at` | `timestamp` | NOT NULL | Última modificação |

#### Tabela: `vault_item_shares` (Compartilhamentos de Itens Customizados)
| Coluna | Tipo | Restrições | Descrição |
|--------|------|------------|-----------|
| `id` | `uuid` | PK, NOT NULL | Identificador do registro |
| `vault_item_id` | `uuid` | NOT NULL, FK → `vault_items.id` | Item do cofre compartilhado |
| `shared_with_user_id`| `uuid` | NOT NULL, FK → `users.id` | Usuário que recebeu a liberação de acesso |

---

### Domínio: Monitoramento Técnico e Automação Financeira

#### Tabela: `contaazul_oauth_tokens` (Estado de Conexão ContaAzul)
| Coluna | Tipo | Restrições | Descrição |
|--------|------|------------|-----------|
| `id` | `uuid` | PK, NOT NULL | Identificador do registro |
| `access_token` | `text` | NOT NULL | Token ativo de comunicação |
| `refresh_token` | `text` | NOT NULL | Token para renovações automáticas |
| `token_type` | `varchar(20)` | NOT NULL | Padrão OAuth2 (`Bearer`) |
| `scope` | `varchar(255)` | NULLABLE | Escopo assinado |
| `expires_at` | `timestamp` | NOT NULL | Validade limite de expiração |
| `refreshed_at` | `timestamp` | NULLABLE | Data do último refresh bem-sucedido |
| `created_at` | `timestamp` | NOT NULL | Criação da autorização |
| `updated_at` | `timestamp` | NOT NULL | Atualização cadastral |

#### Tabela: `financial_link` (Mapeamento Usuário ↔ Cliente ERP)
| Coluna | Tipo | Restrições | Descrição |
|--------|------|------------|-----------|
| `id` | `uuid` | PK, NOT NULL | Identificador do link |
| `user_id` | `uuid` | NOT NULL, FK → `users.id` | Usuário interno correspondente |
| `contaazul_customer_id` | `varchar(100)`| NOT NULL, UNIQUE | ID absoluto do cliente no ContaAzul |
| `contaazul_customer_name`| `varchar(160)`| NULLABLE | Nome associado para conferência rápida |
| `linked_by_user_id` | `uuid` | NULLABLE, FK → `users.id` | Operador que criou a vinculação |
| `created_at` | `timestamp` | NOT NULL | Registro inicial |
| `updated_at` | `timestamp` | NOT NULL | Modificação cadastral |

#### Tabela: `processed_receipts` (Idempotência de Envios de Recibos)
| Coluna | Tipo | Restrições | Descrição |
|--------|------|------------|-----------|
| `id` | `uuid` | PK, NOT NULL | Identificador de envio |
| `financial_link_id` | `uuid` | NOT NULL, FK → `financial_link.id` | Link financeiro de destino |
| `parcela_id` | `varchar(120)` | NOT NULL | ID da parcela no ERP para evitar duplicidade |
| `receipt_hash` | `varchar(128)` | NOT NULL | Hash criptográfico do conteúdo para idempotência |
| `original_recipient_email`| `varchar(255)`| NOT NULL | Endereço destinatário |
| `status` | `varchar(20)` | NOT NULL | Status: `SENT`, `SKIPPED_DUPLICATE`, `FAILED` |
| `brevo_message_id` | `varchar(120)` | NULLABLE | Identificador no gateway de envio (Brevo) |
| `payload` | `jsonb` | NOT NULL | Dump completo em formato JSON da transação |
| `processed_at` | `timestamp` | NOT NULL | Instante da execução |

#### Tabela: `system_alerts` (Incidentes e Alertas do Sistema)
| Coluna | Tipo | Restrições | Descrição |
|--------|------|------------|-----------|
| `id` | `uuid` | PK, NOT NULL | Identificador do alerta |
| `alert_type` | `varchar(60)` | NOT NULL | Tipo técnico do incidente |
| `severity` | `varchar(20)` | NOT NULL | Gravidade: `INFO`, `WARN`, `ERROR`, `CRITICAL` |
| `source` | `varchar(120)` | NOT NULL | Origem funcional da falha (ex: `FinanceService`) |
| `title` | `varchar(255)` | NOT NULL | Título legível de diagnóstico |
| `details` | `text` | NULLABLE | Rastreabilidade do erro técnico |
| `context` | `jsonb` | NOT NULL | Payload necessário para reprocessamento manual |
| `resolved` | `boolean` | NOT NULL, default `false` | Indica se o operador solucionou o caso |
| `created_at` | `timestamp` | NOT NULL | Registro da ocorrência |
| `resolved_at` | `timestamp` | NULLABLE | Instante em que foi marcado como resolvido |

---

### Domínio: Auditoria Geral

#### Tabela: `audit_logs` (Rastreabilidade e Compliance Imutável)
| Coluna | Tipo | Restrições | Descrição |
|--------|------|------------|-----------|
| `id` | `uuid` | PK, NOT NULL | Identificador do log |
| `user_id` | `uuid` | NULLABLE | Operador autor da ação (nulo para sistema) |
| `action` | `varchar(60)` | NOT NULL | Ação executada (ex: `VAULT_SECRET_VIEW`) |
| `resource_type` | `varchar(60)` | NULLABLE | Recurso afetado (ex: `VaultItem`) |
| `resource_id` | `uuid` | NULLABLE | ID do recurso modificado |
| `details` | `text` | NULLABLE | Contexto técnico detalhado em string JSON |
| `ip_address` | `varchar(45)` | NULLABLE | IP de origem da requisição |
| `created_at` | `timestamp` | NOT NULL | Instante exato da gravação imutável |

> [!CAUTION]
> A tabela `audit_logs` não possui chaves estrangeiras vinculadas programaticamente nem operações de UPDATE ou DELETE expostas na camada de aplicação. Isso garante a blindagem física dos históricos mesmo em cenários de exclusão lógica ou física de usuários de TI.

---

## 🗺️ Diagrama de Relacionamentos do Banco de Dados

```
sectors             (1) ──<  users              (N)
item_categories     (1) ──<  items              (N)
items               (1) ──<  stock_batches      (N)
items               (1) ──<  stock_movements    (N)
ticket_categories   (1) ──<  tickets            (N)
users               (1) ──<  tickets            (N)  [solicitante]
users               (1) ──<  tickets            (N)  [técnico, nullable]
items               (1) ──<  tickets            (N)  [item de estoque, nullable]
vault_items         (1) ──<  vault_item_shares  (N)
users               (1) ──<  vault_items        (N)  [proprietário]
users               (1) ──<  vault_item_shares  (N)  [destinatário de share]
financial_link      (1) ──<  processed_receipts (N)
users               (1) ──<  financial_link     (N)  [usuário vinculado]
audit_logs                (Sem FK — Registros isolados e imutáveis)
system_alerts             (Focado em falhas operacionais e de integrações)
```

---

## 🔒 Mecanismos de Segurança de Acesso

O sistema emprega múltiplos perímetros para mitigar a exposição de dados sensíveis na internet:

1. **Tokens de Sessão Degradáveis**: A autenticação JWT comum libera funções operacionais básicas. Funções do Cofre (`VaultItem`) e do financeiro exigem a presença da claim `two_factor_verified: true` no payload assinado do token JWT.
2. **Criptografia Simétrica em Camada de Aplicação**: Todo segredo corporativo salvo na tabela `vault_items` e as chaves de 2FA dos usuários na tabela `users` são convertidos via `EncryptionService` utilizando o padrão **AES-256-GCM** com IV dinâmico de 12 bytes gerado com `SecureRandom`.
3. **Revogação Instantânea de Sessões**: O reset administrativo ou autônomo do 2FA provoca a limpeza instantânea da chave secreta TOTP do usuário no banco. O `SecurityFilter` e o interceptor `TwoFactorSessionGuard` realizam consultas de sanidade no banco a cada requisição sensível, derrubando imediatamente acessos ativos mesmo de tokens JWT válidos com claims pré-assinadas.

---

## 🚀 Planejamento de Deploy e Segurança de Rede

Para implantação segura sob o domínio público `itsm-inovare.ctrls.dev.br`, o modelo estrutural recomendado consiste no isolamento total do perímetro interno:

* **Túnel de Rede Dedicado (Cloudflare Tunnel)**: A porta exposta do container do Nginx ou da API backend não fica exposta diretamente à internet pública. O container do Cloudflare Daemon (`cloudflared`) cria uma ponte de saída criptografada ligando a rede interna do Docker diretamente às bordas da rede de CDN da Cloudflare.
* **Segurança na Borda (WAF & DDoS Protection)**: Todo o tráfego que atinge a aplicação é interceptado nas bordas, aplicando proteção ativa contra ataques automatizados, injeções SQL e permitindo a parametrização de bloqueios geográficos (Geo-IP) para limitar requisições exclusivamente ao território brasileiro.
