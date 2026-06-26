# Inovare TI — Centro de Operações e Automações

O ecossistema Inovare TI integra os sistemas de gestão da Clínica Inovare. Ele gerencia o agendamento de consultas, conciliação financeira de recebimentos e a infraestrutura de suporte tecnológico (ITSM/CMDB).

---

## Escopo do Sistema

A plataforma atua nas seguintes frentes operacionais:

1. **Motor de Agendamentos (Feegow + Blip):** Consome os agendamentos confirmados ou pendentes do ERP Feegow. Dispara lembretes e confirmações interativas via WhatsApp pela API do Take Blip/Meta, atualizando o status de volta no ERP com base na resposta do paciente.
2. **Automação Financeira & Payflow (Conta Azul):** Processa baixas de faturamentos quitados (`ACQUITTED`) na API Conta Azul V2. Garante o controle concorrente de faturamento e envia recibos assinados em PDF para os clientes por e-mail, utilizando mecanismos de contingência local em caso de indisponibilidade externa.
3. **Suporte de TI e Ativos (ITSM + CMDB):** Central de abertura e triagem de incidentes de TI da clínica. Vincula chamados de suporte a ativos físicos (impressoras, servidores), calcula SLAs dinâmicos, realiza baixa automática de peças por algoritmo FIFO e notifica a equipe técnica via canais e bot interativo do Discord.

---

## Sumário da Documentação

Acesse os documentos abaixo para obter detalhes técnicos específicos de cada área do sistema:

* [**Arquitetura e Modelo de Dados**](docs/ARCHITECTURE.md)
  Detalha o padrão Ports & Adapters (Arquitetura Hexagonal), o mapeamento de pacotes por domínio e o dicionário de banco de dados baseado nas migrações do Flyway, incluindo tabelas como `tickets`, `assets`, `asset_users` e `stock_movements`.
* [**Guia de Integração de APIs**](docs/INTEGRATIONS.md)
  Mapeia os fluxos e contratos de comunicação externa da plataforma com o Feegow ERP, APIs de mensageria Take Blip (comandos LIME e roteamento de triagem) e a API Conta Azul V2 (OAuth2, rate limiting com Redis e contingência de recibos).
* [**Catálogo de Funcionalidades e Regras**](docs/FEATURES.md)
  Documenta as regras de negócio implementadas nos Services e UseCases do backend. Explica a esteira de parada crítica para ativos sensíveis, a baixa de insumos via FIFO e os alertas de estoque mínimo (`min_stock`).
* [**Guia de Configuração e Desenvolvimento**](docs/DEVELOPER_GUIDE.md)
  Instruções para subir o ambiente local. Cobre a parametrização das variáveis de ambiente (`.env`), inicialização de serviços no Docker (PostgreSQL, Redis, Prometheus, Grafana) e os comandos para rodar o backend Spring Boot e o frontend React.

---

## Inicialização Rápida

Para iniciar a execução local dos serviços de infraestrutura e das aplicações:

```bash
# 1. Iniciar banco de dados (PostgreSQL), cache (Redis) e telemetria (Prometheus)
docker compose up -d db redis prometheus

# 2. Copiar e preencher as variáveis do ambiente de desenvolvimento
cp .env.example .env
cp .env.example api/.env

# 3. Executar o backend Spring Boot (escutando na porta 8085)
cd api
./mvnw spring-boot:run

# 4. Instalar dependências e iniciar o frontend React (escutando na porta 5173)
cd ../front
npm install
npm run dev
```

A especificação Swagger-UI das rotas HTTP da API está disponível localmente em `http://localhost:8085/swagger-ui.html` ou pelo contrato em [openapi.yaml](docs/openapi.yaml).
