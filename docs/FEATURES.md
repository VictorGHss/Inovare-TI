# Catálogo de Funcionalidades — Inovare TI

Este documento apresenta o resumo executivo, especificações funcionais e as regras de negócio dos módulos principais que compõem a plataforma Inovare TI.

---

## 🎨 Identidade e Experiência do Usuário

* **Design System Premium**: Interface responsiva baseada na cor primária da marca Inovare (`#feb56c`), desenvolvida para garantir leitura ágil e navegação fluida em desktops, tablets e smartphones.
* **Dashboard Executivo**: Painel centralizado que exibe cards de resumos operacionais em tempo real, atalhos de monitoramento técnico, rankings Top 5 de demandas e gráficos interativos de controle de chamados e finanças.

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

* **SLA Dinâmico por Categoria**: O prazo máximo de atendimento (`sla_deadline`) é calculado de forma automática no momento da abertura do chamado, com base no número de horas úteis configurado na categoria selecionada.
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

## 💳 Automação Financeira Protegida (ERP ContaAzul)

Integração bidirecional robusta com o ERP financeiro ContaAzul para automatizar processos de faturamento internos:

* **Duplo Fator Obrigatório**: Qualquer requisição às rotas financeiras `/api/financeiro/**` exige a barreira prévia do TOTP/2FA ativo. A interface só carrega o dashboard financeiro após o operador responder com sucesso ao desafio de segurança.
* **Envio Automatizado de Recibos**: A plataforma intercepta eventos de baixas de boletos e faturamentos de clientes no ERP e dispara automaticamente e-mails de agradecimento e recibos oficiais formatados aos clientes.
* **Prevenção de Duplicidades (Idempotência)**: A tabela `processed_receipts` calcula hashes do payload financeiro e armazena os IDs de parcelas processadas, garantindo que nenhum e-mail de recibo seja enviado em duplicidade para os clientes mesmo com disparos manuais sucessivos.
* **Fila de Incidentes Técnicos**: Falhas de comunicação ou ausência temporária de PDFs gerados pelo ERP são retidas em `system_alerts` com payload técnico completo para permitir a reexecução manual em um único clique assim que os sistemas externos estabilizarem.

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