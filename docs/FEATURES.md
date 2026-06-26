# Catálogo de Funcionalidades — Inovare TI

Este documento apresenta as especificações e regras de negócio de cada módulo do ecossistema Inovare TI.

---

## 1. Motor de Agendamentos e WhatsApp

Este motor gerencia o envio automatizado de confirmações e lembretes aos pacientes da Clínica Inovare.

### 1.1 Algoritmo de Pacing e Virtual Threads
Para mitigar erros de estouro de requisições (**HTTP 429 - Rate Limit**) nos servidores da API Take Blip e WhatsApp:
* **Pacing Dinâmico (Delay):** A cada envio individual de mensagem dentro do loop de processamento do lote, a thread suspende sua execução por um intervalo aleatório e controlado entre **150ms e 300ms** (`ThreadLocalRandom.current().nextLong(150, 301)`).
* **Virtual Threads (Java 21):** O loop é executado sob um executor assíncrono de threads virtuais (`Executors.newVirtualThreadPerTaskExecutor()`). A chamada do `Thread.sleep` suspende apenas a thread virtual coordenadora, liberando os recursos físicos da CPU (Carrier Threads) para outros fluxos concorrentes do Spring Boot.
* **Semáforo de Vazão de Rede:** Um semáforo de concorrência (`blipSemaphore`) limita em no máximo **20** as requisições simultâneas de atualização de contextos de usuários na API do Blip.

### 1.2 Regra de Final de Semana (Antecipação)
Para evitar o incômodo de mensagens automáticas disparadas aos finais de semana (sábados e domingos):
* **Segunda a Quinta-Feira:** O job notifica os pacientes com consultas marcadas para o dia seguinte (`LocalDate.now().plusDays(1)`).
* **Sexta-Feira:** O job antecipa a busca e notifica as consultas marcadas para a próxima **segunda-feira** (`LocalDate.now().plusDays(3)`). As consultas do fim de semana que não puderem ser antecipadas são tratadas manualmente pela recepção.

### 1.3 Agrupamento de Consultas (`NotificationGroup`) e Idempotência
* **Idempotência Individual:** O serviço `AppointmentSendIdempotencyService` valida o ID de cada consulta via `registerIfFirstSend` no banco de dados local. Caso a consulta já tenha sido processada no ciclo diário corrente, ela é descartada.
* **Envio Consolidado em Lote:** Pacientes com múltiplos agendamentos no mesmo dia são unificados sob uma chave composta de telefone + data.
  1. O sistema cria um identificador único de grupo (`groupId` UUID) e persiste todas as sessões sob uma única transação atômica.
  2. Compila um texto consolidado de consultas (`pre_compiled_schedule_text`) e o grava no `NotificationGroup` na coluna correspondente.
  3. Atualiza o contexto do Blip daquele telefone com a listagem completa (fire-and-forget) e dispara apenas um template de WhatsApp unificado (`aviso_agendamento_grupo`).

---

## 2. Central de Chamados (ITSM)

Estrutura o suporte de TI, prioridades de atendimento e comunicação de incidentes tecnológicos.

### 2.1 Cálculo de SLA Útil e Prioridade
O prazo limite para solução de incidentes (`sla_deadline`) é calculado dinamicamente na abertura do chamado:
* Soma-se a quantidade de horas configurada em `sla_hours` (da tabela `itsm_categories` ou `ticket_categories`) à data e hora corrente.
* O cálculo considera apenas as horas úteis configuradas para a equipe de TI, interrompendo a contagem fora do horário comercial da clínica.

### 2.2 Regra de Parada Crítica (Incidentes Críticos)
Caso um incidente possa inviabilizar o atendimento físico na clínica:
* **Trigger por Ativo:** O chamado é associado a um ativo do CMDB marcado como crítico (`assets.is_critical = true`).
* **Trigger por Varredura de Texto:** A descrição ou título do chamado (seja aberto via web ou via comando `/chamado` no Discord) bate com a expressão regular `INV-\d{4}-\d+` (referência a códigos patrimoniais corporativos).
* **Ações Automatizadas:**
  1. A prioridade é redefinida para `URGENT`.
  2. O SLA é reduzido para o tempo fixo de **1 hora** a partir do instante de abertura.
  3. A tag `#🚨ParadaCrítica` é injetada automaticamente nas relações do chamado.
  4. Um alerta de prioridade máxima com detalhes de localização é enviado por DM do Discord ao técnico responsável e ao canal operacional.

### 2.3 Macros de Resolução e Sidebar Lateral
* **Sidebar Recomendatória:** Detalhes de chamados no estado `IN_PROGRESS` exibem chamados finalizados que possuem tags idênticas.
* **Aplicar Solução Padrão:** Se alguma tag associada possuir um texto na coluna `default_resolution` (ex. "Troca de toner efetuada e teste de impressão OK"), a interface web disponibiliza uma macro de 1 clique que preenche a nota de encerramento (`resolutionNotes`) automaticamente.

