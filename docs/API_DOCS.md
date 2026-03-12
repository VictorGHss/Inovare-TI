# Documentação da API — Inovare TI

> **Base URL:** `http://localhost:8085`  
> **Formato:** JSON (`Content-Type: application/json`)  
> **Erros:** Padrão RFC 7807 (Problem Details)  
> **Autenticação:** JWT Bearer Token — envie `Authorization: Bearer <token>` em todas as rotas protegidas.

---

## Regras de Segurança

> **Atenção — Controle de Acesso nas Ações Críticas**
>
> Os endpoints a seguir possuem regras de autorização explícitas além da autenticação JWT básica:
>
> | Endpoint                             | Quem pode acessar                                                              |
> |--------------------------------------|--------------------------------------------------------------------------------|
> | `POST /api/auth/2fa/generate`        | Usuário autenticado                                                            |
> | `POST /api/auth/2fa/verify`          | Usuário autenticado                                                            |
> | `POST /api/auth/2fa/reset-request`   | Usuário autenticado com Discord vinculado                                      |
> | `POST /api/auth/2fa/reset-confirm`   | Usuário autenticado com código de recuperação válido                           |
> | `GET /api/vault`                     | **ADMIN** e **TECHNICIAN**                                                     |
> | `POST /api/vault`                    | **ADMIN** e **TECHNICIAN**                                                     |
> | `GET /api/vault/{itemId}/secret`     | **ADMIN** e **TECHNICIAN** com claim JWT `two_factor_verified=true`            |
> | `GET /api/vault/{itemId}/file`       | **ADMIN** e **TECHNICIAN** com claim JWT `two_factor_verified=true`            |
> | `PATCH /api/tickets/{id}/resolve`    | **ADMIN**, **TECHNICIAN** ou o próprio **dono do chamado** (`requesterId`)     |
> | `PATCH /api/tickets/{id}/claim`      | Exclusivo para **ADMIN** e **TECHNICIAN** (`@PreAuthorize`)                    |
> | `PATCH /api/tickets/{id}/transfer/{userId}` | Exclusivo para **ADMIN** e **TECHNICIAN** (`@PreAuthorize`)             |
> | `GET /api/notifications`             | Retorna **apenas as notificações do usuário autenticado** (proteção por propriedade) |
> | `GET /api/notifications/unread`      | Idem — notificações do usuário autenticado                                     |
> | `PATCH /api/notifications/{id}/read` | Só o **dono da notificação** pode marcá-la como lida                           |
> | `GET /api/tickets`                   | **ADMIN/TECHNICIAN** veem todos; **USER** vê apenas seus próprios chamados     |
>
> Tentativas de acesso não autorizado retornam **403 Forbidden**.



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

### Claim adicional para o Vault

Os endpoints sensíveis do Vault não exigem apenas autenticação JWT. Para leitura de segredos e anexos, o token precisa ter a claim abaixo marcada como verdadeira:

```json
{
  "two_factor_verified": true
}
```

Sem essa claim, ou se o 2FA tiver sido resetado posteriormente, a API retorna `403 Forbidden`.

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

### `POST /api/auth/2fa/generate`

Gera um novo segredo TOTP para o usuário autenticado e retorna os dados necessários para configurar o autenticador.

**Resposta de Sucesso — `200 OK`:**

```json
{
  "qrCodeBase64": "iVBORw0KGgoAAAANSUhEUgAA...",
  "otpauthUrl": "otpauth://totp/inovare-ti:usuario%40empresa.com?secret=ABC123...&issuer=inovare-ti"
}
```

### `POST /api/auth/2fa/verify`

Valida o código TOTP informado e retorna um novo JWT com a claim de 2FA verificado.

**Request Body:**

```json
{
  "code": "123456"
}
```

**Resposta de Sucesso — `200 OK`:**

```json
{
  "status": "AUTHENTICATED",
  "token": "<jwt-com-two-factor-verified>",
  "tempToken": null,
  "userId": null,
  "user": {
    "id": "0d4c7a45-2c88-4f0d-9ea5-61d18e3c0db1",
    "name": "João Silva",
    "email": "joao.silva@empresa.com",
    "role": "TECHNICIAN"
  }
}
```

