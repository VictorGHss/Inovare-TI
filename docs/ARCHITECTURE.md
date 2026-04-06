# Arquitetura do Sistema — Inovare TI

Este documento descreve a arquitetura técnica do sistema ITSM Inovare TI, decisões de design e o planejamento de infraestrutura para produção.

---

## Visão Geral: Arquitetura em 3 Camadas

O sistema é dividido em três camadas independentes, cada uma rodando em container Docker:

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
│  • 2FA via TOTP integrada ao fluxo de autenticação              │
│  • Arquitetura por domínio (tickets, inventory, vault, etc.)   │
│  • Uploads salvos em volume Docker (/app/uploads)              │
│  • Discord Bot (JDA 5) inicializado assíncronamente           │
└────────────────────────┬───────────────────────────────────────┘
                         │ JDBC / PostgreSQL protocol (porta 5432)
┌────────────────────────▼───────────────────────────────────────┐
│           CAMADA 3 — Banco de Dados (PostgreSQL 16)            │
│  • Schema gerenciado pelo Flyway (V1, V2, ... )                │
│  • Migrações versionadas para controle em produção            │
│  • Persistência via volume Docker (postgres_data)              │
└────────────────────────────────────────────────────────────────┘
```

---

## Infraestrutura Docker Compose

O `docker-compose.yml` define os serviços e a rede `inovare_network` (driver `bridge`). A comunicação entre API e DB usa o hostname `db` internamente.

### Serviços (resumo)

| Serviço       | Container               | Build / Imagem              | Porta local                                 |
|---------------|-------------------------|-----------------------------|---------------------------------------------|
| `db`          | `inovareti_db`          | `postgres:16-alpine`        | `5436:5432`                                 |
| `api`         | `inovareti_api`         | Build local `./api`         | `8085:8085`                                 |
| `front`       | `inovareti_front`       | Build local `./front`       | `5173:80`                                   |
| `redis`       | `inovareti_redis`       | `redis:alpine`              | `6380:6379` (acesso interno: `redis:6379`) |
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

O volume `./uploads` é montado como bind mount (`./uploads:/app/uploads`) para facilitar backup local em dev.

Infraestrutura adicional:

- Redis: O `docker-compose.yml` inclui o serviço `redis` utilizado como camada de estabilidade para caching e para o rate-limiter distribuído (`RedisRateLimiter`) usado pelo endpoint administrativo de ContaAzul. A aplicação obtém host/porta via variáveis de ambiente (`SPRING_REDIS_HOST`, `SPRING_REDIS_PORT`) e registra o bean `StringRedisTemplate` quando configurado. O Redis está mapeado em `6380:6379` no host para evitar conflitos locais; internamente a API usa `redis:6379`.
- Prometheus: o projeto expõe métricas via Micrometer/Actuator em `/api/actuator/prometheus`. Para facilitar desenvolvimento local, o repositório agora inclui um serviço `prometheus` no `docker-compose.yml` com mapeamento `9095:9090` (acessível em `http://localhost:9095`). As regras de alerta estão em `docs/prometheus/alert.rules.yml`.

### Healthcheck e ordem de inicialização

O serviço `api` depende do `db` ficar healthy (ex: `pg_isready`) para evitar race conditions.

```yaml
depends_on:
  db:
    condition: service_healthy
```

---

## Evolução Arquitetural Pós-Refatoração Modular

Esta seção documenta a estrutura atual do sistema após a fase de modularização, sem substituir o histórico anterior.

### Camada de Serviços do Frontend

Antes, o frontend concentrava grande parte da comunicação HTTP em um fluxo mais acoplado ao cliente central. Agora, a organização segue um modelo de serviços por domínio:

- `services/api.ts`: client HTTP base (Axios), interceptors de autenticação e configuração comum.
- `services/ticketService.ts`: operações de tickets, comentários e anexos.
- `services/inventoryService.ts`: operações de ativos, itens e categorias.
- `services/financeService.ts`: operações financeiras e integrações do módulo financeiro.
- `services/userService.ts`: operações de usuários e gestão administrativa.

Resultado arquitetural:

- melhor separação de responsabilidades por domínio;
- menor acoplamento entre telas e detalhes de transporte HTTP;
- maior previsibilidade para testes, mocks e evolução incremental.

### Arquitetura Backend para Integrações Conta Azul

A integração com Conta Azul foi reorganizada para reduzir concentração de responsabilidades em uma única classe:

- `ContaAzulClient` permanece como fachada estável para o restante do sistema.
- `ContaAzulSalesClient` concentra consultas e fluxos de vendas.
- `ContaAzulFinancialClient` concentra baixa, recibo e operações financeiras.
- `ContaAzulCustomerClient` concentra pessoas/clientes e resolução de dados cadastrais.

Componentes transversais de suporte:

- `ContaAzulRequestExecutor`: execução HTTP com política comum de chamada.
- `ContaAzulResponseParser`: parsing e normalização de payloads de resposta.
- `ContaAzulHttpException`: encapsulamento consistente de falhas HTTP da integração.

Esse desenho preserva compatibilidade interna via fachada e melhora coesão dos clientes especializados.

### Separação de Responsabilidades no ReportService

O fluxo de relatórios foi dividido para aplicar SRP de forma explícita:

- `ReportService`: fachada de orquestração para exportação.
- `ReportPdfExporter`: geração de relatórios em PDF.
- `ReportExcelExporter`: geração de relatórios em Excel.
- `InventoryPricingService`: cálculo de valores/totais, desacoplado da renderização.

Com isso, a regra de cálculo deixa de competir com detalhes de formatação e saída de arquivo, facilitando manutenção e testes.

