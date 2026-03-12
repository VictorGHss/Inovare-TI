# Documentação do Banco de Dados — Inovare TI

> **SGBD:** PostgreSQL  
> **Gerenciamento de migrações:** Flyway  
> **Convenção de nomenclatura:** snake_case para tabelas e colunas; UUIDs como chaves primárias.

> **Estado atual do schema:** as estruturas de Vault e recuperação de 2FA já estão consolidadas no arquivo base `api/src/main/resources/db/migration/V1__init.sql`.

---

## Estrutura Inicial de Tabelas (Fase 1)

### Domínio: Usuários (`domain/user`)

#### Tabela `sectors`

Armazena os setores ou departamentos da organização. Um setor é referenciado por múltiplos usuários.

| Coluna | Tipo          | Restrições              | Descrição                     |
|--------|---------------|-------------------------|-------------------------------|
| `id`   | `uuid`        | PK, NOT NULL            | Identificador único do setor  |
| `name` | `varchar(100)`| NOT NULL, UNIQUE        | Nome do setor                 |

---

#### Tabela `users`

Armazena os usuários do sistema. Cada usuário possui um papel (role) e pertence a um setor.

| Coluna            | Tipo           | Restrições            | Descrição                                          |
|-------------------|----------------|-----------------------|----------------------------------------------------|
| `id`              | `uuid`         | PK, NOT NULL          | Identificador único do usuário                     |
| `name`            | `varchar(150)` | NOT NULL              | Nome completo                                      |
| `email`           | `varchar(255)` | NOT NULL, UNIQUE      | E-mail de login                                    |
| `password_hash`   | `varchar(255)` | NOT NULL              | Hash da senha (bcrypt)                             |
| `must_change_password` | `boolean`  | NOT NULL, default `false` | Indica se o usuário deve trocar a senha no próximo login |
| `role`            | `varchar(20)`  | NOT NULL              | Papel do usuário: `ADMIN`, `TECHNICIAN` ou `USER`  |
| `sector_id`       | `uuid`         | NOT NULL, FK → sectors| Setor ao qual o usuário pertence                   |
| `location`        | `varchar(150)` | NOT NULL              | Localização física (sala, andar, unidade)          |
| `discord_user_id` | `varchar(50)`  | NULLABLE              | ID do usuário no Discord corporativo               |
| `totp_secret`     | `varchar(500)` | NULLABLE              | Segredo TOTP do 2FA, persistido de forma protegida em nível de aplicação |
| `recovery_code_hash` | `varchar(255)` | NULLABLE           | Hash BCrypt do código temporário de recuperação via Discord |
| `recovery_code_expires_at` | `timestamp` | NULLABLE        | Data/hora de expiração do código temporário de recuperação |

**Relacionamentos:**
- `users.sector_id` → `sectors.id` (N:1 — muitos usuários por setor)

**Enum `UserRole`:**

| Valor       | Descrição                                 |
|-------------|-------------------------------------------|
| `ADMIN`     | Administrador total do sistema            |
| `TECHNICIAN`| Técnico de TI / operação                  |
| `USER`      | Usuário final autenticado                 |

---

### Domínio: Chamados / Helpdesk (`domain/ticket`)

#### Tabela `ticket_categories`

Agrupa os chamados por tipo e define o SLA base de atendimento.

| Coluna           | Tipo           | Restrições       | Descrição                                        |
|------------------|----------------|------------------|--------------------------------------------------|
| `id`             | `uuid`         | PK, NOT NULL     | Identificador único da categoria                 |
| `name`           | `varchar(100)` | NOT NULL, UNIQUE | Nome da categoria (ex.: "Suporte Hardware")      |
| `base_sla_hours` | `integer`      | NOT NULL, ≥ 1    | Prazo máximo de atendimento em horas             |

---

### Domínio: Inventário (`domain/inventory`)

#### Tabela `item_categories`

Agrupa os itens de inventário de TI por tipo, indicando se são consumíveis.

| Coluna          | Tipo           | Restrições       | Descrição                                          |
|-----------------|----------------|------------------|----------------------------------------------------|
| `id`            | `uuid`         | PK, NOT NULL     | Identificador único da categoria                   |
| `name`          | `varchar(100)` | NOT NULL, UNIQUE | Nome da categoria (ex.: "Notebook", "Cabo USB")    |
| `is_consumable` | `boolean`      | NOT NULL         | `true` se o item é consumível (ex.: tonner, cabo)  |

---

---

## Novas Tabelas (Fase 3)