### `POST /api/auth/2fa/reset-request`

Solicita a recuperação do 2FA. A API gera um código temporário, persiste seu hash no banco e envia a instrução de recuperação por DM no Discord ao usuário vinculado.

**Resposta de Sucesso — `204 No Content`**

**Respostas de Erro:**

| Código | Situação |
|--------|----------|
| `400`  | 2FA não ativado para o usuário |
| `400`  | Usuário sem `discordUserId` vinculado |

### `POST /api/auth/2fa/reset-confirm`

Confirma a recuperação do 2FA validando simultaneamente o código recebido e a senha atual do usuário.

**Request Body:**

```json
{
  "code": "A3BH7KWP",
  "password": "senhaAtual123"
}
```

**Resposta de Sucesso — `200 OK`:**

```json
{
  "status": "AUTHENTICATED",
  "token": "<jwt-sem-two-factor-verified>",
  "tempToken": null,
  "userId": null,
  "user": {
    "id": "0d4c7a45-2c88-4f0d-9ea5-61d18e3c0db1",
    "name": "João Silva",
    "email": "joao.silva@empresa.com",
    "role": "TECHNICIAN"
  }
}
```

**Respostas de Erro:**

| Código | Situação |
|--------|----------|
| `400`  | Código inválido, expirado ou não solicitado |
| `400`  | Senha atual incorreta |
| `400`  | 2FA já desativado |

---

## Módulo: Vault (`/api/vault`)

O módulo Vault protege segredos e anexos sensíveis. A leitura de conteúdo secreto e anexos exige JWT autenticado e sessão com `two_factor_verified=true`.

> **Importante:** a criação de itens aceita `multipart/form-data`, permitindo envio de JSON serializado no campo `payload` e anexo opcional no campo `file`.

### `GET /api/vault`

Lista os itens visíveis ao usuário autenticado com base em propriedade e regras de compartilhamento.

**Resposta de Sucesso — `200 OK`:**

```json
[
  {
    "id": "20a521cc-e6d1-447e-9628-3d7cdd87cf89",
    "title": "VPN Produção",
    "description": "Credencial do firewall principal",
    "itemType": "CREDENTIAL",
    "filePath": null,
    "ownerId": "0d4c7a45-2c88-4f0d-9ea5-61d18e3c0db1",
    "sharingType": "ALL_TECH_ADMIN",
    "createdAt": "2026-03-12T09:00:00",
    "updatedAt": "2026-03-12T09:00:00"
  }
]
```

### `POST /api/vault`

Cria um item no cofre com suporte a `multipart/form-data`.

**Partes esperadas:**

- `payload`: JSON serializado com os dados do item.
- `file`: anexo opcional.

**Exemplo do campo `payload`:**

```json
{
  "title": "Acesso Mikrotik",
  "description": "Credencial do roteador da recepção",
  "itemType": "CREDENTIAL",
  "secretContent": "usuario: admin / senha: ********",
  "sharingType": "CUSTOM",
  "sharedWithUserIds": [
    "f3c37b10-0fb5-438c-bc1f-a82118d011aa"
  ]
}
```

**Resposta de Sucesso — `201 Created`:**

```json
{
  "id": "20a521cc-e6d1-447e-9628-3d7cdd87cf89",
  "title": "Acesso Mikrotik",
  "description": "Credencial do roteador da recepção",
  "itemType": "CREDENTIAL",
  "filePath": "vault/2026/03/arquivo.pdf",
  "ownerId": "0d4c7a45-2c88-4f0d-9ea5-61d18e3c0db1",
  "sharingType": "CUSTOM",
  "createdAt": "2026-03-12T09:00:00",
  "updatedAt": "2026-03-12T09:00:00"
}
```

### `GET /api/vault/{itemId}/secret`

Retorna o conteúdo secreto descriptografado do item. Exige 2FA validado na sessão atual.

**Resposta de Sucesso — `200 OK`:**

```json
{
  "itemId": "20a521cc-e6d1-447e-9628-3d7cdd87cf89",
  "secretContent": "usuario: admin / senha: ********"
}
```

**Respostas de Erro:**

