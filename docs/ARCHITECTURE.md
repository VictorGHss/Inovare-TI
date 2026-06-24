# Arquitetura do Sistema e Modelo de Dados — Inovare TI

Este documento descreve a estrutura técnica do sistema, a adoção do padrão hexagonal (Ports & Adapters), o mapeamento de domínios no backend, o dicionário do banco de dados e o deploy.

---

## Visão Geral: Arquitetura em 3 Camadas

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

## Infraestrutura Docker Compose

O `docker-compose.yml` define os serviços e a rede `inovare_network` (driver `bridge`). A comunicação entre API e DB usa o hostname `db` internamente.

### Serviços

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

* **Redis e Cache**: O serviço `redis` é utilizado para caching distribuído e para o rate-limiter (`RedisRateLimiter`). O sistema implementa um **Cache Temporário de Curta Duração (10 minutos TTL)** anotado sob `@Cacheable(value = "contaAzulSummary")` no resumo financeiro mensal (`fetchSummary`), evitando chamadas repetitivas à API da ContaAzul a cada troca de tela no frontend React.
* **Prometheus**: A API expõe métricas de uso via Micrometer/Actuator em `/api/actuator/prometheus`. O serviço local `prometheus` permite monitorar dados e avaliar regras de alertas (regras em `docs/prometheus/alert.rules.yml`).
* **Healthcheck e Readiness**: A API depende do banco de dados `db` e do cache `redis` estarem ativos e saudáveis antes de iniciar, evitando falhas de conexão na inicialização.

---

## Padrão Arquitetural: Arquitetura Hexagonal (Ports & Adapters)