### Domínio: Inventário — Items e Lotes (`domain/inventory`)

#### Tabela `items`

Armazena os itens de inventário de TI. O campo `specifications` usa o tipo `jsonb`
do PostgreSQL para armazenar atributos técnicos livres (ex.: marca, modelo, serial).

| Coluna             | Tipo           | Restrições                         | Descrição                                                 |
|--------------------|----------------|------------------------------------|-----------------------------------------------------------|
| `id`               | `uuid`         | PK, NOT NULL                       | Identificador único do item                               |
| `item_category_id` | `uuid`         | NOT NULL, FK → item_categories     | Categoria do item                                         |
| `name`             | `varchar(150)` | NOT NULL                           | Nome do item (ex.: "Toner Brother HL-L2360DW")            |
| `current_stock`    | `integer`      | NOT NULL, ≥ 0                      | Quantidade atual em estoque (gerenciada via lotes)        |
| `specifications`   | `jsonb`        | NULLABLE                           | Atributos técnicos livres em JSON                         |

> **Sobre o campo `specifications`:** mapeado com `@JdbcTypeCode(SqlTypes.JSON)` do Hibernate 6.
> O PostgreSQL armazena como `jsonb`, permitindo indexação e consultas sobre o JSON.
> Exemplo de valor: `{"marca": "Brother", "modelo": "HL-L2360DW", "voltagem": "110V"}`.

**Relacionamentos:**
- `items.item_category_id` → `item_categories.id` (N:1)

---

#### Tabela `stock_batches`

Registra cada entrada de estoque de um item (lote de compra). O campo `remaining_quantity`
é decrementado conforme o estoque do lote é consumido.

| Coluna               | Tipo            | Restrições            | Descrição                                         |
|----------------------|-----------------|-----------------------|---------------------------------------------------|
| `id`                 | `uuid`          | PK, NOT NULL          | Identificador único do lote                       |
| `item_id`            | `uuid`          | NOT NULL, FK → items  | Item ao qual pertence o lote                      |
| `original_quantity`  | `integer`       | NOT NULL, > 0         | Quantidade total Original do lote                 |
| `remaining_quantity` | `integer`       | NOT NULL, > 0         | Quantidade ainda disponível no lote               |
| `unit_price`         | `numeric(12,2)` | NOT NULL              | Preço unitário de compra neste lote               |
| `entry_date`         | `timestamp`     | NOT NULL              | Data/hora de registro da entrada                  |

**Relacionamentos:**
- `stock_batches.item_id` → `items.id` (N:1)

---

---

## Novas Tabelas (Fase 4)

### Domínio: Chamados (`domain/ticket`)

#### Tabela `tickets`

Registra cada chamado aberto no sistema. Pode referenciar um item de inventário (baixa de estoque ao fechar).