| Código | Situação |
|--------|----------|
| `403`  | Sessão sem 2FA validado |
| `403`  | 2FA resetado e acesso revogado |
| `404`  | Item não encontrado ou sem acesso |

### `GET /api/vault/{itemId}/file`

Retorna o anexo do item para visualização inline. Exige 2FA validado.

**Resposta de Sucesso — `200 OK`**

- `Content-Type`: inferido pelo backend (`image/png`, `video/mp4`, `application/pdf`, etc.)
- `Content-Disposition`: `inline`

**Respostas de Erro:**

| Código | Situação |
|--------|----------|
| `400`  | Item sem anexo |
| `403`  | Sessão sem 2FA validado |
| `403`  | 2FA resetado e acesso revogado |
| `404`  | Item não encontrado ou sem acesso |

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

### `GET /api/tickets/{id}`

Retorna os dados de um único chamado pelo UUID.

**Resposta de Sucesso — `200 OK`:** objeto `TicketResponseDTO` completo.

**Respostas de Erro:**

| Código | Situação               |
|--------|------------------------|
| `404`  | Chamado não encontrado |

---

### `PATCH /api/tickets/{id}/resolve`

> **Acesso:** ADMIN, TECHNICIAN ou o usuário que abriu o chamado (`requesterId`).

Resolve um chamado existente, alterando o status para `RESOLVED`. Se o chamado possuir `requestedItemId` e `requestedQuantity`, debita o estoque do item de forma atômica (mesma transação).

**Path Parameter:**

| Parâmetro | Tipo | Descrição         |
|-----------|------|-------------------|
| `id`      | UUID | ID do chamado     |

**Request Body (opcional):**

```json
{
  "resolutionNotes": "Substituído o toner da impressora. Problema resolvido."
}
```

**Resposta de Sucesso — `200 OK`:**

```json
{
  "id": "aa11bb22-cc33-dd44-ee55-ff6677889900",
  "status": "RESOLVED",
  "closedAt": "2026-03-11T16:45:00",
  "...": "demais campos do chamado"
}
```

> Se o chamado referenciar um item de inventário, o `currentStock` é decrementado em `requestedQuantity` atomicamente.

**Respostas de Erro:**

| Código | Situação                                                        |
|--------|-----------------------------------------------------------------|
| `403`  | Usuário não tem permissão (não é dono nem ADMIN/TECHNICIAN)    |
| `404`  | Chamado não encontrado                                          |
| `422`  | Estoque insuficiente para atender a quantidade solicitada       |

---

### `PATCH /api/tickets/{id}/claim`

> **Acesso:** Exclusivo para **ADMIN** e **TECHNICIAN**.

Assume o chamado para o usuário autenticado e altera o status para `IN_PROGRESS`.

**Path Parameter:**

| Parâmetro | Tipo | Descrição         |
|-----------|------|-------------------|
| `id`      | UUID | ID do chamado     |

Não requer body.

**Resposta de Sucesso — `200 OK`:** objeto `TicketResponseDTO` com `status: "IN_PROGRESS"` e `assignedToId` preenchido.

**Respostas de Erro:**

| Código | Situação                              |
|--------|---------------------------------------|
| `403`  | Role insuficiente (somente ADMIN/TECHNICIAN) |
| `404`  | Chamado não encontrado                |

---

### `PATCH /api/tickets/{id}/transfer/{userId}`

> **Acesso:** Exclusivo para **ADMIN** e **TECHNICIAN**.

Transfere o chamado para outro técnico/usuário. Se o chamado estiver `OPEN`, muda o status para `IN_PROGRESS`.

**Path Parameters:**

| Parâmetro | Tipo | Descrição                             |
|-----------|------|---------------------------------------|
| `id`      | UUID | ID do chamado                         |
| `userId`  | UUID | ID do usuário para quem será transferido |

Não requer body.

**Resposta de Sucesso — `200 OK`:** objeto `TicketResponseDTO` com `assignedToId` atualizado.

**Respostas de Erro:**

| Código | Situação                                      |
|--------|-----------------------------------------------|
| `403`  | Role insuficiente (somente ADMIN/TECHNICIAN)  |
| `404`  | Chamado ou usuário de destino não encontrado  |

---

### `POST /api/tickets/{id}/attachments`

