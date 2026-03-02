# Documentação da API — Inovare TI

> **Base URL:** `http://localhost:8080`  
> **Formato:** JSON (`Content-Type: application/json`)  
> **Erros:** Padrão RFC 7807 (Problem Details)  
> **Autenticação:** Não implementada nesta fase.

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

## Changelog

| Versão | Data       | Descrição                                                            |
|--------|------------|----------------------------------------------------------------------|
| 0.2.0  | 2026-03-02 | Fase 2 Parte 2 — Endpoint de Usuários + Segurança inicial (permitAll) |
| 0.1.0  | 2026-03-02 | Fase 2 — Endpoints de Setores, Categorias de Ticket e Item           |
