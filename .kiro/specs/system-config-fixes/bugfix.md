# Bugfix Requirements Document

## Introduction

Este documento cobre três bugs relacionados à configuração do sistema na aplicação Inovare-TI:

1. **Backup falha com `pg_dump` não encontrado** — O agendador de backup (`DatabaseBackupScheduler`) tenta executar o binário `pg_dump` diretamente via `ProcessBuilder`, mas esse binário não está disponível no container Docker da API Spring Boot, causando falha crítica em qualquer tentativa de backup (manual ou agendado).

2. **Mapeamento de filas do Blip: não é possível excluir** — Na tela de configuração de mapeamento de profissionais, o botão "Excluir" permanece desabilitado para registros existentes no banco de dados, impedindo a remoção de mapeamentos.

3. **Seleção de fila do Blip sempre exibe a primeira opção e não persiste** — Ao selecionar qualquer fila do Blip no dropdown da tela de mapeamento, o sistema sempre exibe "Recepção 1° andar esquerda" (primeira da lista) independentemente da opção escolhida, e o valor selecionado não é salvo no banco de dados.

---

## Bug Analysis

### Current Behavior (Defect)

**Bug 1 — Backup com pg_dump não encontrado:**

1.1 WHEN o usuário aciona o backup manual via `POST /admin/backups/trigger` THEN o sistema lança `java.io.IOException: Cannot run program "pg_dump": Exec failed, error: 2 (No such file or directory)` e registra alerta de severidade CRITICAL no banco de dados

1.2 WHEN o agendador executa o backup automático às 3h THEN o sistema falha com o mesmo erro de binário não encontrado e nenhum arquivo de backup é gerado

**Bug 2 — Não é possível excluir mapeamento:**

2.1 WHEN o usuário acessa a tela de mapeamento de profissionais e tenta clicar em "Excluir" para um mapeamento existente THEN o botão está desabilitado (`disabled`) e a ação de exclusão não pode ser iniciada

**Bug 3 — Select de fila do Blip sempre mostra a primeira opção:**

3.1 WHEN o usuário seleciona qualquer fila do Blip no dropdown de um mapeamento existente THEN o sistema exibe "Recepção 1° andar esquerda" (primeira opção da lista) em vez da fila selecionada

3.2 WHEN o usuário seleciona uma fila do Blip e salva o mapeamento THEN o valor selecionado não é persistido no banco de dados, mantendo o valor anterior ou vazio

---

### Expected Behavior (Correct)

**Bug 1 — Backup com pg_dump não encontrado:**

2.1 WHEN o usuário aciona o backup manual via `POST /admin/backups/trigger` e a propriedade `app.backup.pg-dump-binary` aponta para um caminho válido do binário `pg_dump` (por exemplo, dentro do container do banco de dados via `docker exec`) THEN o sistema SHALL executar o dump do banco de dados sem lançar `IOException` e gerar um arquivo `.sql` não vazio no diretório configurado em `app.backup.output-dir`

2.2 WHEN o agendador executa o backup automático e o dump é gerado com sucesso THEN o sistema SHALL compactar o arquivo `.sql` em um arquivo ZIP com tamanho maior que zero bytes no diretório `app.backup.output-dir`

2.3 WHEN o backup falha por qualquer motivo (binário não encontrado, falha de conexão, erro de I/O) THEN o sistema SHALL registrar um alerta de severidade CRITICAL no `SystemAlertRepository` com a mensagem de erro original e retornar HTTP 500 para chamadas manuais

**Bug 2 — Não é possível excluir mapeamento:**

2.4 WHEN o usuário acessa a tela de mapeamento de profissionais e a linha exibida possui um `id` UUID não nulo proveniente do banco de dados THEN o sistema SHALL renderizar o botão "Excluir" dessa linha no estado habilitado (sem atributo `disabled`)

2.5 WHEN o usuário clica no botão "Excluir" habilitado de um mapeamento THEN o sistema SHALL exibir um diálogo de confirmação antes de executar a exclusão

2.6 WHEN o usuário confirma a exclusão no diálogo THEN o sistema SHALL enviar `DELETE /v1/appointments/mapping/{id}` com o UUID correto e, ao receber HTTP 200/204, remover a linha da tabela na interface sem recarregar a página inteira

2.7 WHEN o usuário cancela o diálogo de confirmação THEN o sistema SHALL fechar o diálogo e manter o mapeamento intacto na interface e no banco de dados

2.8 WHEN a chamada `DELETE /v1/appointments/mapping/{id}` retorna HTTP 4xx ou 5xx THEN o sistema SHALL exibir uma mensagem de erro visível ao usuário e manter a linha na tabela

**Bug 3 — Select de fila do Blip sempre mostra a primeira opção:**

2.9 WHEN o sistema carrega a tela de mapeamento de profissionais e um profissional já possui `blipQueueId` salvo no banco de dados THEN o sistema SHALL pré-selecionar no dropdown a opção cuja propriedade `id` (ou `identity`) retornada pela API do Blip seja igual ao `blipQueueId` armazenado

2.10 WHEN o usuário seleciona uma fila do Blip no dropdown THEN o sistema SHALL atualizar o estado local do componente com o `id` da fila selecionada, de modo que o dropdown exiba o nome da fila escolhida e não a primeira opção da lista

2.11 WHEN o usuário salva o mapeamento após selecionar uma fila do Blip THEN o sistema SHALL enviar no payload da requisição o campo `blipQueueId` com o valor `id` da fila selecionada, e o banco de dados SHALL armazenar esse valor na coluna `blip_queue_id` da tabela de mapeamentos

2.12 WHEN a API do Blip retorna a lista de filas e o `blipQueueId` salvo no banco não corresponde a nenhum `id` da lista THEN o sistema SHALL exibir o dropdown sem nenhuma opção pré-selecionada (placeholder visível) em vez de selecionar a primeira opção

---

### Unchanged Behavior (Regression Prevention)

3.1 WHEN o backup é executado com sucesso THEN o sistema SHALL compactar o dump em ZIP com criptografia AES-256 quando `app.backup.zip-password` estiver configurado e não vazio

3.2 WHEN o backup é executado com sucesso e `app.backup.destination-email` está configurado THEN o sistema SHALL enviar o arquivo ZIP por e-mail para o endereço configurado

3.3 WHEN o backup é executado com sucesso THEN o sistema SHALL registrar um alerta de severidade SUCCESS no `SystemAlertRepository`

3.4 WHEN o usuário salva mapeamentos sem alterar a fila do Blip de um profissional THEN o sistema SHALL preservar o valor de `blipQueueId` já armazenado no banco de dados para esse profissional

3.5 WHEN o usuário exclui um mapeamento com sucesso THEN os demais registros de mapeamento na tabela SHALL permanecer com seus valores de `id`, `feegowProfessionalId` e `blipQueueId` inalterados

3.6 WHEN o usuário carrega a tela de mapeamento de profissionais THEN o sistema SHALL exibir todos os profissionais retornados pela API da Feegow e, para cada um, o mapeamento existente no banco de dados (se houver)

3.7 WHEN `app.backup.enabled` está definido como `false` THEN o agendador SHALL não executar o backup automático e nenhum `SystemAlert` SHALL ser criado nem exceção propagada
