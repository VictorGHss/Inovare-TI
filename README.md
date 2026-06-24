# Inovare TI — Centro de Operações e Automações

Sistema para gerenciamento de chamados de suporte (ITSM), controle patrimonial (CMDB), conciliação financeira e automação de confirmações de consultas da Clínica Inovare.

## Escopo do Projeto

O sistema gerencia quatro áreas principais da operação:
1. **Motor de Agendamentos (Appointment Motor):** Integra-se ao Feegow ERP e utiliza webhooks do Take Blip/Meta para disparar mensagens de confirmação de consultas, organizar as respostas em filas e gerenciar o reengajamento matinal (*nudges*).
2. **Central de Chamados (ITSM):** Fluxo para abertura, triagem e resolução de chamados de suporte, com controle de SLA e avisos enviados aos canais das secretárias via Discord.
3. **Controle Patrimonial e Estoque:** Inventário de insumos e ativos de infraestrutura de TI (MikroTik e Ubiquiti UniFi), integrando telemetria de tráfego e contadores via Prometheus.
4. **Módulo Payflow e Conta Azul:** Integração com a API V2 da Conta Azul para conciliação em lote de parcelas pagas, tratamento de concorrência e geração de recibos internos em PDF.

## Stack Tecnológica

| Camada | Tecnologias |
| :--- | :--- |
| **Backend** | Java 21 (Virtual Threads), Spring Boot 3.x, JPA/Hibernate, Flyway, PostgreSQL, Redis |
| **Frontend** | React, TypeScript, Vite, TailwindCSS, Axios |
| **Observabilidade** | Micrometer, Prometheus, Grafana |
| **Mensageria / APIs** | Protocolo LIME (Take Blip), Discord API, SMTP (Skymail / Brevo) |

## Documentação

As especificações técnicas estão organizadas nos arquivos dentro de `docs/`:

* [**Arquitetura e Design de Software**](file:///C:/Projeto/Inovare-TI/docs/ARCHITECTURE.md): Estrutura do padrão Ports & Adapters (Arquitetura Hexagonal), mapeamento de pacotes e isolamento de domínio.
* [**Guia do Desenvolvedor e Setup Local**](file:///C:/Projeto/Inovare-TI/docs/DEVELOPER_GUIDE.md): Requisitos mínimos, configuração do ambiente e comandos para rodar o projeto do zero.
* [**Regras de Negócio e Funcionalidades**](file:///C:/Projeto/Inovare-TI/docs/FEATURES.md): Lógicas centrais, como controle de rate limit de mensagens, concorrência de recibos e tratamento de agendamentos no fim de semana.
* [**Manual de Integração de APIs**](file:///C:/Projeto/Inovare-TI/docs/INTEGRATIONS.md): Detalhes de contratos e fluxos de comunicação com Feegow ERP, Blip/Meta e Conta Azul.

> **Endpoints REST:** A especificação das rotas da API é gerada automaticamente pelo Springdoc Swagger. O contrato de referência pode ser consultado em [docs/openapi.yaml](file:///C:/Projeto/Inovare-TI/docs/openapi.yaml).

## Inicialização Rápida

Com as credenciais configuradas no arquivo `.env`, inicialize os serviços com os comandos abaixo:

```bash
# 1. Subir os contêineres de banco de dados, cache e telemetria (PostgreSQL, Redis e Prometheus)
docker-compose up -d

# 2. Executar o servidor Backend (Porta 8085)
cd api && ./mvnw spring-boot:run

# 3. Iniciar o servidor Frontend em modo de desenvolvimento
cd front && npm install && npm run dev
```