---

## 3. Gestão de Ativos e Inventário (CMDB + Estoque)

Garante o controle físico do patrimônio tecnológico e a consistência contábil das baixas de estoque.

### 3.1 Ativos Multi-usuário (CMDB)
Os computadores e impressoras da clínica podem ser utilizados por múltiplos colaboradores (médicos e secretárias em turnos rotativos).
* A associação de usuários a ativos é feita de forma granular (Many-to-Many) na tabela `asset_users`.
* Permite à equipe de TI auditar quais usuários possuem acesso a um determinado computador, auxiliando na verificação de vazamento de credenciais.

### 3.2 Vínculo entre Chamados e Manutenção de Ativos (V38)
Toda manutenção corretiva ou preventiva realizada em um equipamento é registrada para fins de controle de custos de hardware:
* No encerramento do chamado (`ResolveTicketUseCase`), se houver metadados de manutenção preenchidos (`ResolveTicketRequest.maintenance()`), o sistema cria um registro em `asset_maintenances` com o tipo de manutenção, custo financeiro e técnico executor.
* A coluna `ticket_id` é vinculada à manutenção do ativo, estabelecendo uma rastreabilidade bidirecional. Isso permite saber qual chamado de TI motivou aquela manutenção específica.

### 3.3 Estoque Crítico e Alertas de Compra (V34)
* A coluna `min_stock` determina o limite aceitável de segurança para cada insumo (como mouses, toners e cabos).
* Ao realizar a saída física de qualquer item, se o estoque atual cair a um nível menor ou igual a `min_stock`, o sistema dispara o evento `LowStockEvent`.
* O listener `DiscordAlertListener` processa o evento de forma assíncrona (`@Async`), formatando um aviso em laranja e enviando-o via webhook ou JDA Bot para o canal de compras da TI.

### 3.4 Baixa de Insumos via FIFO e Transacionalidade
As peças e insumos de hardware de TI são retirados seguindo a regra FIFO (First-In, First-Out):
* **Algoritmo FIFO:** A rotina de dedução (`StockDeductionService`) consome prioritariamente os lotes de estoque (`stock_batches`) com data de entrada mais antiga e quantidade disponível superior a zero (`remaining_quantity > 0`). Caso a retirada seja maior que o lote mais antigo, o saldo é abatido dos lotes subsequentes.
* **Propagação Mandatória (`Propagation.MANDATORY`):** O método de dedução deve obrigatoriamente rodar dentro da mesma transação do caso de uso de resolução do chamado (`ResolveTicketUseCase`). Qualquer falha na redução de lotes ou inconsistência de saldos cancela a transação inteira no banco (Rollback).
* **Mecanismo de Proteção e Fallback:** Se a baixa ocorrer com sucesso mas a movimentação de histórico não for gravada por oscilações no banco, o caso de uso executa uma verificação final. Constatando a ausência do registro histórico, ele insere de forma autônoma um registro em `stock_movements` do tipo `OUT` contendo a referência `TICKET:{ticketId}` e o custo unitário praticado na transação (`unit_price_at_time`), preservando a integridade do balanço contábil.

---

## 4. Cofre de Credenciais (Vault)

O Vault armazena dados sigilosos e documentos técnicos sob regras rígidas de acesso e conformidade.

### 4.1 Proteção Ativa com Dois Fatores (MFA)
* Toda operação de leitura (visualizar segredo), download de anexos do cofre ou alteração física exige que o usuário passe pelo desafio TOTP (Google Authenticator).
* O token JWT de autenticação deve carregar a claim `two_factor_verified = true`. A ausência desta claim bloqueia o acesso via interceptador `TwoFactorSessionGuard`.

### 4.2 Níveis de Visibilidade de Segredos
* `PRIVATE`: Acesso restrito ao criador do item (`owner_id`) e administradores.
* `ALL_TECH_ADMIN`: Compartilhado automaticamente com toda a equipe técnica e administradores.
* `CUSTOM`: Compartilhamento explícito e individual com colaboradores específicos cadastrados na tabela `vault_item_shares`.

---

## 5. Auditoria de Ações e LGPD

Trilha de auditoria assíncrona que registra eventos e ações de usuários para compliance de LGPD:
* **Escuta de Eventos Assíncrona (`AuditEventListener`):** O Spring Boot publica eventos do tipo `AuditEvent` que são interceptados e salvos em segundo plano na tabela `audit_logs`, sem impactar o tempo de resposta da interface web.
* **Eventos Rastreáveis:**
  * Login com sucesso ou falha, resets e verificações de 2FA.
  * Criação, remoção, alteração ou leitura de segredos no cofre (`VAULT_SECRET_VIEW`).
  * Abertura, fechamento ou alteração de responsáveis de chamados de suporte.
  * Entradas, saídas e transferências físicas de insumos e ativos.