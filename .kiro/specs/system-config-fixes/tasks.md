# Implementation Plan: system-config-fixes

## Overview

Correção de três bugs relacionados à configuração do sistema:

1. **Bug 1** — `DatabaseBackupScheduler` chama `pg_dump` diretamente via `ProcessBuilder`, mas o binário não existe no container Docker da API Spring Boot.
2. **Bug 2** — O botão "Excluir" em `ProfessionalMappingPanel.tsx` está desabilitado porque `row.id` é `undefined` para registros vindos do banco.
3. **Bug 3** — O `<select>` de fila do Blip sempre exibe a primeira opção porque o `value` do select não corresponde ao `id` retornado pela API do Blip, e o `blipQueueId` não é persistido corretamente.

---

## Tasks

- [ ] 1. Corrigir Bug 1 — DatabaseBackupScheduler: usar caminho configurável do pg_dump
  - [ ] 1.1 Verificar e corrigir o valor padrão de app.backup.pg-dump-binary no application.properties
    - Alterar o valor padrão de `${APP_BACKUP_PG_DUMP_BINARY}` para incluir fallback explícito vazio ou documentado
    - Garantir que `DatabaseBackupScheduler` não use `pg_dump` como fallback implícito quando a variável de ambiente não estiver definida
    - Adicionar validação no método `executeBackupInternal`: se `pgDumpBinary` estiver em branco, registrar alerta CRITICAL e retornar sem lançar exceção não tratada
    - _Requisitos: 1.1, 1.2, 2.1, 2.3_

  - [ ] 1.2 Atualizar DatabaseBackupScheduler para validar o binário antes de executar
    - Adicionar verificação de `pgDumpBinary` não nulo e não vazio antes de construir o `ProcessBuilder`
    - Lançar `IOException` com mensagem clara quando o binário não estiver configurado, para que o bloco `catch` existente registre o alerta CRITICAL corretamente
    - Garantir que o comportamento de `app.backup.enabled=false` continue ignorando a execução sem criar alertas
    - _Requisitos: 2.1, 2.3, 3.7_

- [ ] 2. Corrigir Bug 2 — Botão Excluir desabilitado no mapeamento de profissionais
  - [ ] 2.1 Investigar e corrigir o retorno do campo id no endpoint de listagem de mapeamentos
    - Verificar o DTO/record de resposta do endpoint `GET /v1/appointments/admin/mappings` para confirmar que o campo `id` (UUID) é serializado no JSON
    - Se o campo `id` estiver ausente no DTO de resposta, adicioná-lo
    - _Requisitos: 2.4_

  - [ ] 2.2 Corrigir o merge de dados em ProfessionalMappingPanel.tsx para preservar o id do mapeamento
    - No `useEffect` de carregamento inicial e na função `handleSyncData`, garantir que o campo `id` do `DoctorMapping` retornado pelo banco seja preservado no objeto merged
    - O campo `id: existing?.id` já está presente no código — verificar se o problema está na tipagem ou no retorno da API
    - _Requisitos: 2.4_

  - [ ] 2.3 Garantir que o botão Excluir use o dialogo de confirmacao e trate erros HTTP
    - Verificar se o botão usa `confirm()` nativo e se o comportamento está correto
    - Garantir que erros HTTP 4xx/5xx do `deleteMappingById` sejam capturados e exibidos via `toast.error`
    - Garantir que, ao cancelar o diálogo, o mapeamento permaneça intacto
    - _Requisitos: 2.5, 2.6, 2.7, 2.8_

- [ ] 3. Checkpoint — Verificar Bug 1 e Bug 2
  - Confirmar que as correções dos bugs 1 e 2 estão funcionando corretamente

- [ ] 4. Corrigir Bug 3 — Select de fila do Blip sempre exibe a primeira opcao
  - [ ] 4.1 Investigar o endpoint GET /v1/appointments/blip/queues e o campo identificador das filas
    - Verificar o controller/service que serve `/v1/appointments/blip/queues` e confirmar qual campo é retornado como identificador no JSON (`id` vs `identity` vs outro)
    - Comparar com o campo `blipQueueId` armazenado no banco de dados (`blip_queue_id` na entidade `AppointmentDoctorMappingEntity`)
    - _Requisitos: 2.9, 2.10, 2.11, 2.12_

  - [ ] 4.2 Corrigir o select em ProfessionalMappingPanel.tsx para usar o campo correto como value
    - Garantir que `<option value={q.id}>` use o mesmo campo que é armazenado em `blipQueueId` no banco
    - Garantir que `value={row.blipQueueId}` no `<select>` corresponda ao campo correto
    - Se o campo identificador da fila for `identity` em vez de `id`, atualizar a interface `BlipQueue` em `doctorMappingService.ts` e os `<option value>` correspondentes
    - _Requisitos: 2.9, 2.10_

  - [ ] 4.3 Garantir que o blipQueueId selecionado seja incluido corretamente no payload de salvamento
    - Verificar que `updateField(idx, 'blipQueueId', e.target.value)` atualiza o estado corretamente
    - Verificar que o payload enviado em `handleSave` inclui `blipQueueId` com o valor correto
    - Verificar que `SyncMappingRequest` no backend aceita e persiste o campo `blipQueueId` corretamente
    - _Requisitos: 2.11_

  - [ ] 4.4 Implementar comportamento de placeholder quando blipQueueId salvo nao corresponde a nenhuma fila
    - Quando `blipQueueId` do banco não corresponde a nenhum `id` da lista retornada pela API do Blip, o `<select>` deve exibir o placeholder em vez de selecionar a primeira opção
    - _Requisitos: 2.12_

- [ ] 5. Checkpoint final — Garantir que todos os bugs estao corrigidos
  - Confirmar que todos os três bugs estão corrigidos e funcionando corretamente
