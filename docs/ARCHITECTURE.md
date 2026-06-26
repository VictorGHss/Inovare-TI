# Arquitetura do Sistema e Modelo de Dados — Inovare TI

Este documento descreve o padrão hexagonal (Ports & Adapters) adotado na camada backend, a divisão de responsabilidades do frontend React e o dicionário de dados do banco PostgreSQL baseado no histórico de migrações do Flyway.

---

## 1. Estrutura Arquitetural do Sistema

O sistema é dividido em três camadas independentes executadas em contêineres Docker isolados:

1. **Frontend SPA (React + Vite):** Interface responsiva estruturada em TypeScript, utilizando React Router para navegação. A camada de serviços é modularizada por domínio sobre chamadas HTTP Axios centralizadas.
2. **Backend API (Spring Boot):** API modular em Java 21 utilizando Virtual Threads para chamadas assíncronas e concorrência leve. Adota o padrão de arquitetura hexagonal para isolar as regras de negócio de frameworks e adaptadores externos.
3. **Banco de Dados (PostgreSQL 16):** Persistência relacional com controle de transações ACID. O ciclo de vida do schema é gerido de forma incremental via Flyway.

### 1.1 Camada Backend: Arquitetura Hexagonal (Ports & Adapters)
O código-fonte do backend, localizado em `api/src/main/java/br/dev/ctrls/inovareti/modules/`, é dividido em pacotes por contexto delimitado (módulos). Cada módulo implementa a seguinte estrutura de pacotes:

```
br.dev.ctrls.inovareti.modules.<modulo>/
├── domain/                  <-- O Core do Hexágono (Sem dependência de frameworks)
│   ├── model/               <-- Modelos de domínio ricos em regras de negócio puras
│   └── port/                <-- Contratos de interface que definem as fronteiras
│       ├── input/           <-- Portas de Entrada (Contratos dos Casos de Uso)
│       └── output/          <-- Portas de Saída / SPI (Contratos para DB, APIs externas)
│
├── application/             <-- O Orquestrador
│   ├── usecase/             <-- Implementação dos Casos de Uso (Fluxos de negócio transacionais)
│   └── service/             <-- Serviços auxiliares do domínio
│
└── infrastructure/          <-- Os Adaptadores Tecnológicos (Spring, Banco, APIs)
    ├── adapter/
    │   ├── input/           <-- REST Controllers (@RestController) e Listeners
    │   └── output/          <-- Repositórios JPA (@Repository), Clientes HTTP e Integradores
    └── config/              <-- Classes de configuração específicas do módulo (@Configuration)
```

*   **Camada de Domínio (`domain`):** Perímetro isolado onde residem as entidades lógicas (ex. `Ticket`, `Item`, `Asset`) e os contratos das portas. Não possui importações de pacotes do Spring Framework. As portas de saída (`port/output`) representam SPIs (Service Provider Interfaces) a serem implementadas pela infraestrutura.
*   **Camada de Aplicação (`application`):** Implementa os casos de uso expostos nas portas de entrada. Gerencia a coordenação lógica das transações, carregando modelos e invocando portas de saída.
*   **Camada de Infraestrutura (`infrastructure`):** Conecta a aplicação com tecnologias externas. Os adaptadores de entrada (Driving) expõem REST APIs. Os adaptadores de saída (Driven) implementam as portas de persistência (JPA) e comunicação (HTTP clients).

---

## 2. Dicionário de Dados (PostgreSQL 16)

O banco de dados do Inovare TI é estruturado sob o PostgreSQL 16. O controle do schema é efetuado de forma cronológica via arquivos SQL na pasta `api/src/main/resources/db/migration/`.

