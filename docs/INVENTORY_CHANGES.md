## Correção: Persistência de Movimentações de Saída ao Fechar Chamados

Resumo das alterações implementadas para corrigir o bug onde o `current_stock` era reduzido
mas a movimentação não aparecia no Relatório Geral de Saídas:

- Adicionada verificação adicional após dedução FIFO para garantir que um registro
  em `stock_movements` exista; em caso negativo, é criado um registro `OUT`
  (fallback) com `reference = TICKET:{ticketId}|REQUESTER:{requesterId}`.
- Ajustada a geração de valores para relatórios para filtrar apenas movimentos
  do tipo `OUT` quando somar `unit_price_at_time` associados a um chamado.

Motivação:
- Evitar perda de trilha de auditoria quando, por algum motivo inesperado,
  o serviço de dedução não persistir a movimentação (ex.: falha transacional
  pontual). O fallback garante consistência entre redução de estoque e relatório.

Observações:
- A lógica padrão continua sendo a criação do `StockMovement` pelo
  `StockDeductionService` (FIFO). O fallback é apenas uma proteção adicional.
- Não houve alteração na API pública ou nos contratos existentes.

Arquivos alterados:
- `ResolveTicketUseCase.java` — fallback de persistência e comentários em PT-BR
- `InventoryPricingService.java` — filtra movimentos por `StockMovementType.OUT`
- `StockMovementRepository.java` — novo método de consulta por referência e tipo

Se quiser, posso também adicionar testes de integração que simulam a
falha de persistência e validam o comportamento de fallback.
