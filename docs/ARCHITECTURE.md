# Arquitetura do Sistema — Inovare TI

Este documento descreve a arquitetura técnica do sistema ITSM Inovare TI, as decisões de design adotadas e o planejamento de infraestrutura para produção.

---

## Visão Geral: Arquitetura em 3 Camadas

O sistema é dividido em três camadas independentes, cada uma rodando em seu próprio container Docker:

```
┌────────────────────────────────────────────────────────────────┐
│                         CLIENTE (Navegador)                    │
└────────────────────────┬───────────────────────────────────────┘
                         │ HTTP (porta 5173)
┌────────────────────────▼───────────────────────────────────────┐
│           CAMADA 1 — Frontend SPA (React + Nginx)              │
│  • React 19 + TypeScript + Vite 6 + Tailwind CSS               │
│  • React Router v7 com Lazy Loading por rota                   │
│  • Gerenciamento de estado via React Context (AuthContext)      │
│  • Comunicação com a API via Axios (services/api.ts)           │
└────────────────────────┬───────────────────────────────────────┘
                         │ HTTP/REST + JWT (porta 8085)
┌────────────────────────▼───────────────────────────────────────┐
│           CAMADA 2 — Backend API (Spring Boot)                 │
│  • Java 21 + Spring Boot 4 + Spring Security                   │
│  • Autenticação stateless via JWT (auth0 java-jwt 4.4)         │
│  • Arquitetura por domínio: ticket, inventory, asset, user...  │
│  • Casos de uso isolados (UseCase classes) por operação        │
│  • Upload de arquivos salvo em volume Docker (/app/uploads)    │
│  • Discord Bot (JDA 5) com inicialização assíncrona            │
└────────────────────────┬───────────────────────────────────────┘
                         │ JDBC / PostgreSQL protocol (porta 5432)
┌────────────────────────▼───────────────────────────────────────┐
│           CAMADA 3 — Banco de Dados (PostgreSQL 16)            │
│  • Schema gerenciado exclusivamente pelo Flyway                │
│  • V1__init.sql — schema completo (tabelas, índices, FKs)      │
│  • V2__insert_dev_data.sql — dados de desenvolvimento          │
│  • Persistência garantida via volume Docker (postgres_data)    │
└────────────────────────────────────────────────────────────────┘
```

---

## Infraestrutura Docker Compose

O arquivo `docker-compose.yml` define três serviços que se comunicam por uma rede privada interna:

### Serviços

| Serviço  | Container          | Imagem / Build         | Porta exposta |
|----------|--------------------|------------------------|---------------|
| `db`     | `inovareti_db`     | `postgres:16-alpine`   | `5436:5432`   |
| `api`    | `inovareti_api`    | Build local `./api`    | `8085:8085`   |
| `front`  | `inovareti_front`  | Build local `./front`  | `5173:80`     |

### Rede

Todos os serviços estão conectados à rede `inovare_network` (driver `bridge`). A comunicação interna entre `api` e `db` ocorre pelo hostname `db` na porta padrão `5432` — sem exposição desnecessária ao host.

```yaml
networks:
  inovare_network:
    driver: bridge
```

### Volumes de Persistência

| Volume          | Finalidade                                                    |
|-----------------|---------------------------------------------------------------|
| `postgres_data` | Dados do PostgreSQL — sobrevive a restarts e rebuilds         |
| `./uploads`     | Arquivos enviados pelos usuários (NFs, anexos de chamados)    |

O volume de uploads é montado como bind mount (`./uploads:/app/uploads`), tornando os arquivos acessíveis diretamente no host para backup.

### Healthcheck e Ordem de Inicialização

O serviço `api` só inicia após o `db` ser declarado **healthy** pelo `pg_isready`. Isso elimina race conditions que causariam falhas de conexão na inicialização da aplicação.

```yaml
depends_on:
  db:
    condition: service_healthy
```

---

## Estrutura Interna do Backend

O backend segue uma organização por **domínio** (Domain-Driven Design simplificado):