### 2.1 Principais Marcos e Evolução do Schema
*   **V1 (Inicialização):** Consolidação do schema inicial com tabelas base de usuários, setores, categorias, chamados, inventário (lotes e movimentos), ativos (CMDB), cofre (Vault) e auditoria.
*   **V8 (Relacionamentos de Tickets e Tags):** Introdução da tabela autorreferencial `ticket_relations` e a primeira tabela simples de tags baseadas em strings textuais (`ticket_tags`).
*   **V9 (Suporte Multi-usuário em Ativos):** Criação da tabela de junção `asset_users` para relacionamento Many-to-Many entre ativos e usuários, migrando a coluna `user_id` da tabela `assets` e removendo-a em seguida para permitir múltiplos usuários por equipamento.
*   **V17 (ITSM e SLAs):** Introdução da tabela `itsm_categories` com chaves inteiras seriais e SLA configurável em horas, além da tabela de junção `ticket_additional_users` para vincular múltiplos colaboradores afetados pelo mesmo chamado.
*   **V18 (Ativação de Setores e Defesas):** Adiciona a flag logicamente controlada `active` na tabela de setores (`sectors`) e recria defensivamente a tabela `asset_users`.
*   **V19 (Tags Ricas e Criticidade):** Purga a antiga tabela textual de tags. Cria as tabelas `ticket_tags` (com suporte a cores em hexadecimal e macros de resolução `default_resolution`) e a tabela de junção `ticket_tag_relations`. Adiciona a flag `is_critical` na tabela de ativos (`assets`) e o relacionamento físico de ativos com chamados via coluna `asset_id` na tabela `tickets`.
*   **V20 (Destinatários de Estoque):** Adiciona o campo `recipient_user_id` associando movimentações de inventário ao usuário que recebeu o insumo.
*   **V34 (Controle de Estoque Crítico):** Adiciona a coluna `min_stock` na tabela de itens (`items`) para o controle e alertas de limites de estoque.
*   **V38 (Vínculo Bidirecional ITSM/CMDB):** Adiciona a coluna `ticket_id` na tabela `asset_maintenances` (histórico de manutenções de ativos), permitindo associar as ordens de manutenção diretamente aos chamados de suporte de origem.

---

### 2.2 Tabelas e Mapeamento Físico

#### Tabela: `sectors` (Setores Corporativos)
| Coluna | Tipo | Restrições | Descrição |
|--------|------|------------|-----------|
| `id` | `uuid` | PK, default `gen_random_uuid()` | Identificador único do setor |
| `name` | `varchar(100)` | NOT NULL, UNIQUE | Nome descritivo do setor |
| `active` | `boolean` | NOT NULL, default `true` | Indica se o setor está ativo no sistema (V18) |

#### Tabela: `users` (Colaboradores e Técnicos)
| Coluna | Tipo | Restrições | Descrição |
|--------|------|------------|-----------|
| `id` | `uuid` | PK, default `gen_random_uuid()` | Identificador único do usuário |
| `name` | `varchar(150)` | NOT NULL | Nome completo |
| `email` | `varchar(255)` | NOT NULL, UNIQUE | E-mail corporativo (login) |
| `password_hash` | `varchar(255)` | NOT NULL | Hash BCrypt da senha do usuário |
| `must_change_password` | `boolean` | NOT NULL, default `false` | Forçar redefinição no primeiro acesso |
| `role` | `varchar(20)` | NOT NULL | Nível de acesso: `ADMIN`, `TECHNICIAN`, `USER` |
| `sector_id` | `uuid` | NOT NULL, FK -> `sectors(id)` | Vínculo com o setor do colaborador |
| `location` | `varchar(150)` | NOT NULL | Sala ou andar físico na clínica |
| `discord_user_id` | `varchar(50)` | NULLABLE | ID da conta Discord para notificações |
| `totp_secret` | `varchar(500)` | NULLABLE | Segredo TOTP de dois fatores criptografado |
| `recovery_code_hash` | `varchar(255)` | NULLABLE | Hash do código de emergência do TOTP |
| `recovery_code_expires_at`| `timestamp` | NULLABLE | Expiração da chave temporária do Discord |
| `receives_it_notifications`| `boolean` | NOT NULL, default `true` | Define se o técnico recebe alertas de TI |

#### Tabela: `itsm_categories` (Categorias de Chamados com SLA)
| Coluna | Tipo | Restrições | Descrição |
|--------|------|------------|-----------|
| `id` | `serial` | PK | Identificador incremental da categoria (V17) |
| `name` | `varchar(150)` | NOT NULL, UNIQUE | Nome descritivo do canal de atendimento |
| `sla_hours` | `integer` | NOT NULL | Prazo máximo em horas para solução (SLA) |

