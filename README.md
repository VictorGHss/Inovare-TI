# 🏥 Inovare TI — Landing Page e Guia Central

Sua solução unificada e integrada de gerenciamento de serviços e automação clínica.

[![Java Version](https://img.shields.io/badge/Java-21-orange.svg?style=flat-square&logo=openjdk)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen.svg?style=flat-square&logo=springboot)](https://spring.io/projects/spring-boot)
[![React Version](https://img.shields.io/badge/React-19-blue.svg?style=flat-square&logo=react)](https://react.dev/)
[![Docker](https://img.shields.io/badge/Docker-Compose-blue.svg?style=flat-square&logo=docker)](https://www.docker.com/)

---

## 🌟 Propósito do Ecossistema

O **Inovare-TI** é uma solução corporativa unificada de **ITSM (IT Service Management)** projetada especificamente para ambientes de saúde e clínicas médicas de alto padrão. O ecossistema visa centralizar a gestão de suporte tecnológico, blindar processos operacionais e de estoque, e integrar os fluxos de relacionamento e faturamento da clínica em um perímetro robusto e escalável.

O ecossistema é governado por quatro pilares estratégicos:
*   **Gestão de Suporte (ITSM)**: Abertura e triagem estruturada de chamados de TI, cálculo dinâmico de conformidade de SLA e respostas instantâneas via Macros de 1-Clique.
*   **Motor de Agendamentos e WhatsApp**: Ingestão de consultas e automação de lembretes aos pacientes via Blip/Meta Cloud API, com Pacing não-bloqueante e anti-spam via agrupamento de lotes.
*   **Gestão Patrimonial e Estoque (CMDB)**: CMDB avançado com ativos de hardware multi-usuário vinculados a QR Codes e controle transacional rígido de insumos baseado na lógica contábil FIFO.
*   **Automação e Conciliação Financeira**: Integração contínua bidirecional com o ERP Conta Azul V2 para leitura de faturamentos, geração de recibos internos e despacho automático de PDFs via e-mail.

---

## 🛠️ Matriz Tecnológica (Tech Stack Core)

A arquitetura do Inovare-TI está estruturada sobre tecnologias estáveis, priorizando performance assíncrona, robustez contábil e isolamento de domínios:

| Camada | Tecnologia | Descrição / Finalidade |
| :--- | :--- | :--- |
| **Backend** | `Java 21` | Linguagem base moderna com suporte nativo a *Virtual Threads* para concorrência escalável. |
| **Backend** | `Spring Boot 3.x` | Framework mestre para injeção de dependências, controle transacional e REST API. |
| **Backend** | `Hibernate / JPA` | Abstração de persistência relacional e mapeamento objeto-relacional (ORM). |
| **Backend** | `Flyway` | Versionamento de banco de dados para controle de migrações DDL e sementes em ambientes. |
| **Backend** | `PostgreSQL 16` | Banco de dados relacional oficial do ecossistema, com suporte nativo a dados estruturados em JSONB. |
| **Backend** | `Redis` | Camada de cache distribuído de alta velocidade para armazenamento temporário e rate-limiting. |
| **Backend** | `Prometheus / Micrometer` | Coleta de telemetria técnica e exposição de métricas de SRE para observabilidade local. |
| **Frontend** | `React` | Biblioteca declarativa de alto padrão para estruturação de SPAs responsivas e modulares. |
| **Frontend** | `TypeScript` | Tipagem estática aplicada a interfaces e componentes, reduzindo falhas de dados em runtime. |
| **Frontend** | `Vite` | Ferramenta de build e desenvolvimento ultra-rápida baseada em ESM nativo com HMR. |
| **Frontend** | `Axios` | Cliente HTTP modular com suporte a interceptadores e renovação assíncrona de sessões JWT. |
| **Frontend** | `TailwindCSS / UI` | Design System customizado e baseado na paleta da Inovare (`#feb56c`), otimizada para CSS moderno. |

---

## 🗺️ Índice de Documentação (Atalhos Rápidos)

Toda a engenharia, runbooks de SRE, especificações técnicas e mapeamentos do banco de dados estão estruturados de forma especializada e podem ser acessados diretamente pelos atalhos abaixo:

*   🏗️ **[Arquitetura e Design de Software](file:///C:/Projeto/Inovare-TI/docs/ARCHITECTURE.md)**
    *Detalhamento do padrão de Arquitetura Hexagonal (Ports & Adapters) adotado nos pacotes do backend, diagrama de relacionamentos das tabelas do banco de dados PostgreSQL e planejamento estratégico de segurança de rede via túneis Cloudflare.*
*   🚀 **[Guia de Inicialização do Desenvolvedor (Setup Local)](file:///C:/Projeto/Inovare-TI/docs/DEVELOPER_GUIDE.md)**
    *Requisitos mínimos, inicialização da infraestrutura de containers via Docker Compose, configuração do arquivo de ambiente baseando-se no `.env.example`, comandos para compilação e execução de testes de integração (`mvn clean test`) e inicialização do front React (`npm run dev`).*
*   ⚙️ **[Regras de Negócio e Funcionalidades do Motor](file:///C:/Projeto/Inovare-TI/docs/FEATURES.md)**
    *Entendimento aprofundado das regras do Motor de Agendamentos (Pacing/Throttling com Virtual Threads do Java 21, Dilema de Segunda-Feira e a idempotência em lote com a tabela `NotificationGroup`), regras do cofre Vault criptografado, SLA de chamados e algoritmo FIFO de estoque.*
*   🔌 **[Manual de Integrações de APIs Externas](file:///C:/Projeto/Inovare-TI/docs/INTEGRATIONS.md)**
    *Mapeamento técnico e contratos de comunicação de APIs de parceiros: a estratégia de fallback em cascata de telefones e filtros de procedimentos na Feegow ERP, o transbordo via variáveis de contexto e Master-States na plataforma Blip/Meta, e a conciliação bancária periódica e manual na Conta Azul V2.*

---

## ⚙️ Inicialização Rápida

### 1) Configuração do Ambiente `.env`
Renomeie o modelo [.env.example](file:///C:/Projeto/Inovare-TI/.env.example) na raiz do projeto para `.env` e preencha as variáveis locais de banco, Redis e portas (consulte o [Guia de Inicialização](file:///C:/Projeto/Inovare-TI/docs/DEVELOPER_GUIDE.md) para detalhes):
```bash
cp .env.example .env
```

### 2) Subir o Ambiente Integrado
Para rodar toda a infraestrutura física (Banco Postgres, Redis e Prometheus) de suporte local:
```bash
docker compose up -d db redis prometheus
```

### 3) Execução do Backend & Frontend
*   **Compilar e testar backend Java**:
    ```bash
    cd api
    ./mvnw clean test
    ./mvnw spring-boot:run
    ```
*   **Instalar e rodar o frontend React**:
    ```bash
    cd front
    npm install
    npm run dev
    ```

---

## 🔑 Credenciais de Semente (`dev` profile)

Quando o backend é executado sob o perfil de desenvolvimento (`dev`) e a propriedade `app.seeder.enabled=true` está configurada, os seguintes usuários de teste são carregados no banco de dados:

| Especialidade | E-mail de Login | Senha Provisória | Perfil de Acesso |
| :--- | :--- | :--- | :--- |
| **Diretoria / Administrador** | `admin@inovare.med.br` | `admin123` | `ROLE_ADMIN` |
| **Suporte Técnico / TI** | `tecnico@inovare.med.br` | `tech123` | `ROLE_TECHNICIAN` |
| **Colaborador / Solicitante** | `joao.silva@inovare.med.br` | `user123` | `ROLE_USER` |

> [!CAUTION]
> Estas sementes são exclusivas para o perfil local `dev` e são ignoradas em ambientes de homologação e produção por diretivas de segurança do Flyway.