```
br.dev.ctrls.inovareti/
├── config/         # SecurityConfig, WebMvcConfig, CORS
├── core/           # Tratamento global de erros (GlobalExceptionHandler), auth filter
├── domain/
│   ├── auth/       # AuthController, TokenService (JWT)
│   ├── ticket/     # Ticket, TicketController, usecase/ (ClaimTicket, ResolveTicket...)
│   ├── inventory/  # Item, StockBatch, StockMovement, ItemController
│   ├── asset/      # Asset, AssetMaintenance, AssetController
│   ├── user/       # User, UserController, UserRepository
│   ├── notification/ # Notification, NotificationController
│   ├── knowledge/  # Article, ArticleController
│   ├── report/     # ReportController (exportação XLSX)
│   └── settings/   # SystemSettings, SettingsController
├── infra/
│   ├── discord/    # DiscordBotService, DiscordWebhookService
│   └── storage/    # LocalFileStorageService
└── InovareTiApplication.java
```

Cada operação de negócio relevante é encapsulada em uma classe **UseCase** dedicada (ex: `ClaimTicketUseCase`, `ResolveTicketUseCase`, `TransferTicketUseCase`), mantendo o controller fino e o serviço testável de forma isolada.

---

## Decisões Técnicas Recentes (Fases 1–4)

### 1. Flyway substituindo `ddl-auto` do Hibernate

O `spring.jpa.hibernate.ddl-auto` está configurado como `validate` — o Hibernate **apenas valida** o schema, sem criar ou alterar tabelas. Toda evolução do banco é feita exclusivamente via migrações Flyway versionadas (`V1__init.sql`, `V2__insert_dev_data.sql`).

**Motivação:** controle total sobre o schema em produção, histórico auditável de mudanças e eliminação de riscos de perda de dados por auto-migração.

### 2. Inicialização Assíncrona do Discord Bot

O `DiscordBotService` (JDA 5) é inicializado de forma **assíncrona** em uma thread separada ao subir a aplicação. Isso evita que uma falha de conectividade com o Discord (token inválido, serviço indisponível) bloqueie ou derrube toda a API Spring Boot.

```java
// O bot falha graciosamente sem impactar o startup da aplicação
@EventListener(ApplicationReadyEvent.class)
public void initialize() {
    CompletableFuture.runAsync(this::startBot);
}
```

### 3. Lazy Loading nas Rotas do React

Todas as páginas do frontend, exceto `Login` e `PrimeiroAcesso`, são carregadas com `React.lazy()` + `Suspense`. Cada rota gera um chunk JavaScript separado pelo Vite, reduzindo o bundle inicial e melhorando o tempo de carregamento para o usuário final.

### 4. Segurança com Spring Security + JWT Stateless

- Autenticação 100% stateless via JWT Bearer Token (expiração: 8h).
- Autorização granular por `@PreAuthorize` nos endpoints críticos.
- Senhas armazenadas exclusivamente como hash BCrypt (custo 10).
- TOTP (dois fatores) preparado no schema (`totp_secret` na tabela `users`).

---

## Módulos do Frontend

| Rota                        | Módulo / Página        | Acesso                       |
|-----------------------------|------------------------|------------------------------|
| `/dashboard`                | Dashboard              | Todos os usuários autenticados |
| `/tickets`                  | Lista de chamados      | USER vê apenas seus próprios |
| `/tickets/:id`              | Detalhe do chamado     | Dono ou ADMIN/TECHNICIAN     |
| `/inventory`                | Inventário / Itens     | ADMIN / TECHNICIAN           |
| `/assets`                   | Ativos patrimoniais    | ADMIN / TECHNICIAN           |
| `/users`                    | Gestão de usuários     | ADMIN                        |
| `/settings`                 | Configurações sistema  | ADMIN                        |
| `/knowledge-base`           | Base de conhecimento   | Todos os usuários autenticados |

---

## Planejamento de Deploy em Produção

O deploy em produção será realizado em um **home server** (Linux) com exposição segura à internet via **Cloudflare Tunnel** — sem necessidade de abrir portas no roteador ou usar IP público fixo.

```
Internet
   │
   ▼
Cloudflare Edge  ──── Cloudflare Tunnel (cloudflared daemon)
                                │
                                ▼
                      Home Server (Linux)
                      Docker Compose (inovare_network)
                      ├── inovareti_front  :5173
                      ├── inovareti_api    :8085
                      └── inovareti_db     :5432 (interno)
```

**Vantagens desta abordagem:**
- Sem IP público fixo necessário.
- TLS/HTTPS gerenciado automaticamente pela Cloudflare.
- O banco de dados fica completamente fora da internet (acesso apenas interno).
- Zero-Trust Access pode ser habilitado para restringir usuários via Cloudflare Access.