#### Tabela: `tickets` (Incidente e Solicitação de Suporte - ITSM)
| Coluna | Tipo | Restrições | Descrição |
|--------|------|------------|-----------|
| `id` | `uuid` | PK, default `gen_random_uuid()` | Identificador único do chamado |
| `title` | `varchar(200)` | NOT NULL | Título resumido da demanda |
| `description` | `text` | NULLABLE | Detalhamento do problema |
| `anydesk_code` | `varchar(500)` | NULLABLE | Identificador para acesso remoto via AnyDesk |
| `status` | `varchar(20)` | NOT NULL | Status: `OPEN`, `IN_PROGRESS`, `RESOLVED`, `CLOSED` |
| `priority` | `varchar(10)` | NOT NULL | Prioridade: `LOW`, `NORMAL`, `HIGH`, `URGENT` |
| `requester_id` | `uuid` | NOT NULL, FK -> `users(id)` | Usuário solicitante |
| `assigned_to_id` | `uuid` | NULLABLE, FK -> `users(id)` | Técnico responsável |
| `category_id` | `uuid` | NOT NULL, FK -> `ticket_categories(id)`| Antiga categoria mestre baseada em UUID |
| `requested_item_id` | `uuid` | NULLABLE, FK -> `items(id)` | Item de estoque solicitado para baixa |
| `requested_quantity` | `integer` | NULLABLE | Quantidade a ser retirada |
| `sla_deadline` | `timestamp` | NOT NULL | Limite de prazo calculado para atendimento |
| `created_at` | `timestamp` | NOT NULL | Instante de abertura do chamado |
| `closed_at` | `timestamp` | NULLABLE | Instante de resolução ou encerramento |
| `solution_text` | `text` | NULLABLE | Nota explicativa da resolução (V7) |
| `asset_id` | `uuid` | NULLABLE, FK -> `assets(id)` | Equipamento associado do CMDB (V19) |

#### Tabela: `ticket_additional_users` (Vínculo de Usuários Afetados)
| Coluna | Tipo | Restrições | Descrição |
|--------|------|------------|-----------|
| `ticket_id` | `uuid` | PK, FK -> `tickets(id)` ON DELETE CASCADE | Chamado de suporte de referência (V17) |
| `user_id` | `uuid` | PK, FK -> `users(id)` ON DELETE CASCADE | Usuário adicional afetado pelo problema |

#### Tabela: `ticket_tags` (Entidade Mestre de Tags Ricas)
| Coluna | Tipo | Restrições | Descrição |
|--------|------|------------|-----------|
| `id` | `uuid` | PK, default `gen_random_uuid()` | Identificador da tag (V19) |
| `name` | `varchar(100)` | NOT NULL, UNIQUE | Nome descritivo da tag (ex. `#🚨ParadaCrítica`) |
| `color` | `varchar(20)` | NOT NULL | Cor em código hexadecimal para a UI (ex. `#FF0000`) |
| `active` | `boolean` | NOT NULL, default `true` | Flag para soft-delete lógico |
| `default_resolution` | `text` | NULLABLE | Resolução padrão auto-preenchida (macro de 1 clique) |

#### Tabela: `ticket_tag_relations` (Junção Chamados x Tags)
| Coluna | Tipo | Restrições | Descrição |
|--------|------|------------|-----------|
| `ticket_id` | `uuid` | PK, FK -> `tickets(id)` ON DELETE CASCADE | Vínculo com o chamado (V19) |
| `tag_id` | `uuid` | PK, FK -> `ticket_tags(id)` ON DELETE CASCADE | Vínculo com a tag associada |

#### Tabela: `items` (Insumos de TI)
| Coluna | Tipo | Restrições | Descrição |
|--------|------|------------|-----------|
| `id` | `uuid` | PK, default `gen_random_uuid()` | Identificador único do insumo |
| `item_category_id` | `uuid` | NOT NULL, FK -> `item_categories(id)`| Categoria de insumo (ex. Periféricos) |
| `name` | `varchar(150)` | NOT NULL | Nome comercial do produto |
| `current_stock` | `integer` | NOT NULL, >= 0 | Quantidade total disponível no estoque principal |
| `specifications` | `jsonb` | NOT NULL, default `'{}'` | Detalhes técnicos e marcas em formato JSON |
| `min_stock` | `integer` | NOT NULL, default `0` | Estoque mínimo para trigger de alertas (V34) |