---

## Estrutura Interna do Backend

```
br.dev.ctrls.inovareti/
├── config/         # SecurityConfig, WebMvcConfig, CORS
├── core/           # Tratamento global de erros (GlobalExceptionHandler), auth filter
├── domain/
│   ├── auth/       # AuthController, TokenService, TOTP/2FA, recovery flow
│   ├── ticket/     # Ticket, TicketController, usecase/
│   ├── inventory/  # Item, StockBatch, StockMovement, ItemController
│   ├── asset/      # Asset, AssetMaintenance, AssetController
│   ├── user/       # User, UserController, UserRepository
│   ├── vault/      # VaultController, VaultService, itens sensíveis e compartilhamento
│   ├── audit/      # AuditLog, AuditLogService, AuditEvent, AuditLogController
│   ├── notification/ # Notification, NotificationController
│   └── settings/   # SystemSettings, SettingsController
├── infra/
│   ├── discord/    # DiscordBotService, DiscordWebhookService
│   ├── security/   # TwoFactorSessionGuard, EncryptionService
│   └── storage/    # LocalFileStorageService
└── InovareTiApplication.java
```

Cada caso de uso relevante é encapsulado em uma classe `UseCase` para manter controllers finos e serviços testáveis.

---

## Integrações Externas (resumo)

O backend integra com provedores externos para finanças, email e notificações.

- ContaAzul (OAuth2): tokens persistidos em `contaazul_oauth_tokens`, refresh proativo e automações para download/envio de recibos.
- Envio de emails (SMTP / JavaMailSender / Brevo): `FinanceEmailService` para envio de recibos com anexos.
- Discord (JDA 5): bot assíncrono para notificações e recuperação operacional.
- Vault (EncryptionService): criptografia AES-256/GCM para segredos armazenados.

As integrações ficam isoladas em `infra/` e `domain/*` para facilitar mocks e testes.

---

## Decisões Técnicas Relevantes

1. Flyway para migrações (Hibernate `ddl-auto` = `validate`).
2. Discord Bot inicializado de forma assíncrona para não bloquear startup.
3. Lazy loading das rotas no frontend para reduzir bundle inicial.
4. Segurança: JWT stateless, roles granulares e TOTP integrado ao fluxo.

```java
@EventListener(ApplicationReadyEvent.class)
public void initialize() {
    CompletableFuture.runAsync(this::startBot);
}
```

---

## Camada de Segurança e Vault

O sistema combina criptografia, 2FA e auditoria para proteger dados sensíveis:

- Cofre (`Vault`) com itens criptografados (AES-256/GCM).
- JWT com claim `two_factor_verified` para acesso a recursos sensíveis.
- Recuperação assistida via Discord: código temporário enviado por DM.
- Revogação imediata de acesso após reset do 2FA.

---

## Auditoria e Trilha de Compliance

Eventos do Spring são publicados (`AuditEvent`) e processados por listeners assíncronos que gravam em `audit_logs`.

| Ação                      | Gatilho                                      |
|---------------------------|----------------------------------------------|
| `VAULT_SECRET_VIEW`       | Leitura do conteúdo secreto de um item       |
| `VAULT_FILE_VIEW`         | Visualização de anexo do Vault               |
| `VAULT_ITEM_CREATE`       | Criação de item no cofre                     |
| `LOGIN_SUCCESS`           | Login autenticado com sucesso                |
| `LOGIN_FAILURE`           | Tentativa de login com credenciais inválidas |
| `TWO_FACTOR_RESET`        | Reset do 2FA via Discord                     |

Campos do `audit_logs`: `user_id`, `action`, `resource_type`, `resource_id`, `details` (JSON), `ip_address`, `created_at`.

---

## Serviços de Infraestrutura (detalhes técnicos)

Pacote: `api/src/main/java/br/dev/ctrls/inovareti/infra`

### LocalFileStorageService (`infra/storage`)

- Armazena arquivos no sistema de arquivos do servidor em diretório configurado por `file.upload-dir`.
- Gera nomes únicos usando `UUID` e valida extensão/segurança ao resolver caminhos.
- Limite de upload configurável via `app.upload.max-file-size-bytes` (padrão 5MB).
- Métodos: `store(MultipartFile)`, `load(String) -> Resource`, `delete(String)`.

### EncryptionService (`infra/security`)

- Implementa criptografia AES-GCM (AES/GCM/NoPadding) para proteger segredos do Vault.
- Deriva a chave a partir de `app.vault.encryption-key` usando SHA-256 (32 bytes).
- Gera IV de 12 bytes (SecureRandom) e inclui IV no payload codificado em Base64.
- Métodos: `encrypt(String)`, `decrypt(String)` e validações de integridade.

### TwoFactorSessionGuard (`infra/security`)

- Valida se a sessão atual foi marcada como `twoFactorVerified` (determinada pelo `SecurityFilter`).
- Confere também se o usuário ainda possui TOTP configurado no banco (`user.getTotpSecret()`).

Observações operacionais:

- A chave `app.vault.encryption-key` deve ser tratada como segredo e armazenada em Vault/Secret Manager.
- Rotacionar a chave exige procedimento de re-encrypt (documentar passos de migração).
- Em produção, recomenda-se usar S3/Blob Storage para uploads em vez de armazenamento local.

---

## Planejamento de Deploy

Deploy em produção sugerido via Cloudflare Tunnel para expor a aplicação sem IP público fixo.

Vantagens: TLS gerenciado pela Cloudflare, banco interno sem exposição pública e possibilidade de Zero-Trust Access.


