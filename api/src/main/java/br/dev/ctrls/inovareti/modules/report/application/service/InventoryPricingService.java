package br.dev.ctrls.inovareti.modules.report.application.service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import br.dev.ctrls.inovareti.modules.finance.domain.model.FinancialTransaction;
import br.dev.ctrls.inovareti.modules.finance.domain.port.FinancialTransactionRepository;
import br.dev.ctrls.inovareti.modules.inventory.domain.model.StockBatch;
import br.dev.ctrls.inovareti.modules.inventory.domain.port.output.StockBatchRepositoryPort;
import br.dev.ctrls.inovareti.modules.inventory.domain.model.StockMovement;
import br.dev.ctrls.inovareti.modules.inventory.domain.port.output.StockMovementRepositoryPort;
import br.dev.ctrls.inovareti.modules.inventory.domain.model.StockMovementType;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.Ticket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço especializado em precificação de saídas de inventário para relatórios.
 *
 * Responsabilidades:
 * - centralizar cálculo de valor total por ticket de saída;
 * - manter o mesmo comportamento matemático legado (sem alterar fórmulas);
 * - encapsular o motor de fallback de preço utilizado hoje pela aplicação.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class InventoryPricingService {

    private final StockBatchRepositoryPort stockBatchRepository;
    private final StockMovementRepositoryPort stockMovementRepository;
    private final FinancialTransactionRepository transactionRepository;

    /**
     * Calcula o valor total de saída para uma lista de tickets.
     *
     * O mapa é indexado pelo ID do ticket para ser reutilizado por exportadores
     * (Excel/PDF) sem duplicar regra de cálculo.
     */
    public Map<UUID, BigDecimal> calculateExitTotalsByTicket(List<Ticket> tickets) {
        Map<UUID, BigDecimal> totals = new LinkedHashMap<>();

        for (Ticket ticket : tickets) {
            if (ticket == null || ticket.getId() == null) {
                continue;
            }

            int qty = Optional.ofNullable(ticket.getRequestedQuantity()).orElse(0);
            totals.put(ticket.getId(), calculateExitTotalPrice(ticket, qty));
        }

        return totals;
    }

    /**
     * Calcula o preço total de uma saída de item priorizando o valor registrado
     * em financial_transactions.amount (se existir) ou, alternativamente,
     * somando unit_price_at_time dos movimentos de estoque relacionados ao chamado.
     *
     * Como fallback final, utiliza o preço do lote mais recente multiplicado pela
     * quantidade. Esta matemática foi preservada integralmente do comportamento legado.
     */
    public BigDecimal calculateExitTotalPrice(Ticket ticket, int quantity) {
        // 1) Tenta obter lançamentos financeiros vinculados ao ticket
        try {
            var txs = transactionRepository.findByTicketId(ticket.getId());
            if (txs != null && !txs.isEmpty()) {
                BigDecimal sum = BigDecimal.ZERO;
                for (FinancialTransaction tx : txs) {
                    if (tx.getResourceType() == FinancialTransaction.ResourceType.INVENTORY
                            && tx.getAmount() != null) {
                        sum = sum.add(tx.getAmount());
                    }
                }
                if (sum.compareTo(BigDecimal.ZERO) > 0) {
                    return sum;
                }
            }
        } catch (Exception e) {
            log.warn("Erro ao buscar lançamentos financeiros para ticket {}: {}", ticket.getId(), e.getMessage());
        }

        // 2) Fallback: somar unit_price_at_time dos movimentos de estoque referenciando o ticket
        try {
            String prefix = "TICKET:" + ticket.getId();
            // Busca apenas movimentos do tipo OUT referenciando o chamado
            List<StockMovement> movements = stockMovementRepository.findByReferenceStartingWithAndTypeOrderByDateDesc(prefix, StockMovementType.OUT);
            if (movements != null && !movements.isEmpty()) {
                BigDecimal sum = BigDecimal.ZERO;
                for (StockMovement movement : movements) {
                    BigDecimal price = Optional.ofNullable(movement.getUnitPriceAtTime()).orElse(BigDecimal.ZERO);
                    sum = sum.add(price);
                }
                return sum;
            }
        } catch (Exception e) {
            log.warn("Erro ao buscar movimentos para ticket {}: {}", ticket.getId(), e.getMessage());
        }

        // 3) Fallback final: preço unitário do lote mais recente * quantidade
        var item = ticket.getRequestedItem();
        if (item == null) {
            return BigDecimal.ZERO;
        }

        List<StockBatch> batches = stockBatchRepository.findByItemOrderByEntryDateDesc(item);
        if (batches.isEmpty()) {
            log.warn("Nenhum lote encontrado para o item {}, retornando preço zero", item.getId());
            return BigDecimal.ZERO;
        }

        BigDecimal unitPrice = batches.get(0).getUnitPrice();
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