#### Tabela: `stock_batches` (Lotes de Inventário - Algoritmo FIFO)
| Coluna | Tipo | Restrições | Descrição |
|--------|------|------------|-----------|
| `id` | `uuid` | PK, default `gen_random_uuid()` | Identificador único do lote de compra |
| `item_id` | `uuid` | NOT NULL, FK -> `items(id)` | Item de estoque associado |
| `original_quantity` | `integer` | NOT NULL, >= 1 | Quantidade de unidades adquirida no lote |
| `remaining_quantity`| `integer` | NOT NULL, >= 0 | Quantidade restante no lote (deduzida via FIFO) |
| `unit_price` | `numeric(12,2)` | NOT NULL | Preço unitário pago na aquisição |
| `brand` | `varchar(100)` | NULLABLE | Marca informada |
| `supplier` | `varchar(150)` | NULLABLE | Fornecedor parceiro |
| `purchase_reason` | `varchar(200)` | NULLABLE | Nota sobre o motivo da compra |
| `entry_date` | `timestamp` | NOT NULL | Data de recebimento e entrada física |
| `invoice_file_path` | `varchar(500)` | NULLABLE | Caminho local do arquivo de Nota Fiscal |

#### Tabela: `stock_movements` (Histórico de Movimentações de Estoque)
| Coluna | Tipo | Restrições | Descrição |
|--------|------|------------|-----------|
| `id` | `uuid` | PK, default `gen_random_uuid()` | Identificador do movimento |
| `item_id` | `uuid` | NOT NULL, FK -> `items(id)` | Item de estoque afetado |
| `type` | `varchar(10)` | NOT NULL, check IN/OUT | Tipo da ação: `IN` (entrada) ou `OUT` (saída) |
| `quantity` | `integer` | NOT NULL, >= 1 | Quantidade movimentada |
| `unit_price_at_time`| `numeric(19,2)`| NULLABLE | Custo unitário praticado na movimentação |
| `reference` | `varchar(255)` | NOT NULL | Origem (ex. `TICKET:ticket_uuid` ou `WITHDRAWAL`) |
| `date` | `timestamp` | NOT NULL | Data do registro da movimentação |
| `recipient_user_id` | `uuid` | NULLABLE, FK -> `users(id)` | Usuário que recebeu/retirou o insumo (V20) |

#### Tabela: `assets` (Ativos Físicos e Hardware - CMDB)
| Coluna | Tipo | Restrições | Descrição |
|--------|------|------------|-----------|
| `id` | `uuid` | PK, default `gen_random_uuid()` | Identificador único do ativo |
| `name` | `varchar(150)` | NOT NULL | Nome de registro (ex. Switch Switchroom) |
| `patrimony_code` | `varchar(80)` | NOT NULL, UNIQUE | Placa de patrimônio (ex. `INV-2026-045`) |
| `category_id` | `uuid` | NULLABLE, FK -> `asset_categories(id)`| Categoria de ativo (ex. Redes) |
| `specifications` | `text` | NULLABLE | Detalhes físicos estruturados do bem |
| `acquisition_value` | `numeric(19,2)`| NULLABLE | Preço de compra |
| `created_at` | `timestamp` | NOT NULL | Data de registro |
| `is_critical` | `boolean` | NOT NULL, default `false` | Se crítico, dispara fluxos de SLA de 1 hora (V19) |

#### Tabela: `asset_users` (Associação Multi-usuário em Ativos)
| Coluna | Tipo | Restrições | Descrição |
|--------|------|------------|-----------|
| `asset_id` | `uuid` | PK, FK -> `assets(id)` ON DELETE CASCADE | Ativo compartilhado (V9/V18) |
| `user_id` | `uuid` | PK, FK -> `users(id)` ON DELETE CASCADE | Colaborador associado que utiliza o ativo |

