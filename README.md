# Inovare TI — Sistema ITSM para Clínica

Sistema de **Gerenciamento de Serviços de TI (ITSM)** desenvolvido para clínicas, com suporte a abertura e acompanhamento de chamados, gestão de inventário, controle de ativos, base de conhecimento e integração com Discord para notificações em tempo real.

---

## Visão Geral

O Inovare TI é uma aplicação web full-stack que centraliza toda a operação do suporte de TI interno:

- **Chamados (Tickets):** abertura, triagem, atribuição, resolução e SLA automático por categoria.
- **Inventário:** controle de estoque com lotes de compra, notas fiscais e movimentações.
- **Ativos:** registro e histórico de manutenção de equipamentos patrimoniais.
- **Base de Conhecimento:** artigos internos com tags e busca textual.
- **Notificações:** alertas em tempo real no sistema e via Discord Bot bidirecional.
- **Relatórios:** exportação de chamados e inventário para XLSX.

---

## Stack Tecnológica

| Camada      | Tecnologia                                                          |
|-------------|---------------------------------------------------------------------|
| Backend     | Java 21 · Spring Boot 4 · Spring Security · JWT (auth0 4.4.0)      |
| Persistência| PostgreSQL 16 · Spring Data JPA · Flyway (migrações)               |
| Frontend    | React 19 · TypeScript · Vite 6 · Tailwind CSS · React Router v7    |
| Integração  | Discord JDA 5 (Bot bidirecional) · Discord Webhooks                 |
| Infraestrutura | Docker · Docker Compose · Nginx (serve o SPA em produção)       |

---

## Pré-requisitos

- [Docker Desktop](https://www.docker.com/products/docker-desktop/) instalado e em execução.
- [Git](https://git-scm.com/) para clonar o repositório.
- Arquivo `.env` configurado na raiz do projeto (veja a seção abaixo).

---

## Configuração do Ambiente (`.env`)

Crie um arquivo `.env` na raiz do projeto com as variáveis abaixo. Valores de exemplo para desenvolvimento local estão indicados:

```env
# Banco de dados
POSTGRES_DB=inovareti
POSTGRES_USER=postgres
POSTGRES_PASSWORD=postgres

# JWT — use uma string longa e aleatória em produção
JWT_SECRET=MeuSegredoJWTSuperSegurogfdsInovareTI2025!

# Chave de criptografia para dados sensíveis
ENCRYPTION_SECRET=MyStrongDevEncryptionSecret2024!

# Discord (opcional em desenvolvimento — deixe em branco para desativar)
DISCORD_WEBHOOK_URL=
DISCORD_BOT_TOKEN=
DISCORD_BOT_ENABLED=false

# URL do frontend (usada nos links enviados ao Discord)
FRONTEND_URL=http://localhost:5173/
```

---

## Como Rodar o Projeto Localmente

Com o `.env` configurado, suba todos os serviços com um único comando na raiz do projeto:

```bash
docker compose up --build
```

O Docker Compose irá:
1. Subir o **banco PostgreSQL 16** na porta `5436`.
2. **Aguardar** o banco ficar saudável (healthcheck) antes de iniciar a API.
3. Subir a **API Spring Boot** na porta `8085`.
4. Subir o **frontend React** (via Nginx) na porta `5173`.

> Após o primeiro `up`, o Flyway executará automaticamente as migrações de banco de dados (**V1** — schema completo e **V2** — dados de desenvolvimento), sem necessidade de nenhuma ação manual.

Acesse a aplicação em: **http://localhost:5173**

---

## Credenciais de Desenvolvimento

As migrações do Flyway (V2) inserem os seguintes usuários de desenvolvimento automaticamente:

| Perfil        | E-mail                          | Senha      | Role         |
|---------------|---------------------------------|------------|--------------|
| Administrador | `admin@inovare.med.br`          | `admin123` | `ADMIN`      |
| Técnico       | `tecnico@inovare.med.br`        | `tech123`  | `TECHNICIAN` |
| Usuário comum | `joao.silva@inovare.med.br`     | `user123`  | `USER`       |

> **Atenção:** essas credenciais existem apenas em desenvolvimento. Em produção, o seeder é desativado (`app.seeder.enabled=false`) e os usuários devem ser criados manualmente pelo painel de administração.

---

## Estrutura do Projeto

```
Inovare-TI/
├── api/          # Backend Spring Boot (Java 21)
│   └── src/main/resources/db/migration/   # Migrações Flyway (V1, V2)
├── front/        # Frontend React + Vite
├── docs/         # Documentação técnica (ARCHITECTURE.md, API_DOCS.md)
├── docker-compose.yml
└── .env          # Variáveis de ambiente (não versionado)
```

---

## Documentação Adicional

- [Arquitetura do Sistema](docs/ARCHITECTURE.md)
- [Documentação da API REST](docs/API_DOCS.md)
- [Banco de Dados — Schema](docs/DATABASE.md)
- [TODO e Roadmap](docs/TODO.md)
