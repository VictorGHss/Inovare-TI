# Documentação do Banco de Dados — Inovare TI

> **SGBD:** PostgreSQL  
> **Gerenciamento de migrações:** Flyway  
> **Convenção de nomenclatura:** snake_case para tabelas e colunas; UUIDs como chaves primárias.

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
| `role`            | `varchar(20)`  | NOT NULL              | Papel do usuário: `ADMIN`, `DOCTOR` ou `SECRETARY` |
| `sector_id`       | `uuid`         | NOT NULL, FK → sectors| Setor ao qual o usuário pertence                   |
| `location`        | `varchar(150)` | NOT NULL              | Localização física (sala, andar, unidade)          |
| `discord_user_id` | `varchar(50)`  | NULLABLE              | ID do usuário no Discord corporativo               |
| `totp_secret`     | `varchar(255)` | NULLABLE              | Segredo TOTP para 2FA (armazenado criptografado)   |

**Relacionamentos:**
- `users.sector_id` → `sectors.id` (N:1 — muitos usuários por setor)

**Enum `UserRole`:**

| Valor       | Descrição                                 |
|-------------|-------------------------------------------|
| `ADMIN`     | Administrador total do sistema            |
| `DOCTOR`    | Médico/profissional de saúde              |
| `SECRETARY` | Secretária / atendimento                  |

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

## Diagrama de Relacionamentos (Atualizado — Fase 3)

```
sectors          (1) ──< users            (N)
item_categories  (1) ──< items            (N)
items            (1) ──< stock_batches    (N)
```

As tabelas `ticket_categories` são independentes nesta fase e serão relacionadas a `tickets` na Fase 4.

---

## Observações

- Todas as PKs são `UUID` geradas pelo Hibernate via `GenerationType.UUID` (RFC 4122).
- O campo `password_hash` **nunca** deve armazenar senha em texto puro.
- O campo `totp_secret` deve ser criptografado em nível de aplicação antes de ser persistido (a ser implementado com `@Converter` JPA na Fase futura).
- O campo `specifications` usa `jsonb` (PostgreSQL), que permite indexação GIN para consultas sobre as chaves JSON.
- As migrações DDL ficam em `api/src/main/resources/db/migration/` seguindo o padrão `V{versão}__{descricao}.sql`.