#### Tabela: `asset_maintenances` (Histórico de Ordens de Manutenção)
| Coluna | Tipo | Restrições | Descrição |
|--------|------|------------|-----------|
| `id` | `uuid` | PK, default `gen_random_uuid()` | Identificador da ordem |
| `asset_id` | `uuid` | NOT NULL, FK -> `assets(id)` | Ativo em manutenção |
| `maintenance_date` | `date` | NOT NULL | Data da intervenção |
| `type` | `varchar(20)` | NOT NULL | Tipo: `PREVENTIVE`, `CORRECTIVE`, `UPGRADE`, `TRANSFER` |
| `description` | `text` | NULLABLE | Descrição das atividades executadas |
| `cost` | `numeric(10,2)`| NULLABLE | Custo da ordem de serviço |
| `technician_id` | `uuid` | NOT NULL, FK -> `users(id)` | Técnico que efetuou o trabalho |
| `ticket_id` | `uuid` | NULLABLE, FK -> `tickets(id)` ON DELETE SET NULL | Chamado ITSM originário (V38) |
| `created_at` | `timestamp` | NOT NULL | Registro de criação |

#### Tabela: `vault_items` (Cofre Eletrônico Criptografado)
| Coluna | Tipo | Restrições | Descrição |
|--------|------|------------|-----------|
| `id` | `uuid` | PK, default `gen_random_uuid()` | Identificador único do segredo |
| `title` | `varchar(150)` | NOT NULL | Título indicador da senha ou anotação |
| `description` | `text` | NULLABLE | Descrição externa |
| `item_type` | `varchar(20)` | NOT NULL | Tipo: `CREDENTIAL`, `DOCUMENT`, `NOTE` |
| `secret_content` | `text` | NULLABLE | Conteúdo sigiloso criptografado com AES-256-GCM |
| `file_path` | `varchar(500)` | NULLABLE | Caminho físico de anexo criptografado |
| `owner_id` | `uuid` | NOT NULL, FK -> `users(id)` | Criador/Dono do registro |
| `sharing_type` | `varchar(20)` | NOT NULL | Visibilidade: `PRIVATE`, `ALL_TECH_ADMIN`, `CUSTOM` |
| `created_at` | `timestamp` | NOT NULL | Data de inserção |
| `updated_at` | `timestamp` | NOT NULL | Última modificação |

#### Tabela: `audit_logs` (Histórico de Compliance Imutável)
| Coluna | Tipo | Restrições | Descrição |
|--------|------|------------|-----------|
| `id` | `uuid` | PK, default `gen_random_uuid()` | Identificador único do log |
| `user_id` | `uuid` | NULLABLE | Usuário autor da ação (nulo para sistema) |
| `action` | `varchar(60)` | NOT NULL | Ação de auditoria (ex. `VAULT_SECRET_VIEW`) |
| `resource_type` | `varchar(60)` | NULLABLE | Entidade modificada ou visualizada |
| `resource_id` | `uuid` | NULLABLE | ID do registro afetado |
| `details` | `text` | NULLABLE | Dump descritivo dos parâmetros em formato JSON |
| `ip_address` | `varchar(45)` | NULLABLE | Endereço IP do cliente requisitante |
| `created_at` | `timestamp` | NOT NULL | Data da gravação (imutável) |

---

## 3. Relacionamentos Físicos no Banco de Dados

O diagrama abaixo consolida a estrutura de chaves estrangeiras (FK) do sistema:

```
sectors (1) ────< users (N)
users (1) ──────< tickets (N) [como requester_id]
users (1) ──────< tickets (N) [como assigned_to_id, nullable]
users (1) ──────< vault_items (N) [como owner_id]

itsm_categories (1) ───< tickets (N) [via category_id]
items (1) ─────────────< tickets (N) [via requested_item_id, nullable]
assets (1) ────────────< tickets (N) [via asset_id, nullable]

items (1) ─────────────< stock_batches (N)
items (1) ─────────────< stock_movements (N)
users (1) ─────────────< stock_movements (N) [como recipient_user_id, nullable]

assets (1) ────────────< asset_maintenances (N)
users (1) ─────────────< asset_maintenances (N) [como technician_id]
tickets (1) ───────────< asset_maintenances (N) [via ticket_id, nullable]

vault_items (1) ───< vault_item_shares (N)
users (1) ─────────< vault_item_shares (N) [como shared_with_user_id]

assets (N) ──────────o asset_users (N) ──o users (N) [Tabela de junção N:N]
tickets (N) ─────────o ticket_tag_relations (N) ──o ticket_tags (N) [Tabela de junção N:N]
tickets (N) ─────────o ticket_additional_users (N) ──o users (N) [Tabela de junção N:N]
```
