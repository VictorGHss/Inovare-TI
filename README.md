# Inovare TI — Centro de Operações e Automações

Ecossistema unificado desenvolvido para gerenciar chamados de suporte (ITSM), controle patrimonial (CMDB), conciliação financeira e automação de pautas médicas da Clínica Inovare.

## 🚀 Escopo do Projeto

O sistema orquestra quatro pilares centrais da operação:
1. **Motor de Agendamentos (Appointment Motor):** Consome os dados do Feegow ERP e gerencia webhooks do Take Blip/Meta para disparar templates ativos de confirmação de consultas, distribuir respostas em filas dinâmicas e rodar fluxos matinais de reengajamento (*nudges*).
2. **Central de Chamados (ITSM):** Fluxo interno para abertura, triagem e resolução de tickets de suporte com controle rigoroso de SLA e alertas assíncronos enviados diretamente aos canais das secretárias via webhooks do Discord.
3. **Controle Patrimonial & Estoque:** Inventário de insumos clínicos e ativos de infraestrutura de TI (MikroTik e Ubiquiti UniFi) integrados com contadores e telemetria de tráfego via Prometheus.
4. **Módulo Payflow & Conta Azul:** Integração com a API V2 da Conta Azul para conciliação em lote de parcelas quitadas, tratamento de concorrência atômica e emissão assistida de recibos internos em PDF.

## 🛠️ Stack Tecnológica

| Camada | Tecnologias Core |
| :--- | :--- |
| **Backend** | Java 21 (Virtual Threads), Spring Boot 3.x, JPA/Hibernate, Flyway, PostgreSQL, Redis |
| **Frontend** | React, TypeScript, Vite, TailwindCSS, Axios |
| **Observabilidade** | Micrometer, Prometheus, Grafana |
| **Mensageria / APIs** | Protocolo LIME (Take Blip), Discord API, SMTP (Skymail / Brevo) |

## 📂 Índice de Documentação

Toda a especificação técnica detalhada foi distribuída em guias especializados dentro do diretório `docs/`. Utilize os atalhos para navegar:

* 🏗️ [**Arquitetura e Design de Software**](file:///C:/Projeto/Inovare-TI/docs/ARCHITECTURE.md): Detalhamento do padrão Ports & Adapters (Arquitetura Hexagonal), mapeamento de pacotes e isolamento de domínio.
* 🚀 [**Guia do Desenvolvedor & Setup Local**](file:///C:/Projeto/Inovare-TI/docs/DEVELOPER_GUIDE.md): Requisitos mínimos, inicialização dos containers e variáveis de ambiente para rodar o projeto do zero.
* ⚙️ [**Regras de Negócio e Funcionalidades**](file:///C:/Projeto/Inovare-TI/docs/FEATURES.md): Lógicas core do motor (algoritmo de pacing temporal contra rate limits, concorrência de recibos e a regra de fim de semana).
* 🔌 [**Manual de Integração de APIs**](file:///C:/Projeto/Inovare-TI/docs/INTEGRATIONS.md): Detalhamento dos contratos e fluxos de comunicação com Feegow ERP, Blip/Meta e Conta Azul.

> 📌 **Nota sobre endpoints REST:** A especificação técnica das rotas da API é gerada dinamicamente pelo Springdoc Swagger. O contrato estático de referência pode ser consultado em [docs/openapi.yaml](file:///C:/Projeto/Inovare-TI/docs/openapi.yaml).

## 🛠️ Inicialização Rápida

Com as credenciais configuradas no seu arquivo `.env`, inicialize o ecossistema com os comandos abaixo:

```bash
# 1. Subir os contêineres de infraestrutura básica (PostgreSQL, Redis e Prometheus)
docker-compose up -d

# 2. Executar e iniciar o servidor Backend (Porta 8085)
cd api && ./mvnw spring-boot:run

# 3. Iniciar o servidor Frontend em ambiente de desenvolvimento
cd front && npm install && npm run dev
```
