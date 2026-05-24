# 🏥 Inovare TI — Ecossistema Integrado ITSM

[![Java Version](https://img.shields.io/badge/Java-21-orange.svg?style=flat-square&logo=openjdk)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4-brightgreen.svg?style=flat-square&logo=springboot)](https://spring.io/projects/spring-boot)
[![React Version](https://img.shields.io/badge/React-19-blue.svg?style=flat-square&logo=react)](https://react.dev/)
[![Docker](https://img.shields.io/badge/Docker-Compose-blue.svg?style=flat-square&logo=docker)](https://www.docker.com/)
[![License](https://img.shields.io/badge/License-MIT-green.svg?style=flat-square)](LICENSE)

Sistema premium de **Gerenciamento de Serviços de TI (ITSM)** customizado para clínicas médicas. Centraliza o controle de chamados de suporte, gestão avançada de inventário e lotes (FIFO), custódia de credenciais seguras (Vault criptografado), base de conhecimento inteligente e automação de faturamento com integração direta com o ERP ContaAzul.

---

## 📋 Visão Geral

O Inovare TI é uma aplicação corporativa full-stack de alto padrão projetada para automatizar e blindar a operação de suporte tecnológico de ambientes de saúde:

*   **Motor de Chamados (Helpdesk)**: Abertura e triagem rápida com cálculo dinâmico de prazos de SLA baseados em categorias de serviços de TI.
*   **Inventário FIFO & Ativos**: Gestão rígida de estoque organizada por lotes de aquisição. Saídas de insumos realizam o consumo automático e transacional baseando-se no algoritmo FIFO (First-In, First-Out).
*   **Cofre Seguro (Vault)**: Proteção de credenciais e notas confidenciais utilizando criptografia simétrica robusta **AES-256-GCM** na camada de aplicação, blindada por barreira obrigatória de 2FA.
*   **Automação de Recibos**: Integração com ERP ContaAzul para monitorar faturamentos e despachar de forma autônoma e-mails de recibos fiscais com controle de idempotência.
*   **Auditoria Imutável 360**: Trilha assíncrona e desacoplada que registra ações críticas e logs de conformidade de acessos para conformidade LGPD.

---

## 🛠️ Stack Tecnológica do Ecossistema

| Camada | Tecnologia | Descrição |
| :--- | :--- | :--- |
| **Backend API** | `Java 21` · `Spring Boot 4` · `Spring Security` · `JWT` | Core robusto, stateless, com validação de privilégios em dois fatores. |
| **Persistência / Cache** | `PostgreSQL 16` · `Redis` · `Spring Data JPA` · `Flyway` | Banco relacional estruturado, migrações DDL controladas e cache/rate-limiting distribuído. |
| **Frontend SPA** | `React 19` · `TypeScript` · `Vite 6` · `React Router v7` | SPA ultra-rápido com lazy loading por rota e design system unificado. |
| **Integração Externa** | `Discord JDA 5` · `ContaAzul API` · `Brevo / SMTP` | Bot assíncrono para notificações e resets, faturamento no ERP e disparos SMTP corporativos. |
| **Monitoramento / SRE** | `Prometheus` · `Grafana` · `Micrometer Actuator` | Stack de observabilidade local integrada com dashboards de saúde no container. |

---

## 📂 Documentação Técnica Mestre

Toda a engenharia e governança de processos do sistema está organizada e mantida em apenas **cinco documentos canônicos** na pasta `docs/`. Clique abaixo para acessar os guias detalhados em português:

*   ### 🏗️ [Arquitetura e Modelo de Dados (docs/ARCHITECTURE.md)](docs/ARCHITECTURE.md)
    *Decisões arquiteturais de 3 camadas, configurações do Docker Compose, organograma de pacotes do Spring Boot, considerações do perímetro de segurança, além do **Dicionário Completo de Tabelas** e relacionamentos do PostgreSQL.*

*   ### 💻 [Guia do Desenvolvedor e Operações (docs/DEVELOPER_GUIDE.md)](docs/DEVELOPER_GUIDE.md)
    *Instruções de setup local, suítes de testes, mapeamento de variáveis do `.env`, tratamento de exceções (RFC 7807), SMTP, geração de PDFs, **Playbooks de SRE & Triagem de Incidentes** e a linha do tempo cronológica de fases do projeto.*

*   ### 🚀 [Catálogo de Funcionalidades (docs/FEATURES.md)](docs/FEATURES.md)
    *Especificações funcionais e regras de negócio do Vault com 2FA, do Helpdesk com SLA, da Base de Conhecimento, do PWA Mobile, além do detalhamento matemático e transacional do **Algoritmo FIFO de Estoque** e auditoria.*

*   ### 🔌 [Integrações de Serviços (docs/INTEGRATIONS.md)](docs/INTEGRATIONS.md)
    *Configurações, autenticação OAuth2 e fluxos técnicos de processamento da ContaAzul, conexões do Bot Discord (JDA) e regras do gateway Brevo.*

*   ### 📖 [Especificação de APIs REST (docs/API_DOCS.md)](docs/API_DOCS.md)
    *Documentação exaustiva de contratos, payloads e esquemas de requisição/resposta JSON para todos os controllers da aplicação.*

---

## ⚙️ Inicialização Rápida via Docker Compose

### 1) Arquivo de Configuração `.env`
Crie um arquivo `.env` na raiz do projeto contendo as chaves necessárias para execução local. Para detalhes técnicos sobre cada variável, consulte o [Guia do Desenvolvedor](docs/DEVELOPER_GUIDE.md).

```env
# Banco de Dados
POSTGRES_DB=inovareti
POSTGRES_USER=inovareti_user
POSTGRES_PASSWORD=change_this_secure_password
DB_URL=jdbc:postgresql://db:5432/inovareti

# Segurança
JWT_SECRET=SuaStringSeguraComMaisDe32CaracteresAleatorios
VAULT_ENCRYPTION_KEY=SuaChaveMestraDerivadaDe32BytesBase64

# Configurações Adicionais
API_PORT=8085
FRONTEND_URL=http://localhost:5173
```

### 2) Executar a Aplicação
Com o Docker Desktop ativo, acione o bootstrap completo do ecossistema a partir da raiz do repositório:
```bash
docker compose up --build
```
A aplicação subirá todas as dependências organizadas em contêineres e fará o build do frontend Nginx e da API Java automaticamente.
*   **Acesso ao Frontend SPA**: `http://localhost:5173`
*   **Acesso à API Backend**: `http://localhost:8085/api`
*   **PostgreSQL Externo**: `localhost:5436` (host local mapeado para o container)

---

## 🔑 Credenciais Padrão de Semente (`seed` dev profile)

Quando executado com o profile do Spring de desenvolvimento (`dev`) e a propriedade `app.seeder.enabled=true` ativa, o Flyway inicializa automaticamente três usuários de teste para homologação:

| Perfil de Acesso | E-mail de Login | Senha Provisória | Nível de Autoridade (Role) |
| :--- | :--- | :--- | :--- |
| **Administrador** | `admin@inovare.med.br` | `admin123` | `ROLE_ADMIN` |
| **Técnico de TI** | `tecnico@inovare.med.br` | `tech123` | `ROLE_TECHNICIAN` |
| **Usuário Final** | `joao.silva@inovare.med.br` | `user123` | `ROLE_USER` |

> [!CAUTION]
> Estas sementes de desenvolvimento são injetadas estritamente sob o profile `dev`. Em ambientes produtivos, o seeder automático de banco é desativado por padrão.

---

## 🛠️ Ferramentas Técnicas e Observabilidade

*   **Swagger Spec local via UI**:
    ```bash
    docker run --rm -p 8080:8080 -e SWAGGER_JSON=/usr/share/nginx/html/openapi.json -v "%CD%/docs":/usr/share/nginx/html:ro swaggerapi/swagger-ui
    ```
    Acesse em: `http://localhost:8080`
*   **Endpoints de Coleta Actuator**:
    *   Métricas Prometheus: `http://localhost:8085/api/actuator/prometheus` (coleta anônima liberada para o target do Prometheus).
    *   Painel de Saúde Geral: `http://localhost:8085/api/actuator/health` (restrito a usuários autenticados `ADMIN`).
