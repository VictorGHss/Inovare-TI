# Inovare TI — Sistema ITSM para Clínica

Sistema de **Gerenciamento de Serviços de TI (ITSM)** desenvolvido para clínicas, focado em centralizar chamados, inventário, ativos, base de conhecimento e automação financeira com notificações via Discord.

---

## 📋 Visão Geral

O Inovare TI é uma aplicação full‑stack que automatiza a operação do suporte de TI interno:

- **Chamados (Tickets):** Abertura, triagem, atribuição e SLA automático por categoria.
- **Inventário e Ativos:** Gestão de estoque com lotes, notas fiscais, movimentações e histórico de manutenção patrimonial.
- **Cofre Seguro (Vault):** Proteção de credenciais e documentos com criptografia AES‑256/GCM e acesso restrito por 2FA.
- **Automação Financeira:** Integração OAuth2 com ContaAzul para vínculo de clientes e envio automatizado de recibos.
- **Auditoria 360:** Trilha de compliance imutável registrando todas as ações críticas do sistema.

---

## 🛠️ Stack Tecnológica

| Camada       | Tecnologia                                                      |
|--------------|------------------------------------------------------------------|
| Backend      | Java 21 · Spring Boot 4 · Spring Security · JWT                 |
| Persistência / Cache | PostgreSQL 16 · Spring Data JPA · Flyway (migrações) · Redis (cache, usado para rate-limiting e throttling das APIs)            |
| Frontend     | React 19 · TypeScript · Vite 6 · Tailwind CSS · React Router v7 |
| Integração   | Discord JDA 5 (Bot bidirecional) · ContaAzul (OAuth2)          |
| Infra        | Docker · Docker Compose · Nginx (serve o SPA)                  |

---

## ⚙️ Configuração e Execução

### 1) Pré‑requisitos

- `Docker Desktop` em execução.
- `Git` para clonar o repositório.

### 2) Variáveis de Ambiente (`.env`)

Crie um arquivo `.env` na raiz do projeto conforme o modelo abaixo. Detalhes e recomendações de segurança em `docs/DEVELOPER_GUIDE.md`.

```env
# Banco de dados
POSTGRES_DB=inovareti
POSTGRES_USER=postgres
POSTGRES_PASSWORD=postgres

# Segurança
JWT_SECRET=SuaStringLongaE_Aleatoria_Para_Producao
ENCRYPTION_SECRET=ChaveMestraParaCriptografiaVault2024!

# Discord (Opcional)
DISCORD_BOT_TOKEN=seu_token_aqui
DISCORD_BOT_ENABLED=false

# URLs
FRONTEND_URL=http://localhost:5173/
```

### 3) Rodar o Projeto

Na raiz do projeto, suba os serviços com:

```bash
docker compose up --build
```

Observação: no `docker-compose.yml` o serviço PostgreSQL está mapeado para a porta externa `5436` (host:5436 -> container:5432). Para conectar localmente ao banco use `localhost:5436`.

Acesse a aplicação em: `http://localhost:5173`.

---

## 🔑 Credenciais de Desenvolvimento (seed)

Os usuários iniciais (Administrador, Técnico e Usuário) são criados automaticamente pelo `DatabaseSeeder` apenas quando a aplicação é executada com o profile de desenvolvimento (`dev`). Em ambientes de produção o seeder não é executado.

Exemplo de usuários criados no profile `dev`:

| Perfil        | E-mail                       | Senha     | Role        |
|---------------|------------------------------|-----------|-------------|
| Administrador | admin@inovare.med.br         | admin123  | ADMIN       |
| Técnico       | tecnico@inovare.med.br       | tech123   | TECHNICIAN  |
| Usuário       | joao.silva@inovare.med.br    | user123   | USER        |

> Atenção: esses usuários são inseridos somente em ambientes de desenvolvimento (profile `dev`). Em produção, o seeder não é executado.

---

## 📂 Documentação Mestre

A documentação técnica foi consolidada em arquivos temáticos — consulte:

- **Arquitetura e Infra:** `docs/ARCHITECTURE.md`
- **Guia do Desenvolvedor:** `docs/DEVELOPER_GUIDE.md` (configurações, JWT+2FA, SMTP, erros)
- **Integrações:** `docs/INTEGRATIONS.md` (ContaAzul, Discord, Vault)
- **Operações & Runbooks:** `docs/OPERATIONS.md` (refresh, métricas, backfill)
- **Banco de Dados:** `docs/DATABASE.md` (dicionário e migrações)
- **Documentação da API:** `docs/openapi.yaml` / `docs/openapi.json`

---

### Aviso rápido — Recibos ContaAzul

A geração do PDF do recibo após a baixa na Conta Azul é assíncrona. O sistema realiza até 20 tentativas automáticas antes de gerar um alerta operacional. Procedimentos de triagem, backfill e ações operacionais estão documentados em `docs/INTEGRATIONS.md` e `docs/OPERATIONS.md` (ver seção "Falha na Captura de Recibo (20 tentativas)").


## 🛠️ Ferramentas de Suporte

- **OpenAPI / Swagger:** servir a spec localmente via Docker:

```bash
docker run --rm -p 8080:8080 -e SWAGGER_JSON=/usr/share/nginx/html/openapi.json -v "%CD%/docs":/usr/share/nginx/html:ro swaggerapi/swagger-ui
```

- **Observability / Actuator:** endpoints expostos (a API roda com contexto `/api`):

  - Health: `/api/actuator/health`
  - Prometheus: `/api/actuator/prometheus`

---

Se precisar, eu posso abrir um PR com estas atualizações ou ajustar o texto para incluir links diretos a trechos específicos da documentação. 
