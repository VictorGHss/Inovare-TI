# Catálogo de Funcionalidades — Inovare TI

Este documento apresenta o resumo executivo, especificações funcionais e as regras de negócio dos módulos principais que compõem a plataforma Inovare TI.

---

## 🎨 Identidade e Experiência do Usuário

* **Design System Premium**: Interface responsiva baseada na cor primária da marca Inovare (`#feb56c`), desenvolvida para garantir leitura ágil e navegação fluida em desktops, tablets e smartphones.
* **Dashboard Executivo**: Painel centralizado que exibe cards de resumos operacionais em tempo real, atalhos de monitoramento técnico, rankings Top 5 de demandas e gráficos interativos de controle de chamados e finanças.

---

## 📅 Motor de Agendamentos e Comunicação com Pacientes

O Motor de Agendamentos automatiza a ingestão diária de consultas externas e gerencia a esteira de lembretes e confirmações enviadas aos pacientes por meio da plataforma WhatsApp, operando sob regras rígidas de segurança operacional e conformidade técnica.

O processamento principal é orquestrado pelo caso de uso [IngestAppointmentsUseCase](file:///C:/Projeto/Inovare-TI/api/src/main/java/br/dev/ctrls/inovareti/modules/appointment/application/usecase/IngestAppointmentsUseCase.java), que interage com os adapters de mensageria e persistência local.

### 1. Algoritmo de Pacing e Throttling (Virtual Threads do Java 21)
Para garantir a saúde operacional da aplicação e mitigar estouros de limite de requisições (**Rate Limit / HTTP 429**) na API do Take Blip/Meta Cloud, a rotina de ingestão e despacho implementa um controle rigoroso de vazão temporal:
* **Espaçamento Temporal (Pacing)**: Durante o processamento concorrente do lote diário de agendamentos, o sistema avalia se um disparo anterior já foi efetuado. Em caso positivo, aplica um atraso aleatório controlado entre **150ms e 300ms** antes de iniciar a próxima tarefa de notificação (implementado na linha 248 de [IngestAppointmentsUseCase](file:///C:/Projeto/Inovare-TI/api/src/main/java/br/dev/ctrls/inovareti/modules/appointment/application/usecase/IngestAppointmentsUseCase.java)):
  ```java
  long delayMillis = java.util.concurrent.ThreadLocalRandom.current().nextLong(150, 301);
  Thread.sleep(delayMillis);
  ```
* **Virtual Threads do Java 21**: O uso de `Thread.sleep` é completamente seguro e não prejudica o desempenho do servidor. Como o projeto está estruturado sobre as *Virtual Threads* do Java 21 (`Executors.newVirtualThreadPerTaskExecutor()`), a chamada suspende temporariamente apenas a thread virtual coordenadora, cedendo os recursos físicos da CPU (Carrier Threads) a outros processos na JVM.
* **Limitação de Concorrência Secundária**: Adicionalmente, um semáforo de concorrência ([blipSemaphore](file:///C:/Projeto/Inovare-TI/api/src/main/java/br/dev/ctrls/inovareti/modules/appointment/application/usecase/IngestAppointmentsUseCase.java#L86)) com limite dinâmico configurável (propriedade `APP_APPOINTMENT_BLIP_INGEST_CONCURRENCY`, padrão: `20`) protege os barramentos de rede, bloqueando excessos de requisições simultâneas de configuração de contexto e envio de templates ao Blip.

### 2. Regra do Dilema de Segunda-Feira
Para evitar o envio inconveniente de mensagens automáticas de lembretes durante o final de semana (sábado e domingo), a rotina de busca de consultas executa uma antecipação estratégica de datas, baseando-se no dia da semana em que é executada:
* **Execuções em Dias Úteis (Segunda a Quinta-Feira)**: O sistema busca e ingere consultas agendadas para o dia seguinte (`LocalDate.now().plusDays(1)`).
* **Execuções na Sexta-Feira (O Dilema de Segunda-Feira)**: Quando a rotina de ingestão detecta que o dia atual é sexta-feira (`FRIDAY`), o sistema calcula a data alvo com um salto temporal de três dias (`LocalDate.now().plusDays(3)`). Com isso, todas as consultas agendadas para a próxima **segunda-feira** são ingeridas e notificadas antecipadamente na própria sexta-feira, em horário comercial, blindando o descanso do paciente no final de semana.

### 3. Idempotência de Envio e Agrupamento em Lote (NotificationGroup)
Duplicidades de envio e bombardeio de mensagens múltiplas a um único cliente são bloqueadas por dois mecanismos de segurança:
* **Filtro de Idempotência Individual**: O serviço [AppointmentSendIdempotencyService](file:///C:/Projeto/Inovare-TI/api/src/main/java/br/dev/ctrls/inovareti/modules/appointment/application/service/AppointmentSendIdempotencyService.java) intercepta o identificador do agendamento vindo do ERP. O método `registerIfFirstSend(feegowAppointmentId)` atua como uma barreira que registra o despacho e rejeita reprocessamentos da mesma consulta no mesmo ciclo.
* **Estratégia de Envio Agrupado (NotificationGroup)**: Caso o paciente possua múltiplos agendamentos cadastrados para a mesma data alvo, o motor impede o spam de mensagens isoladas unificando o fluxo:
  1. Os agendamentos são agrupados na chave composta do telefone purificado e da data (`normalizedPhone + "#" + date`).
  2. Um identificador único de grupo (`groupId`) em formato UUID é gerado.
  3. Em uma **única transação de banco de dados** via `transactionTemplate.execute(...)`, todas as sessões ([AppointmentSession](file:///C:/Projeto/Inovare-TI/api/src/main/java/br/dev/ctrls/inovareti/modules/appointment/domain/model/AppointmentSession.java)) são persistidas associadas ao `groupId` e registros na tabela [NotificationGroup](file:///C:/Projeto/Inovare-TI/api/src/main/java/br/dev/ctrls/inovareti/modules/appointment/domain/model/NotificationGroup.java) são inseridos de uma só vez, reduzindo o overhead do pool de conexões (HikariCP).
  4. O sistema pré-compila uma lista de agendamentos detalhada contendo informações consolidadas de todas as consultas do grupo e armazena na coluna `pre_compiled_schedule_text`.
  5. O contexto do Blip do usuário é configurado de forma assíncrona (fire-and-forget) com a lista detalhada compilada, o `groupId` e a flag `isConfirmingAgenda = true`.
  6. É disparado um único template unificado de grupo informando ao paciente sobre as suas consultas coletivas. As confirmações ou cancelamentos feitos pelo paciente são computados no grupo e propagados a todas as sessões vinculadas de forma correlata.

---

## 🔒 Cofre Eletrônico de Senhas e Documentos (Vault)

O Vault atua como um perímetro de segurança para custódia de credenciais corporativas e dados sensíveis:

* **Proteção Ativa por MFA**: Exige validação obrigatória de segundo fator (TOTP) para a leitura de segredos (`secret_content`), downloads de anexos criptografados ou qualquer ação de escrita (criação, edição e exclusão de itens).
* **Níveis de Compartilhamento**:
  * `PRIVATE`: Acesso restrito exclusivamente ao usuário proprietário (`owner_id`) e administradores.
  * `ALL_TECH_ADMIN`: Compartilhamento coletivo automático com todos os técnicos de TI e administradores cadastrados.
  * `CUSTOM`: Compartilhamento granular com usuários selecionados individualmente (gerido pela tabela `vault_item_shares`).
* **Edição e Exclusão Seguras**: Somente o criador do item (`owner_id`) ou um operador com permissão de `ADMIN` possuem autoridade para atualizar ou expurgar dados do cofre.
* **Compliance de Acesso**: Todo e qualquer evento sensível no cofre (visualização de senhas, download de anexos, alterações) gera imediatamente um registro imutável na trilha de auditoria.

---

## 🎫 Central de Chamados (Helpdesk)

O motor de chamados centraliza e agiliza o fluxo de suporte de TI da clínica:

* **SLA Dinâmico por Categoria**: O prazo máximo de atendimento (`sla_deadline`) é calculated de forma automática no momento da abertura do chamado, com base no número de horas úteis configurado na categoria selecionada.
* **Gestão de Atribuições**: Técnicos podem assumir chamados autonomamente (Claim) ou administradores podem designar operadores específicos.
* **Comentários e Histórico**: Suporte a diálogos e troca de mensagens entre os solicitantes e a equipe de suporte técnico, com upload de anexos legados integrados ao chamado.
* **Base de Conhecimento Lateral & Macros de 1-Clique**:
  * **Pesquisa de Chamados Similares**: Na sidebar de detalhes de chamados em progresso (`IN_PROGRESS`), a aplicação executa uma lógica consultiva automática em background buscando chamados finalizados (`RESOLVED`/`CLOSED`) que compartilham de tags em comum. Isto apresenta runbooks e soluções antigas instantaneamente ao técnico na mesma tela.
  * **Botão Premium "Aplicar Solução Padrão"**: Se qualquer uma das tags do chamado possuir uma macro de resolução (`default_resolution`) cadastrada, um botão de atalho premium surge na sidebar. Com **1-clique**, ele abre o modal de resolução e preenche automaticamente toda a nota de fechamento padrão, acelerando a finalização com segurança e padronização.
* **Notificações Operacionais**: Notificações por e-mail e web baseadas no perfil do operador, com preferência individual configurável de recebimento de alertas no Discord.

---

## 🚨 Regra de Negócio de Parada Crítica (Incidentes Críticos)

Para blindar a operação de saúde contra paradas tecnológicas severas que afetem o atendimento ao paciente, o sistema possui uma esteira dedicada e prioritária de detecção e contenção de falhas em ativos críticos:

1. **Associação de Ativo Crítico**: No momento da criação do chamado, se for associado um ativo que esteja sinalizado no CMDB como crítico (`is_critical = true`), a regra é acionada instantaneamente.
2. **Varredura Inteligente por Regex (Discord)**: Ao abrir chamados via slash command `/chamado` no Bot do Discord (onde a descrição é texto livre), o sistema executa em background uma varredura por expressão regular (`INV-\d{4}-\d+`) para capturar códigos de patrimônio digitados pelo usuário. Se corresponder a um patrimônio crítico cadastrado, a automação é disparada automaticamente.
3. **Escalonamento Agressivo de SLA e Prioridade**:
   * A prioridade do chamado é forçada para `URGENT`.
   * O SLA (`sla_deadline`) é recalculado para **exatamente 1 hora** a partir do momento da criação, independente do SLA padrão da categoria.
   * A tag mestre `#🚨ParadaCrítica` é injetada de forma automática no chamado.
4. **Alerta Vermelho ao Técnico Responsável**: O bot do Discord despacha de forma síncrona uma mensagem privada de embed rico, estilizada com a barra vermelha e contendo detalhes do incidente urgente diretamente na Direct Message (DM) do técnico de plantão para ação imediata.

---

## 📦 Inventário, Ativos e Algoritmo FIFO Transacional

O controle de insumos e hardware foi projetado para assegurar consistência fiscal e exatidão contábil total:

### 1. Gestão de Insumos e Lotes de Compra
* Cadastro de itens de consumo de TI e peças de reposição agrupadas por categorias.
* Lotes de estoque (`stock_batches`) individuais contendo o valor unitário de aquisição do produto e data de registro, o que viabiliza o acompanhamento financeiro preciso do estoque circulante.

### 2. Algoritmo FIFO (First-In, First-Out)
Para a saída de mercadorias no fechamento de chamados ou retiradas manuais, a plataforma executa estritamente a política FIFO:
* Os lotes com a data de entrada mais antiga e quantidade disponível (`remaining_quantity > 0`) são consumidos prioritariamente.
* Se a quantidade requisitada exceder o lote mais antigo, o motor realiza a dedução fracionada consumindo lotes subsequentes de forma recursiva até sanar a totalidade do pedido.

### 3. Mecanismo Transacional e Fallback de Persistência
Para evitar qualquer discrepância entre a redução do saldo físico (`current_stock` em `items`) e a trilha de relatórios financeiros de saída de inventário, a plataforma implementa uma camada transacional de alta robustez:
* **Atomicidade Garantida**: O método de dedução FIFO utiliza `Propagation.MANDATORY`, executando obrigatoriamente dentro da mesma transação física do encerramento do chamado (`ResolveTicketUseCase`). Em caso de falha em qualquer etapa (dedução de lote, persistência, gravação de log), toda a operação sofre rollback.
* **Persistência de Fallback**: Caso ocorra alguma falha pontual de comunicação e o serviço principal de dedução de lote não registre a movimentação, o caso de uso executa uma barreira de proteção ativa. Ele realiza uma consulta rápida pós-dedução e, caso não localize a movimentação, cria e persiste de forma autônoma um registro em `stock_movements` do tipo `OUT` associado ao ticket de origem (referência: `TICKET:{ticketId}`).
* **Tratamento Fiel de Valores**: Os relatórios financeiros de saídas de inventário realizam filtragem exclusiva por movimentações do tipo `OUT` e tratam valores de aquisição (`unit_price_at_time`) nulos como zero, prevenindo travamento de relatórios e assegurando que nenhum consumo seja ignorado por falta de dados financeiros de entrada históricos.

### 4. Controle de Ativos e QR Codes (CMDB Avançado)
* **Gestão de Ativos Multi-usuário**: Suporta o relacionamento Many-to-Many entre ativos físicos e múltiplos usuários (ex: impressoras de balcão ou servidores locais compartilhados por secretárias de diferentes especialidades, como cardiologia e oftalmologia). Isto permite o rastreamento preciso e a injeção do contexto dos setores envolvidos.
* **Sinalização de Criticidade**: Ativos podem ser marcados como críticos (`is_critical = true`), desencadeando fluxos urgentes de resolução e prioridade máxima quando associados a chamados de suporte.
* **Geração e scanner nativo de QR Codes**: Frontend integrado para scanner de QR Codes físicos usando a câmera do celular, abrindo instantaneamente a tela de auditoria ou gerando chamados pré-configurados associados ao ativo.

---

## 📖 Base de Conhecimento

Espaço estruturado para o compartilhamento de artigos, tutoriais técnicos e runbooks de autoatendimento para os colaboradores da clínica:
* **Fluxo de Rascunhos**: Artigos criados no estado `DRAFT` (Rascunho) são visíveis e editáveis exclusivamente pelo próprio autor do texto ou por operadores `ADMIN`.
* **Publicação Controlada**: A transição para o estado `PUBLISHED` disponibiliza o conteúdo para leitura de todos os setores corporativos do sistema.

---

## 📱 PWA e Mobilidade

* **Experiência Instalável**: Configuração completa de manifesto PWA permitindo a instalação nativa do sistema em dispositivos iOS e Android como um aplicativo dedicado.
* **Scanner de QR Code Nocivo**: Utilização das APIs nativas da câmera para ler códigos patrimoniais colados fisicamente nos computadores, proporcionando rapidez na triagem de problemas nos consultórios da clínica.

---

## 📋 Auditoria 360 e Compliance Imutável

Trilha de auditoria desacoplada e assíncrona gerida por eventos internos Spring Boot (`AuditEvent`) para compliance e conformidade LGPD:

* **Isolamento de Persistência**: A gravação de logs de auditoria ocorre em background de forma assíncrona por meio do `AuditEventListener`, impedindo que atrasos na gravação de logs prejudiquem o tempo de resposta das transações normais de tela.
* **Ações Rastreáveis Mapeadas**:
  * **Autenticação**: Sucesso de login (`LOGIN_SUCCESS`), falha de credenciais (`LOGIN_FAILURE`), validação TOTP, reset de 2FA por administrador ou via Discord.
  * **Cofre (Vault)**: Criação de segredos, leitura descriptografada de senhas (`VAULT_SECRET_VIEW`), download de anexos do cofre, edições e remoções físicas de registros.
  * **Operações**: Aberturas, claims de técnicos, transferências e encerramento de tickets. Alterações cadastrais de usuários e reset de senhas.
  * **Estoque e Ativos**: Criação de insumos, registros de lotes de compra, saídas FIFO de inventário, cadastro e alteração de ativos, além de leituras de QR Code.
  * **Artigos**: Criação de artigos, salvamento de rascunhos, edições e publicações globais de conteúdo na base de conhecimento.