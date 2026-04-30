## Correção: Persistência de Movimentações de Saída ao Fechar Chamados

Resumo das alterações implementadas para corrigir o bug onde o `current_stock` era reduzido
mas a movimentação não aparecia no Relatório Geral de Saídas:

- Adicionada verificação adicional após dedução FIFO para garantir que um registro
  em `stock_movements` exista; em caso negativo, é criado um registro `OUT`
  (fallback) com `reference = TICKET:{ticketId}`. Note que o padrão de referência
  para saídas de chamados é `TICKET:{id}` — relatórios devem buscar por esse
  prefixo (por exemplo: `TICKET:00000000-0000-...`).
- Ajustada a geração de valores para relatórios para filtrar apenas movimentos
  do tipo `OUT` quando somar `unit_price_at_time` associados a um chamado.

Motivação:
- Evitar perda de trilha de auditoria quando, por algum motivo inesperado,
  o serviço de dedução não persistir a movimentação (ex.: falha transacional
  pontual). O fallback garante consistência entre redução de estoque e relatório.

Observações:
- A lógica padrão continua sendo a criação do `StockMovement` pelo
  `StockDeductionService` (FIFO). O fallback é apenas uma proteção adicional.
- A quantidade registrada segue a constraint de BD (`ck_stock_movements_qty`),
  que exige valor >= 1. O código valida antes da dedução para evitar persistir
  movimentos com quantidade inválida.
- Não houve alteração na API pública ou nos contratos existentes.
 
Transacionalidade:
- O método `StockDeductionService.deductWithFifo(...)` usa `Propagation.MANDATORY`.
  Isso significa que a dedução deve ser executada dentro da mesma transação
  que o fechamento do chamado (por exemplo, o método `ResolveTicketUseCase.execute(...)`).
  Essa abordagem garante atomicidade: redução de `current_stock`, alteração de
  lotes (`stock_batch`) e persistência do `stock_movement` são comprometidos
  ou revertidos juntos.

Relatórios:
- O cálculo de valor do relatório de saídas agora aceita movimentos onde
  `unit_price_at_time` é nulo. Nesses casos, o valor é tratado como zero e,
  se houver ao menos um movimento referenciando o `TICKET:{id}`, o total do
  ticket será a soma dos `unit_price_at_time` (com nulos = 0). Isso evita que
  saídas de tickets sejam ignoradas pelo relatório apenas por falta do preço
  registrado no movimento.

Arquivos alterados:
- `ResolveTicketUseCase.java` — fallback de persistência e comentários em PT-BR
- `InventoryPricingService.java` — filtra movimentos por `StockMovementType.OUT`
- `StockMovementRepository.java` — novo método de consulta por referência e tipo

Se quiser, posso também adicionar testes de integração que simulam a
falha de persistência e validam o comportamento de fallback.
