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
│  • 2FA via TOTP com sessão integrada ao JWT                    │
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
│   ├── auth/       # AuthController, TokenService, TOTP/2FA, recovery flow
│   ├── ticket/     # Ticket, TicketController, usecase/ (ClaimTicket, ResolveTicket...)
│   ├── inventory/  # Item, StockBatch, StockMovement, ItemController
│   ├── asset/      # Asset, AssetMaintenance, AssetController
│   ├── user/       # User, UserController, UserRepository
│   ├── vault/      # VaultController, VaultService, itens sensíveis e compartilhamento
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
- TOTP (dois fatores) ativo para validação de acesso a recursos sensíveis.

---

## Camada de Segurança de Dados

O sistema passou a operar com uma camada dedicada de segurança para dados sensíveis e autenticação reforçada. Essa camada combina criptografia forte, autenticação em dois fatores e recuperação operacional assistida por Discord.

### Componentes principais

- **Vault seguro** para armazenamento de credenciais, documentos e notas críticas.
- **Criptografia AES-256/GCM** para proteger conteúdos sensíveis persistidos.
- **JWT com claim `two_factor_verified`** para diferenciar sessão autenticada de sessão autenticada e validada em 2FA.
- **Discord Bot / JDA** como canal operacional para recuperação de acesso e notificações de reset administrativo do 2FA.

### Criptografia de credenciais com AES-256/GCM

O módulo de Vault foi projetado para armazenar dados sensíveis com criptografia em nível de aplicação. O conteúdo secreto (`secret_content`) é protegido com **AES-256/GCM**, garantindo:

- **Confidencialidade**: o conteúdo não é persistido em texto puro no banco.
- **Integridade autenticada**: o modo GCM detecta adulteração do ciphertext.
- **Desacoplamento da infraestrutura**: a chave de criptografia é carregada por variável de ambiente (`VAULT_ENCRYPTION_KEY`), sem fallback inseguro em código.

Essa abordagem é utilizada para credenciais, anotações sensíveis e demais dados privados do cofre, reduzindo o impacto de vazamento direto do banco de dados.

### 2FA integrado ao JWT

O fluxo de autenticação em dois fatores funciona em duas etapas:

1. O usuário realiza login com e-mail e senha.
2. Após validar o código TOTP, a API emite um novo JWT com a claim `two_factor_verified=true`.

Esse token passa a representar uma sessão autenticada e também validada em segundo fator. Recursos sensíveis, como leitura de segredos e anexos do Vault, exigem essa validação adicional.

### Discord como canal oficial de recuperação operacional

Além do TOTP, o sistema utiliza o Discord corporativo como canal oficial de recuperação assistida:

1. O usuário autenticado solicita recuperação do 2FA.
2. A API gera um código temporário, armazena apenas seu hash e envia a DM ao `discordUserId` vinculado.
3. O usuário confirma a operação com o código recebido e sua senha atual.

Esse fluxo reduz a dependência exclusiva do dispositivo autenticador e mantém dupla validação para operações de recuperação.

### Revogação imediata de acesso sensível

Quando o 2FA é resetado, seja pelo próprio usuário via recuperação ou por um administrador, o sistema aplica revogação imediata:

- o backend invalida o estado efetivo de 2FA ao limpar `totp_secret`;
- o guard de segurança do Vault valida o estado atual do usuário no banco, não apenas a claim do JWT;
- o frontend invalida localmente o estado `twoFactorVerified`, forçando nova configuração antes de liberar o cofre.

Isso impede que um token antigo continue acessando dados sensíveis após o reset do segundo fator.

---

## Identidade Visual Oficial

Os tokens visuais de referência da clínica para as próximas fases de interface são:

- **Primary:** `#ffa751`
- **Secondary:** `#ffd1a3`

Essas cores devem orientar a evolução do dashboard premium, componentes de destaque, estados visuais do Vault e demais superfícies de alto valor perceptivo.

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
| `/vault`                    | Cofre seguro           | ADMIN / TECHNICIAN           |

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