Envia um arquivo como anexo ao chamado. Máximo **5 MB** por arquivo.

**Path Parameter:** `id` — UUID do chamado.

**Request:** `multipart/form-data` com o campo `file`.

**Resposta de Sucesso — `201 Created`:**

```json
{
  "id": "cc3d4e5f-...",
  "originalFilename": "nota-fiscal.pdf",
  "storedFilename": "uuid-gerado-internamente.pdf",
  "fileType": "application/pdf",
  "ticketId": "aa11bb22-...",
  "uploadedAt": "2026-03-11T10:00:00"
}
```

---

### `GET /api/tickets/{id}/attachments`

Lista todos os anexos de um chamado.

**Resposta de Sucesso — `200 OK`:** array de objetos `TicketAttachmentResponseDTO`.

---

### `POST /api/tickets/{id}/comments`

Adiciona um comentário a um chamado existente.

**Request Body:**

```json
{
  "content": "Verificado in loco — aguardando peça de reposição.",
  "authorId": "f1e2d3c4-b5a6-7890-fedc-ba0987654321"
}
```

**Resposta de Sucesso — `201 Created`:** objeto do comentário com `id`, `content`, `authorName` e `createdAt`.

---

### `GET /api/tickets/{id}/comments`

Lista todos os comentários de um chamado, ordenados por data crescente.

**Resposta de Sucesso — `200 OK`:** array de objetos `TicketCommentResponseDTO`.

---

## Módulo: Notificações (`/api/notifications`)

> **Segurança:** Todos os endpoints deste módulo retornam **apenas dados do usuário autenticado**. A proteção é feita por leitura do ID do usuário via JWT, garantindo isolamento total entre usuários.

### `GET /api/notifications`

Retorna todas as notificações do usuário autenticado (lidas e não lidas), ordenadas por data decrescente.

**Resposta de Sucesso — `200 OK`:**

```json
[
  {
    "id": "dd4e5f6a-...",
    "title": "Chamado #42 atribuído a você",
    "message": "O chamado 'Impressora offline' foi transferido para você.",
    "isRead": false,
    "link": "/tickets/aa11bb22-...",
    "createdAt": "2026-03-11T09:30:00"
  }
]
```

---

### `GET /api/notifications/unread`

Retorna apenas as notificações não lidas do usuário autenticado.

**Resposta de Sucesso — `200 OK`:** array de objetos `NotificationResponseDTO` com `isRead: false`.

---

### `PATCH /api/notifications/{id}/read`

> **Segurança:** Somente o **dono da notificação** pode marcá-la como lida. Tentativas de outro usuário resultam em `403 Forbidden`.

Marca uma notificação específica como lida.

**Path Parameter:** `id` — UUID da notificação.

Não requer body.

**Resposta de Sucesso — `200 OK`:** objeto `NotificationResponseDTO` com `isRead: true`.

**Respostas de Erro:**

| Código | Situação                                               |
|--------|--------------------------------------------------------|
| `403`  | A notificação pertence a outro usuário                 |
| `404`  | Notificação não encontrada                             |

---

## Changelog

| Versão | Data       | Descrição                                                                   |
|--------|------------|-----------------------------------------------------------------------------|
| 1.0.0  | 2026-03-11 | Documentação atualizada — Fases 1–4 concluídas, segurança e Flyway         |
| 0.6.0  | 2026-03-11 | Segurança granular: resolve/claim/transfer com roles; notificações por dono |
| 0.5.0  | 2026-03-02 | Fase 5 — Segurança JWT: login, SecurityFilter, rotas bloqueadas             |
| 0.4.1  | 2026-03-02 | Adiciona endpoint GET /api/items com JOIN FETCH evitando N+1                |
| 0.4.0  | 2026-03-02 | Fase 4 — Motor de chamados com baixa de estoque transacional                |
| 0.3.0  | 2026-03-02 — Fase 3 — Items de inventário e lotes de estoque (JSON specifications)   |
| 0.2.0  | 2026-03-02 | Fase 2 Parte 2 — Endpoint de Usuários + Segurança inicial (permitAll)       |
| 0.1.0  | 2026-03-02 | Fase 2 — Endpoints de Setores, Categorias de Ticket e Item                 |