O backend do Inovare TI adota rigorosamente a **Arquitetura Hexagonal (Ports & Adapters)** em seus módulos internos (localizados sob o pacote [api/src/main/java/br/dev/ctrls/inovareti/modules](file:///C:/Projeto/Inovare-TI/api/src/main/java/br/dev/ctrls/inovareti/modules/)). Esse padrão visa isolar as regras de negócio essenciais contra dependências externas de infraestrutura, bancos de dados, APIs de terceiros ou frameworks como o Spring Boot.

A divisão de pacotes por módulo é estruturada em três camadas bem definidas:

```
                  ┌─────────────────────────────────────┐
                  │          INFRASTRUCTURE             │
                  │   • controllers  • repositories jpa │
                  │   • rest clients • configurations   │
                  └──────────────────┬──────────────────┘
                                     │ implementa / chama
                                     ▼
                  ┌─────────────────────────────────────┐
                  │            APPLICATION              │
                  │  (Usecases, Services da Aplicação)  │
                  └──────────────────┬──────────────────┘
                                     │ orquestra
                                     ▼
┌────────────────────────────────────────────────────────────────────────┐
│                                DOMAIN                                  │
│                                                                        │
│   ┌────────────────────────┐         ┌──────────────────────────────┐  │
│   │         MODEL          │         │             PORT             │  │
│   │ • Entidades de negócio │         │  • output (Interfaces SPI)   │  │
│   │ • Regras puras         │         │  • input (Use Cases Ports)   │  │
│   └────────────────────────┘         └──────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────────┘
```

### 1. Camada de Domínio (`domain`)
Contém as regras de negócio e as entidades do sistema. Esta camada não depende do Spring Boot nem de bibliotecas de infraestrutura.
*   **Modelos de Domínio (`domain/model`)**: Classes que encapsulam o estado e o comportamento das regras do sistema. Exemplos:
    *   [AppointmentSession](file:///C:/Projeto/Inovare-TI/api/src/main/java/br/dev/ctrls/inovareti/modules/appointment/domain/model/AppointmentSession.java): Controla o estado de um agendamento individualizado.
    *   [NotificationGroup](file:///C:/Projeto/Inovare-TI/api/src/main/java/br/dev/ctrls/inovareti/modules/appointment/domain/model/NotificationGroup.java): Agrupa consultas para disparos em lote sem duplicidade.
    *   [StockBatch](file:///C:/Projeto/Inovare-TI/api/src/main/java/br/dev/ctrls/inovareti/modules/inventory/domain/model/StockBatch.java): Mapeia as quantidades e custos de aquisição do estoque local.
    *   [Ticket](file:///C:/Projeto/Inovare-TI/api/src/main/java/br/dev/ctrls/inovareti/modules/ticket/domain/model/Ticket.java): Representa o ciclo de vida e informações de suporte do chamado.
*   **Portas de Domínio (`domain/port`)**: Delimitam a fronteira do hexágono definindo contratos de interface:
    *   **Portas de Entrada (Input Ports)**: Definem o que o mundo externo pode solicitar ao domínio (interfaces que os Use Cases da camada de aplicação herdam ou expõem).
    *   **Portas de Saída (Output Ports / SPI - Service Provider Interfaces)**: Contratos abstratos de serviços necessários para o domínio, tais como banco de dados e APIs externas. Exemplos:
        *   [AppointmentSessionRepositoryPort](file:///C:/Projeto/Inovare-TI/api/src/main/java/br/dev/ctrls/inovareti/modules/appointment/domain/port/output/AppointmentSessionRepositoryPort.java): Porta de acesso ao banco de dados para gerir sessões.
        *   [ProfessionalExternalPort](file:///C:/Projeto/Inovare-TI/api/src/main/java/br/dev/ctrls/inovareti/modules/appointment/domain/port/output/ProfessionalExternalPort.java): Porta de saída para ler dados de médicos no Feegow.

### 2. Camada de Aplicação (`application`)
Coordenadora das transações e do fluxo de informações, esta camada traduz os fluxos de negócios em casos de uso operacionais utilizando as portas de entrada e saída.
*   **Use Cases**: Classes responsáveis por orquestrar a lógica e regras que alteram o estado da aplicação. Exemplos:
    *   [IngestAppointmentsUseCase](file:///C:/Projeto/Inovare-TI/api/src/main/java/br/dev/ctrls/inovareti/modules/appointment/application/usecase/IngestAppointmentsUseCase.java): Orquestra a busca, agrupamento, persistência atômica e disparo de mensagens em lote.
    *   [SendAppointmentTemplateUseCase](file:///C:/Projeto/Inovare-TI/api/src/main/java/br/dev/ctrls/inovareti/modules/appointment/application/usecase/SendAppointmentTemplateUseCase.java): Coordena o envio individualizado de mensagens do WhatsApp.

### 3. Camada de Infraestrutura (`infrastructure`)
Os adaptadores concretos (`infrastructure/adapter`) ligam a aplicação às tecnologias, contendo detalhes de rede, serialização, frameworks e bibliotecas externas.
*   **Adaptadores de Entrada (Input Adapters / Driving Adapters)**: Componentes tecnológicos que interceptam gatilhos externos e os enviam para a aplicação.
    *   *Exemplos*: Endpoints REST baseados em `@RestController`. Ex: [FinanceiroController](file:///C:/Projeto/Inovare-TI/api/src/main/java/br/dev/ctrls/inovareti/modules/finance/infrastructure/adapter/input/FinanceiroController.java) que expõe endpoints HTTP para a conciliação manual de parcelas.
*   **Adaptadores de Saída (Output Adapters / Driven Adapters)**: Classes que implementam as interfaces de portas de saída (`domain/port/output`).
    *   *Exemplos*: Clientes HTTP de APIs externas (ex: [FeegowPatientAdapter](file:///C:/Projeto/Inovare-TI/api/src/main/java/br/dev/ctrls/inovareti/modules/appointment/infrastructure/adapter/output/feegow/FeegowPatientAdapter.java) que implementa as conexões com o ERP Feegow; [BlipLIMEClient](file:///C:/Projeto/Inovare-TI/api/src/main/java/br/dev/ctrls/inovareti/modules/appointment/infrastructure/adapter/output/client/BlipLIMEClient.java) para envio LIME de metadados no Blip) e adaptadores JPA que encapsulam o Spring Data para persistência de tabelas no PostgreSQL (ex: `AppointmentSessionRepositoryAdapter`).

---

## Fluxo dos Módulos Internos

O sistema está estruturado em módulos de negócio:

### 1. Cofre de Credenciais e Documentos (Vault)
Armazena credenciais confidenciais, documentos técnicos e anotações.
*   **Criptografia em nível de aplicação**: O conteúdo de um segredo (`secret_content`) é criptografado antes da gravação no banco de dados com o algoritmo **AES-256-GCM** e IV dinâmico (gerido por [CryptoConverter](file:///C:/Projeto/Inovare-TI/api/src/main/java/br/dev/ctrls/inovareti/infrastructure/shared/security/CryptoConverter.java)).
*   **Validação MFA**: Operações de escrita e leitura de segredos confidenciais exigem que o token JWT possua uma claim válida de duplo fator (`two_factor_verified = true`), validada pelo `TwoFactorSessionGuard`. Em caso de reset do 2FA do usuário (via dashboard ou Bot do Discord), a sessão é invalidada imediatamente.

### 2. Módulo de Inventário e Algoritmo FIFO
Gerencia a integridade e o controle de quantidades de hardware e insumos consumidos.
*   **Entrada de Produtos por Lotes**: Insumos cadastrados são agrupados em lotes de estoque ([StockBatch](file:///C:/Projeto/Inovare-TI/api/src/main/java/br/dev/ctrls/inovareti/modules/inventory/domain/model/StockBatch.java)) registrando preço de compra e data de entrada.
*   **Algoritmo FIFO Transacional**: No encerramento de chamados que exigem peças de reposição (ex. troca de toner ou teclado), a rotina de encerramento (`ResolveTicketUseCase`) executa de forma automática a saída baseando-se na regra FIFO (lotes mais antigos consumidos primeiro). A execução herda a propagação (`Propagation.MANDATORY`), garantindo que se o algoritmo FIFO falhar ou o estoque estiver inconsistente, a transação sofra rollback.
*   **Fallback de Movimentações**: O sistema verifica se a saída gerou registros de movimentação em `stock_movements`. Em caso de ausência, cria um registro de fallback de saída (tipo `OUT` e referência `TICKET:{ticketId}`) para evitar furos no balanço contábil.

### 3. Gestão de Tickets e Helpdesk (ITSM)
Centraliza a triagem, direcionamento e monitoramento de incidentes e solicitações de TI da clínica.
*   **Cálculo Dinâmico de SLA**: O prazo de atendimento (`sla_deadline`) de um chamado ([Ticket](file:///C:/Projeto/Inovare-TI/api/src/main/java/br/dev/ctrls/inovareti/modules/ticket/domain/model/Ticket.java)) é calculado com base nas horas úteis vigentes da categoria.
*   **Regra de Parada Crítica (Incidentes Críticos)**: Se o chamado estiver vinculado a um ativo físico mestre sinalizado como crítico (`is_critical = true`), ou se a descrição aberta via Bot de Discord contiver expressões regulares associadas a patrimônios críticos (padrão regex `INV-\d{4}-\d+`), a regra é acionada: a prioridade do chamado é definida como `URGENT`, o SLA é definido para **1 hora** limite, e a tag `#🚨ParadaCrítica` é injetada. Um alerta é disparado via webhook e bot (JDA) diretamente na DM do técnico responsável.
*   **Macros de 1-Clique e Busca Lateral**: Permite encontrar soluções sugeridas de chamados resolvidos que compartilham tags similares e utilizar Macros ("Aplicar Solução Padrão") para preencher notas de resolução.

### 4. Telemetria e Analytics com Prometheus
Fornece métricas para diagnóstico preventivo e controle técnico do sistema.
*   **Exposição de Actuator**: O pacote `analytics` configura e exporta telemetria do Micrometer no endpoint exposto `/api/actuator/prometheus`.
*   **Monitoramento de Circuit Breakers**: Se o Circuit Breaker de comunicação com o Feegow ERP entrar em estado `OPEN`, o alerta é gerado no Prometheus e enviado como notificação via webhook para o canal de alertas do Discord.

---

## Modelo de Banco de Dados (Dicionário de Schema)

O banco de dados é o **PostgreSQL 16**. O schema é atualizado incrementalmente via **Flyway** (`api/src/main/resources/db/migration/`). Todas as tabelas seguem a convenção `snake_case` e as chaves primárias são UUIDs gerados na aplicação (RFC 4122).

---

### Domínio: Controle de Usuários e Acessos

#### Tabela: `sectors` (Setores Corporativos)
| Coluna | Tipo | Restrições | Descrição |
|--------|------|------------|-----------|
| `id` | `uuid` | PK, NOT NULL | Identificador único do setor |
| `name` | `varchar(100)` | NOT NULL, UNIQUE | Nome descritivo do setor |
| `active` | `boolean` | NOT NULL, default `true` | Status ativo do setor para soft-delete lógico |

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
| `asset_id` | `uuid` | NULLABLE, FK → `assets.id` ON DELETE SET NULL | ID do ativo/patrimônio associado |

---

### Domínio: Gestão de Tags e Base de Conhecimento

#### Tabela: `ticket_tags` (Entidade Mestre de Tags)
| Coluna | Tipo | Restrições | Descrição |
|--------|------|------------|-----------|
| `id` | `uuid` | PK, NOT NULL | Identificador único da tag |
| `name` | `varchar(100)` | NOT NULL, UNIQUE | Nome descritivo da tag (ex. #🚨ParadaCrítica) |
| `color` | `varchar(20)` | NULLABLE | Cor associada para renderização visual em formato HEX |
| `active` | `boolean` | NOT NULL, default `true` | Status ativo para inativação lógica / soft-delete |
| `default_resolution` | `text` | NULLABLE | Macro de resolução associada à tag (nota de fechamento automático) |

#### Tabela: `ticket_tag_relations` (Relação Many-to-Many entre Chamados e Tags)
| Coluna | Tipo | Restrições | Descrição |
|--------|------|------------|-----------|
| `ticket_id` | `uuid` | PK, FK → `tickets.id` ON DELETE CASCADE | Chamado associado |
| `tag_id` | `uuid` | PK, FK → `ticket_tags.id` ON DELETE CASCADE | Tag mestre associada |

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

### Domínio: Patrimônio e Ativos de Hardware (CMDB)

#### Tabela: `assets` (Patrimônio e Ativos)
| Coluna | Tipo | Restrições | Descrição |
|--------|------|------------|-----------|
| `id` | `uuid` | PK, NOT NULL | Identificador do ativo |
| `name` | `varchar(100)` | NOT NULL | Nome descritivo (ex. Impressora Balcão) |
| `type` | `varchar(50)` | NOT NULL | Tipo do ativo (ex. HARDWARE, SOFTWARE) |
| `tag` | `varchar(50)` | NOT NULL, UNIQUE | Código único de patrimônio (ex. INV-2026-004) |
| `active` | `boolean` | NOT NULL, default `true` | Status ativo do bem |
| `is_critical` | `boolean` | NOT NULL, default `false` | Sinalização de ativo crítico para SLA de 1 hora e parada crítica |

#### Tabela: `asset_users` (Relacionamento Multi-usuário de Ativos)
| Coluna | Tipo | Restrições | Descrição |
|--------|------|------------|-----------|
| `asset_id` | `uuid` | PK, FK → `assets.id` ON DELETE CASCADE | Identificador do ativo compartilhado |
| `user_id` | `uuid` | PK, FK → `users.id` ON DELETE CASCADE | Identificador do usuário associado |

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
> A tabela `audit_logs` não possui chaves estrangeiras vinculadas programaticamente nem permite operações de UPDATE ou DELETE pela aplicação. Isso garante a segurança do histórico de auditoria mesmo em cenários de exclusão de usuários.

---

## Diagrama de Relacionamentos do Banco de Dados

```
sectors             (1) ──<  users              (N)
item_categories     (1) ──<  items              (N)
items               (1) ──<  stock_batches      (N)
items               (1) ──<  stock_movements    (N)
ticket_categories   (1) ──<  tickets            (N)
users               (1) ──<  tickets            (N)  [solicitante]
users               (1) ──<  tickets            (N)  [técnico, nullable]
items               (1) ──<  tickets            (N)  [item de estoque, nullable]
assets              (1) ──<  tickets            (N)  [patrimônio, nullable]
vault_items         (1) ──<  vault_item_shares  (N)
users               (1) ──<  vault_items        (N)  [proprietário]
users               (1) ──<  vault_item_shares  (N)  [destinatário de share]
financial_link      (1) ──<  processed_receipts (N)
users               (1) ──<  financial_link     (N)  [usuário vinculado]
assets              (N) ──o  asset_users        (N)  [muitos-para-muitos com users]
tickets             (N) ──o  ticket_tag_relations (N) [muitos-para-muitos com ticket_tags]
audit_logs                (Sem FK — Registros isolados e imutáveis)
system_alerts             (Focado em falhas operacionais e de integrações)
```

---

## Mecanismos de Segurança de Acesso

O sistema utiliza diferentes camadas para evitar a exposição de dados na internet:

1. **Tokens de Sessão**: A autenticação JWT padrão libera funções básicas. Acesso ao Cofre (`VaultItem`) e recursos financeiros exige a presença da claim `two_factor_verified: true` no token JWT.
2. **Criptografia Simétrica**: Segredos salvos na tabela `vault_items` e as chaves de 2FA dos usuários na tabela `users` são convertidos via `EncryptionService` utilizando o padrão **AES-256-GCM** com IV dinâmico de 12 bytes gerado com `SecureRandom`.
3. **Revogação de Sessões**: O reset do 2FA apaga o segredo TOTP no banco. O `SecurityFilter` e o interceptor `TwoFactorSessionGuard` validam o status a cada requisição, invalidando o acesso caso necessário.

---

## Planejamento de Deploy e Segurança de Rede

Para implantar o sistema de forma segura sob o domínio público `itsm-inovare.ctrls.dev.br`, recomenda-se isolar a rede interna:

* **Túnel de Rede (Cloudflare Tunnel)**: A porta da API ou do frontend não fica exposta diretamente à internet. O contêiner do Cloudflare Daemon (`cloudflared`) cria uma ponte segura ligando a rede interna do Docker diretamente às bordas da Cloudflare.
* **Segurança de Borda (WAF e Proteção contra DDoS)**: O tráfego que chega à aplicação é analisado pelas regras do firewall da Cloudflare, aplicando proteções e permitindo restringir acessos por região geográfica.
