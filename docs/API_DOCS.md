# Documentação da API — Inovare TI

> **Base URL:** `http://localhost:8080`  
> **Formato:** JSON (`Content-Type: application/json`)  
> **Erros:** Padrão RFC 7807 (Problem Details)  
> **Autenticação:** JWT Bearer Token — envie `Authorization: Bearer <token>` em todas as rotas protegidas.

---

## Formato de Erro Padrão

Todos os erros retornam um objeto no formato RFC 7807:

```json
{
  "type": "about:blank",
  "title": "Descrição do erro",
  "status": 400,
  "detail": "Mensagem detalhada",
  "instance": "/api/sectors"
}
```

Erros de validação (400) incluem um campo extra `errors` com os campos inválidos:

```json
{
  "title": "Erro de validação",
  "status": 400,
  "errors": {
    "name": "O nome do setor é obrigatório."
  }
}
```

---

## Autenticação

Todas as rotas da API, **exceto** `POST /api/auth/login`, exigem autenticação via JWT.

### Como enviar o token

Após obter o token no endpoint de login, inclua-o no cabeçalho `Authorization` de todas as requisições:

```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

> O token expira em **8 horas**. Após a expiração realize um novo login.

---

## Módulo: Autenticação (`/api/auth`)

### `POST /api/auth/login`

Autentica um usuário e retorna um token JWT.

**Request Body:**

```json
{
  "email": "joao.silva@inovareti.dev",
  "password": "senhaSegura123"
}
```

| Campo      | Tipo   | Obrigatório | Restrições                 |
|------------|--------|-------------|----------------------------|
| `email`    | string | Sim         | Formato e-mail válido      |
| `password` | string | Sim         | Texto puro (nunca gravado) |

**Resposta de Sucesso — `200 OK`:**

```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJqb2FvLnNpbHZhQGlub3ZhcmV0aS5kZXYiLCJpc3MiOiJpbm92YXJlLXRpIiwiZXhwIjoxNzQwOTYyMDAwfQ.assinatura"
}
```

**Respostas de Erro:**

| Código | Situação                                         |
|--------|--------------------------------------------------|
| `400`  | Campos ausentes ou e-mail inválido               |
| `403`  | Credenciais incorretas (e-mail ou senha errados) |

---

## Módulo: Setores (`/api/sectors`)

### `POST /api/sectors`

Cria um novo setor.

**Request Body:**

```json
{
  "name": "Tecnologia da Informação"
}
```

| Campo  | Tipo   | Obrigatório | Restrições          |
|--------|--------|-------------|---------------------|
| `name` | string | Sim         | Máximo 100 chars    |

**Resposta de Sucesso — `201 Created`:**

```json
{
  "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "name": "Tecnologia da Informação"
}
```

**Respostas de Erro:**

| Código | Situação                              |
|--------|---------------------------------------|
| `400`  | Campo `name` ausente ou inválido      |
| `409`  | Já existe um setor com o mesmo nome   |

---

### `GET /api/sectors`

Retorna todos os setores cadastrados.

**Resposta de Sucesso — `200 OK`:**

```json
[
  {
    "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "name": "Tecnologia da Informação"
  },
  {
    "id": "b2c3d4e5-f6a7-8901-bcde-fa2345678901",
    "name": "Recursos Humanos"
  }
]
```

> Retorna lista vazia `[]` caso não haja setores cadastrados.

---

## Módulo: Categorias de Chamado (`/api/ticket-categories`)

### `POST /api/ticket-categories`

Cria uma nova categoria de chamado com SLA base definido.

**Request Body:**

```json
{
  "name": "Suporte Hardware",
  "baseSlaHours": 8
}
```

| Campo          | Tipo    | Obrigatório | Restrições             |
|----------------|---------|-------------|------------------------|
| `name`         | string  | Sim         | Máximo 100 chars       |
| `baseSlaHours` | integer | Sim         | Mínimo 1               |

**Resposta de Sucesso — `201 Created`:**

```json
{
  "id": "c3d4e5f6-a7b8-9012-cdef-ab3456789012",
  "name": "Suporte Hardware",
  "baseSlaHours": 8
}
```

**Respostas de Erro:**

| Código | Situação                                       |
|--------|------------------------------------------------|
| `400`  | Campos ausentes, inválidos ou `baseSlaHours` < 1 |
| `409`  | Já existe uma categoria com o mesmo nome       |

---

### `GET /api/ticket-categories`

Retorna todas as categorias de chamado cadastradas.

**Resposta de Sucesso — `200 OK`:**

```json
[
  {
    "id": "c3d4e5f6-a7b8-9012-cdef-ab3456789012",
    "name": "Suporte Hardware",
    "baseSlaHours": 8
  },
  {
    "id": "d4e5f6a7-b8c9-0123-defa-bc4567890123",
    "name": "Acesso e Redes",
    "baseSlaHours": 4
  }
]
```

> Retorna lista vazia `[]` caso não haja categorias cadastradas.

---

## Módulo: Categorias de Item de Inventário (`/api/item-categories`)

### `POST /api/item-categories`

Cria uma nova categoria de item de inventário.

**Request Body:**

```json
{
  "name": "Notebook",
  "isConsumable": false
}
```

| Campo          | Tipo    | Obrigatório | Restrições          |
|----------------|---------|-------------|---------------------|
| `name`         | string  | Sim         | Máximo 100 chars    |
| `isConsumable` | boolean | Sim         | `true` ou `false`   |

**Resposta de Sucesso — `201 Created`:**

```json
{
  "id": "e5f6a7b8-c9d0-1234-efab-cd5678901234",
  "name": "Notebook",
  "isConsumable": false
}
```

**Respostas de Erro:**

| Código | Situação                                     |
|--------|----------------------------------------------|
| `400`  | Campos ausentes ou inválidos                 |
| `409`  | Já existe uma categoria com o mesmo nome     |

---

### `GET /api/item-categories`

Retorna todas as categorias de item de inventário cadastradas.

**Resposta de Sucesso — `200 OK`:**

```json
[
  {
    "id": "e5f6a7b8-c9d0-1234-efab-cd5678901234",
    "name": "Notebook",
    "isConsumable": false
  },
  {
    "id": "f6a7b8c9-d0e1-2345-fabc-de6789012345",
    "name": "Cabo USB",
    "isConsumable": true
  }
]
```

> Retorna lista vazia `[]` caso não haja categorias cadastradas.

---

## Módulo: Usuários (`/api/users`)

### `POST /api/users`

Cria um novo usuário. A senha é recebida em texto puro e armazenada como hash BCrypt.

**Request Body:**

```json
{
  "name": "João Silva",
  "email": "joao.silva@inovareti.dev",
  "password": "senhaSegura123",
  "role": "ADMIN",
  "sectorId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "location": "Sala 101 - Bloco A",
  "discordUserId": "123456789012345678"
}
```

| Campo           | Tipo   | Obrigatório | Restrições                              |
|-----------------|--------|-------------|-----------------------------------------|
| `name`          | string | Sim         | Máximo 150 chars                        |
| `email`         | string | Sim         | Formato e-mail válido, máximo 255 chars |
| `password`      | string | Sim         | Mínimo 8 chars (nunca retornado)        |
| `role`          | string | Sim         | `ADMIN`, `DOCTOR` ou `SECRETARY`        |
| `sectorId`      | UUID   | Sim         | Deve referenciar um setor existente     |
| `location`      | string | Sim         | Máximo 150 chars                        |
| `discordUserId` | string | Não         | Máximo 50 chars                         |

**Resposta de Sucesso — `201 Created`:**

```json
{
  "id": "f1e2d3c4-b5a6-7890-fedc-ba0987654321",
  "name": "João Silva",
  "email": "joao.silva@inovareti.dev",
  "role": "ADMIN",
  "sectorId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "sectorName": "Tecnologia da Informação",
  "location": "Sala 101 - Bloco A",
  "discordUserId": "123456789012345678"
}
```

> Os campos `passwordHash` e `totpSecret` **nunca** são retornados pela API.

**Respostas de Erro:**

| Código | Situação                                                  |
|--------|-----------------------------------------------------------|
| `400`  | Campos obrigatórios ausentes, e-mail inválido ou senha < 8 chars |
| `404`  | O `sectorId` informado não existe                         |
| `409`  | Já existe um usuário com o mesmo e-mail                   |

---

### `GET /api/users`

Retorna todos os usuários cadastrados. O setor é carregado via JOIN para evitar N+1.

**Resposta de Sucesso — `200 OK`:**

```json
[
  {
    "id": "f1e2d3c4-b5a6-7890-fedc-ba0987654321",
    "name": "João Silva",
    "email": "joao.silva@inovareti.dev",
    "role": "ADMIN",
    "sectorId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "sectorName": "Tecnologia da Informação",
    "location": "Sala 101 - Bloco A",
    "discordUserId": "123456789012345678"
  }
]
```

> Retorna lista vazia `[]` caso não haja usuários cadastrados.

---

## Módulo: Itens de Inventário (`/api/items`)

### `POST /api/items`

Cria um novo item de inventário. O estoque inicial é sempre **zero**; use o endpoint de lotes para registrar entradas de estoque.

**Request Body:**

```json
{
  "itemCategoryId": "e5f6a7b8-c9d0-1234-efab-cd5678901234",
  "name": "Toner Brother HL-L2360DW",
  "specifications": {
    "marca": "Brother",
    "modelo": "TN-2370",
    "voltagem": "110V"
  }
}
```

| Campo            | Tipo   | Obrigatório | Restrições                                      |
|------------------|--------|-------------|-------------------------------------------------|
| `itemCategoryId` | UUID   | Sim         | Deve referenciar uma categoria existente        |
| `name`           | string | Sim         | Máximo 150 chars                                |
| `specifications` | object | Não         | JSON livre com atributos técnicos (chave-valor) |

**Resposta de Sucesso — `201 Created`:**

```json
{
  "id": "aa1b2c3d-e4f5-6789-abcd-ef0123456789",
  "itemCategoryId": "e5f6a7b8-c9d0-1234-efab-cd5678901234",
  "itemCategoryName": "Toner",
  "name": "Toner Brother HL-L2360DW",
  "currentStock": 0,
  "specifications": {
    "marca": "Brother",
    "modelo": "TN-2370",
    "voltagem": "110V"
  }
}
```

**Respostas de Erro:**

| Código | Situação                                          |
|--------|---------------------------------------------------|
| `400`  | Campos obrigatórios ausentes ou inválidos         |
| `404`  | O `itemCategoryId` informado não existe           |

---

### `GET /api/items`

Retorna todos os itens de inventário cadastrados. A categoria é carregada via JOIN FETCH para evitar N+1.

**Resposta de Sucesso — `200 OK`:**

```json
[
  {
    "id": "aa1b2c3d-e4f5-6789-abcd-ef0123456789",
    "itemCategoryId": "e5f6a7b8-c9d0-1234-efab-cd5678901234",
    "itemCategoryName": "Toner",
    "name": "Toner Brother HL-L2360DW",
    "currentStock": 50,
    "specifications": {
      "marca": "Brother",
      "modelo": "TN-2370",
      "voltagem": "110V"
    }
  }
]
```

> Retorna lista vazia `[]` caso não haja itens cadastrados.

---

### `POST /api/items/{id}/batches`

Registra um lote de entrada de estoque para o item especificado no path.
Operação atômica: cria o lote **e** atualiza o `currentStock` do item na mesma transação.

**Path Parameter:**

| Parâmetro | Tipo | Descrição         |
|-----------|------|-------------------|
| `id`      | UUID | ID do item        |

**Request Body:**

```json
{
  "quantity": 50,
  "unitPrice": 89.90
}
```

> O campo `itemId` é lido do path (`{id}`) e não precisa ser enviado no body.

| Campo       | Tipo    | Obrigatório | Restrições          |
|-------------|---------|-------------|---------------------|
| `quantity`  | integer | Sim         | > 0                 |
| `unitPrice` | number  | Sim         | > 0.00              |

**Resposta de Sucesso — `201 Created`:**

```json
{
  "id": "bb2c3d4e-f5a6-7890-bcde-fa1234567890",
  "itemId": "aa1b2c3d-e4f5-6789-abcd-ef0123456789",
  "itemName": "Toner Brother HL-L2360DW",
  "originalQuantity": 50,
  "remainingQuantity": 50,
  "unitPrice": 89.90,
  "entryDate": "2026-03-02T14:30:00"
}
```

> Após este endpoint, o `currentStock` do item será incrementado em `quantity` unidades.

**Respostas de Erro:**

| Código | Situação                                      |
|--------|-----------------------------------------------|
| `400`  | Campos obrigatórios ausentes ou `quantity` ≤ 0 |
| `404`  | O `id` do item não existe                     |

---

## Módulo: Chamados / Helpdesk (`/api/tickets`)

### `POST /api/tickets`

Abre um novo chamado. O `status` inicial é `OPEN` e o `slaDeadline` é calculado automaticamente
somando `baseSlaHours` da categoria ao momento da criação.

**Request Body:**

```json
{
  "title": "Impressora não responde na sala 202",
  "description": "A impressora HP LaserJet trava ao imprimir PDF.",
  "anydeskCode": "123 456 789",
  "priority": "HIGH",
  "requesterId": "f1e2d3c4-b5a6-7890-fedc-ba0987654321",
  "assignedToId": null,
  "categoryId": "c3d4e5f6-a7b8-9012-cdef-ab3456789012",
  "requestedItemId": null,
  "requestedQuantity": null
}
```

| Campo               | Tipo   | Obrigatório | Restrições                                              |
|---------------------|--------|-------------|---------------------------------------------------------|
| `title`             | string | Sim         | Máximo 200 chars                                        |
| `description`       | string | Não         | Texto livre                                             |
| `anydeskCode`       | string | Não         | Máximo 50 chars                                         |
| `priority`          | string | Sim         | `LOW`, `NORMAL`, `HIGH`, `URGENT`                       |
| `requesterId`       | UUID   | Sim         | Deve referenciar um usuário existente                   |
| `assignedToId`      | UUID   | Não         | Técnico responsável (pode ser atribuído depois)         |
| `categoryId`        | UUID   | Sim         | Deve referenciar uma categoria existente                |
| `requestedItemId`   | UUID   | Não         | Item de inventário solicitado                           |
| `requestedQuantity` | int    | Não         | Obrigatório se `requestedItemId` for informado; > 0     |

**Resposta de Sucesso — `201 Created`:**

```json
{
  "id": "aa11bb22-cc33-dd44-ee55-ff6677889900",
  "title": "Impressora não responde na sala 202",
  "description": "A impressora HP LaserJet trava ao imprimir PDF.",
  "anydeskCode": "123 456 789",
  "status": "OPEN",
  "priority": "HIGH",
  "requesterId": "f1e2d3c4-b5a6-7890-fedc-ba0987654321",
  "requesterName": "João Silva",
  "assignedToId": null,
  "assignedToName": null,
  "categoryId": "c3d4e5f6-a7b8-9012-cdef-ab3456789012",
  "categoryName": "Suporte Hardware",
  "requestedItemId": null,
  "requestedItemName": null,
  "requestedQuantity": null,
  "slaDeadline": "2026-03-02T22:30:00",
  "createdAt": "2026-03-02T14:30:00",
  "closedAt": null
}
```

**Respostas de Erro:**

| Código | Situação                                                         |
|--------|------------------------------------------------------------------|
| `400`  | Campos obrigatórios ausentes ou inválidos                        |
| `404`  | `requesterId`, `assignedToId`, `categoryId` ou `requestedItemId` não existem |

---

### `PATCH /api/tickets/{id}/close`

Fecha um chamado. Se o chamado possuir `requestedItemId` e `requestedQuantity`, debita o
estoque do item de forma atômica (mesma transação).

**Path Parameter:**

| Parâmetro | Tipo | Descrição         |
|-----------|------|-------------------|
| `id`      | UUID | ID do chamado     |

Não requer body.

**Resposta de Sucesso — `200 OK`:**

```json
{
  "id": "aa11bb22-cc33-dd44-ee55-ff6677889900",
  "status": "CLOSED",
  "closedAt": "2026-03-02T16:45:00",
  "...": "demais campos do chamado"
}
```

> Após este endpoint, o `currentStock` do item referenciado é decrementado em `requestedQuantity`.

**Respostas de Erro:**

| Código | Situação                                                       |
|--------|----------------------------------------------------------------|
| `404`  | Chamado não encontrado                                         |
| `422`  | Estoque insuficiente para atender a quantidade solicitada      |

---

## Changelog

| Versão | Data       | Descrição                                                              |
|--------|------------|------------------------------------------------------------------------|
| 0.5.0  | 2026-03-02 | Fase 5 — Segurança JWT: login, SecurityFilter, rotas bloqueadas         |
| 0.4.1  | 2026-03-02 | Adiciona endpoint GET /api/items com JOIN FETCH evitando N+1           |
| 0.4.0  | 2026-03-02 | Fase 4 — Motor de chamados com baixa de estoque transacional           |
| 0.3.0  | 2026-03-02 | Fase 3 — Items de inventário e lotes de estoque (JSON specifications)  |
| 0.2.0  | 2026-03-02 | Fase 2 Parte 2 — Endpoint de Usuários + Segurança inicial (permitAll)  |
| 0.1.0  | 2026-03-02 | Fase 2 — Endpoints de Setores, Categorias de Ticket e Item             |
