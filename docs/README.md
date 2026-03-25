# Inovare TI — Sistema ITSM para Clínica

Sistema de **Gerenciamento de Serviços de TI (ITSM)** para clínicas: chamados, inventário, ativos, base de conhecimento e notificações (Discord).

---

## Visão Geral

O Inovare TI é uma aplicação web full-stack que centraliza a operação do suporte de TI interno: abertura e acompanhamento de chamados, gestão de inventário e ativos, automações financeiras e trilha de auditoria.

---

## Stack Tecnológica

| Camada      | Tecnologia                                                          |
|-------------|---------------------------------------------------------------------|
| Backend     | Java 21 · Spring Boot 4 · Spring Security · JWT                     |
| Persistência| PostgreSQL 16 · Spring Data JPA · Flyway (migrações)               |
| Frontend    | React 19 · TypeScript · Vite 6 · Tailwind CSS · React Router v7    |
| Integração  | Discord JDA 5 · Webhooks · ContaAzul (OAuth2)                      |
| Infra       | Docker · Docker Compose · Nginx (serve o SPA)                      |

---

## Pré-requisitos

- `Docker Desktop` instalado e em execução.
- `Git` para clonar o repositório.
- Crie um arquivo `.env` na raiz do projeto com as variáveis necessárias (veja `docs/DEVELOPER_GUIDE.md` para exemplos e orientações de segurança).

---

## Como Rodar (rápido)

Com o `.env` configurado, inicie todos os serviços:

```bash
docker compose up --build
```

O Compose:
1. Sobe o PostgreSQL (porta local padrão usada em dev).
2. Aguarda healthcheck antes de iniciar a API.
3. Sobe a API Spring Boot (porta 8085) e o frontend (porta 5173).

Acesse: **http://localhost:5173**

---

## Estrutura do Projeto

```
Inovare-TI/
├── api/          # Backend Spring Boot (Java 21)
├── front/        # Frontend React + Vite
├── docs/         # Documentação técnica consolidada
├── docker-compose.yml
└── .env          # Variáveis de ambiente (não versionado)
```

---

## Documentação Mestre

- Arquitetura e Infra: `docs/ARCHITECTURE.md`
- Guia do Desenvolvedor (configurações, JWT, exceções, SMTP): `docs/DEVELOPER_GUIDE.md`
- Integrações (ContaAzul, Discord, Vault): `docs/INTEGRATIONS.md`
- Operações & Runbooks (ContaAzul, métricas, throttling): `docs/OPERATIONS.md`
- Histórico do Projeto & Roadmap: `docs/PROJECT_HISTORY.md`

Outros arquivos de referência:
- Especificação OpenAPI: `docs/openapi.yaml` / `docs/openapi.json`
- Banco de dados: `docs/DATABASE.md`

---

## Observações

- Para detalhes sensíveis (credenciais, chaves, procedimentos de rotação), consulte `docs/DEVELOPER_GUIDE.md` e mantenha segredos fora do repositório (Vault / Secrets Manager).
- A documentação foi consolidada em arquivos-mestre para reduzir duplicidade e simplificar navegação.