| Coluna                | Tipo           | Restrições                          | Descrição                                                          |
|-----------------------|----------------|-------------------------------------|--------------------------------------------------------------------|
| `id`                  | `uuid`         | PK, NOT NULL                        | Identificador único do chamado                                     |
| `title`               | `varchar(200)` | NOT NULL                            | Título do chamado                                                  |
| `description`         | `text`         | NULLABLE                            | Descrição detalhada do problema ou solicitação                     |
| `anydesk_code`        | `varchar(50)`  | NULLABLE                            | Código AnyDesk para acesso remoto ao computador do solicitante     |
| `status`              | `varchar(20)`  | NOT NULL                            | Status: `OPEN`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`                |
| `priority`            | `varchar(10)`  | NOT NULL                            | Prioridade: `LOW`, `NORMAL`, `HIGH`, `URGENT`                      |
| `requester_id`        | `uuid`         | NOT NULL, FK → users                | Usuário que abriu o chamado                                        |
| `assigned_to_id`      | `uuid`         | NULLABLE, FK → users                | Técnico responsável pelo atendimento                               |
| `category_id`         | `uuid`         | NOT NULL, FK → ticket_categories    | Categoria do chamado                                               |
| `requested_item_id`   | `uuid`         | NULLABLE, FK → items                | Item de inventário solicitado (opcional)                           |
| `requested_quantity`  | `integer`      | NULLABLE                            | Quantidade do item solicitado; obrigatório se `requested_item_id` for preenchido |
| `sla_deadline`        | `timestamp`    | NOT NULL                            | Prazo de atendimento calculado com base no `base_sla_hours` da categoria |
| `created_at`          | `timestamp`    | NOT NULL                            | Data/hora de abertura do chamado                                   |
| `closed_at`           | `timestamp`    | NULLABLE                            | Data/hora de encerramento do chamado                               |

**Relacionamentos:**
- `tickets.requester_id` → `users.id` (N:1)
- `tickets.assigned_to_id` → `users.id` (N:1, nullable)
- `tickets.category_id` → `ticket_categories.id` (N:1)
- `tickets.requested_item_id` → `items.id` (N:1, nullable)

**Regra de negócio — baixa de estoque:**
Ao fechar um chamado que possua `requested_item_id` e `requested_quantity`, o campo
`current_stock` do `Item` é decrementado em `requested_quantity` dentro da mesma transação.

---

## Diagrama de Relacionamentos (Atualizado — Fase 4)

```
sectors            (1) ──< users             (N)
item_categories    (1) ──< items             (N)
items              (1) ──< stock_batches     (N)
tick_categories    (1) ──< tickets           (N)
users              (1) ──< tickets           (N)  [requester]
users              (1) ──< tickets           (N)  [assigned_to, nullable]
items              (1) ──< tickets           (N)  [requested_item, nullable]
```

---

## Domínio: Vault Seguro (`domain/vault`)

### Tabela `vault_items`

Armazena os itens do cofre de credenciais e documentos sensíveis. O `secret_content`/`secretContent` é criptografado em nível de aplicação com AES-256/GCM antes de ser persistido.

| Coluna           | Tipo           | Restrições                         | Descrição |
|------------------|----------------|------------------------------------|-----------|
| `id`             | `uuid`         | PK, NOT NULL                       | Identificador único do item do cofre |
| `title`          | `varchar(150)` | NOT NULL                           | Título de identificação do item |
| `description`    | `text`         | NULLABLE                           | Descrição funcional do item |
| `item_type`      | `varchar(20)`  | NOT NULL                           | Tipo do item: `CREDENTIAL`, `DOCUMENT`, `NOTE` |
| `secret_content` | `text`         | NULLABLE                           | Conteúdo sensível criptografado em nível de aplicação |
| `file_path`      | `varchar(500)` | NULLABLE                           | Caminho do anexo salvo em storage local |
| `owner_id`       | `uuid`         | NOT NULL, FK → users               | Usuário proprietário do item |
| `sharing_type`   | `varchar(20)`  | NOT NULL                           | Regra de compartilhamento: `PRIVATE`, `ALL_TECH_ADMIN`, `CUSTOM` |
| `created_at`     | `timestamp`    | NOT NULL                           | Data/hora de criação |
| `updated_at`     | `timestamp`    | NOT NULL                           | Data/hora da última atualização |

**Relacionamentos:**
- `vault_items.owner_id` → `users.id` (N:1)

### Tabela `vault_item_shares`

Representa os compartilhamentos explícitos entre um item do cofre e usuários específicos quando `sharing_type = CUSTOM`.

| Coluna                | Tipo      | Restrições                    | Descrição |
|-----------------------|-----------|-------------------------------|-----------|
| `id`                  | `uuid`    | PK, NOT NULL                  | Identificador único do compartilhamento |
| `vault_item_id`       | `uuid`    | NOT NULL, FK → vault_items    | Item do cofre compartilhado |
| `shared_with_user_id` | `uuid`    | NOT NULL, FK → users          | Usuário que recebeu acesso |

**Relacionamentos:**
- `vault_item_shares.vault_item_id` → `vault_items.id` (N:1)
- `vault_item_shares.shared_with_user_id` → `users.id` (N:1)

### Considerações de Segurança do Vault

- A posse do JWT autenticado não é suficiente para ler segredos do cofre.
- A leitura de `secret_content` e de anexos exige sessão com 2FA validado.
- Após reset de 2FA, o backend revoga imediatamente o acesso sensível validando o estado atual do usuário no banco.

---

## Observações

- Todas as PKs são `UUID` geradas pelo Hibernate via `GenerationType.UUID` (RFC 4122).
- O campo `password_hash` **nunca** deve armazenar senha em texto puro.
- O campo `totp_secret` é criptografado em nível de aplicação antes da persistência.
- O fluxo de recuperação do 2FA utiliza `recovery_code_hash` + `recovery_code_expires_at`, evitando armazenamento do código temporário em texto puro.
- O campo `specifications` usa `jsonb` (PostgreSQL), que permite indexação GIN para consultas sobre as chaves JSON.
- As migrações DDL ficam em `api/src/main/resources/db/migration/` seguindo o padrão `V{versão}__{descricao}.sql`.
