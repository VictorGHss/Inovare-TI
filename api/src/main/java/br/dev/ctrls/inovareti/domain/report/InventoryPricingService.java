package br.dev.ctrls.inovareti.domain.report;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import br.dev.ctrls.inovareti.modules.finance.domain.model.FinancialTransaction;
import br.dev.ctrls.inovareti.modules.finance.domain.port.FinancialTransactionRepository;
import br.dev.ctrls.inovareti.domain.inventory.StockBatch;
import br.dev.ctrls.inovareti.domain.inventory.StockBatchRepository;
import br.dev.ctrls.inovareti.domain.inventory.StockMovement;
import br.dev.ctrls.inovareti.domain.inventory.StockMovementRepository;
import br.dev.ctrls.inovareti.domain.inventory.StockMovementType;
import br.dev.ctrls.inovareti.domain.ticket.Ticket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * ServiÃƒÂ§o especializado em precificaÃƒÂ§ÃƒÂ£o de saÃƒÂ­das de inventÃƒÂ¡rio para relatÃƒÂ³rios.
 *
 * Responsabilidades:
 * - centralizar cÃƒÂ¡lculo de valor total por ticket de saÃƒÂ­da;
 * - manter o mesmo comportamento matemÃƒÂ¡tico legado (sem alterar fÃƒÂ³rmulas);
 * - encapsular o motor de fallback de preÃƒÂ§o utilizado hoje pela aplicaÃƒÂ§ÃƒÂ£o.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class InventoryPricingService {

    private final StockBatchRepository stockBatchRepository;
    private final StockMovementRepository stockMovementRepository;
    private final FinancialTransactionRepository transactionRepository;

    /**
     * Calcula o valor total de saÃƒÂ­da para uma lista de tickets.
     *
     * O mapa ÃƒÂ© indexado pelo ID do ticket para ser reutilizado por exportadores
     * (Excel/PDF) sem duplicar regra de cÃƒÂ¡lculo.
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
     * Calcula o preÃƒÂ§o total de uma saÃƒÂ­da de item priorizando o valor registrado
     * em financial_transactions.amount (se existir) ou, alternativamente,
     * somando unit_price_at_time dos movimentos de estoque relacionados ao chamado.
     *
     * Como fallback final, utiliza o preÃƒÂ§o do lote mais recente multiplicado pela
     * quantidade. Esta matemÃƒÂ¡tica foi preservada integralmente do comportamento legado.
     */
    public BigDecimal calculateExitTotalPrice(Ticket ticket, int quantity) {
        // 1) Tenta obter lanÃƒÂ§amentos financeiros vinculados ao ticket
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
            log.warn("Erro ao buscar lanÃƒÂ§amentos financeiros para ticket {}: {}", ticket.getId(), e.getMessage());
        }

        // 2) Fallback: somar unit_price_at_time dos movimentos de estoque referenciando o ticket
        try {
            String prefix = "TICKET:" + ticket.getId();
            // Busca apenas movimentos do tipo OUT referenciando o chamado
            List<StockMovement> movements = stockMovementRepository.findByReferenceStartingWithAndTypeOrderByDateDesc(prefix, StockMovementType.OUT);
            if (movements != null && !movements.isEmpty()) {
                // Se existirem movimentos vinculados ao ticket, somamos seus valores.
                // ObservaÃƒÂ§ÃƒÂ£o: alguns movimentos gerados automaticamente podem ter
                // `unit_price_at_time` nulo. Tratamos nulos como zero para que o
                // relatÃƒÂ³rio inclua as saÃƒÂ­das do ticket (mesmo com preÃƒÂ§o nÃƒÂ£o registrado).
                BigDecimal sum = BigDecimal.ZERO;
                for (StockMovement movement : movements) {
                    BigDecimal price = Optional.ofNullable(movement.getUnitPriceAtTime()).orElse(BigDecimal.ZERO);
                    sum = sum.add(price);
                }
                // Retornamos a soma mesmo que seja zero Ã¢â‚¬â€ isso garante que tickets
                // com movimentos registrados apareÃƒÂ§am no RelatÃƒÂ³rio de SaÃƒÂ­das.
                return sum;
            }
        } catch (Exception e) {
            log.warn("Erro ao buscar movimentos para ticket {}: {}", ticket.getId(), e.getMessage());
        }

        // 3) Fallback final: preÃƒÂ§o unitÃƒÂ¡rio do lote mais recente * quantidade
        var item = ticket.getRequestedItem();
        if (item == null) {
            return BigDecimal.ZERO;
        }

        List<StockBatch> batches = stockBatchRepository.findByItemOrderByEntryDateDesc(item);
        if (batches.isEmpty()) {
            log.warn("No batches found for item {}, returning zero price", item.getId());
            return BigDecimal.ZERO;
        }

        BigDecimal unitPrice = batches.get(0).getUnitPrice();
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}

