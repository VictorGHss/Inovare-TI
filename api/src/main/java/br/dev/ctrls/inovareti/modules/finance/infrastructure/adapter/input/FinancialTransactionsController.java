package br.dev.ctrls.inovareti.modules.finance.infrastructure.adapter.input;

import br.dev.ctrls.inovareti.modules.finance.domain.model.FinancialTransaction;
import br.dev.ctrls.inovareti.modules.finance.application.service.FinancialTransactionSpecification;
import br.dev.ctrls.inovareti.modules.finance.domain.port.FinancialTransactionRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.dev.ctrls.inovareti.domain.inventory.ItemRepository;
import br.dev.ctrls.inovareti.domain.inventory.StockMovement;
import br.dev.ctrls.inovareti.domain.inventory.StockMovementRepository;
import br.dev.ctrls.inovareti.domain.user.SectorRepository;
import br.dev.ctrls.inovareti.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;

/**
 * Controller que expÃƒÂµe transaÃƒÂ§ÃƒÂµes financeiras internas e suas linhas derivadas
 * a partir de movimentos de estoque (stock_movements).
 * <p>Role necessÃƒÂ¡ria: ADMIN ou FINANCE_MANAGER</p>
 */
@RestController
@RequestMapping("/financial")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'FINANCE_MANAGER')")
public class FinancialTransactionsController {

    private final FinancialTransactionRepository transactionRepository;
    private final StockMovementRepository stockMovementRepository;
    private final ItemRepository itemRepository;
    private final UserRepository userRepository;
    private final SectorRepository sectorRepository;

    // Usando o Specification Pattern para construir a consulta dinamicamente.
    @GetMapping("/transactions")
    public ResponseEntity<List<TransactionLineDTO>> listTransactions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        
        // DeclaraÃƒÂ§ÃƒÂ£o local para garantir resoluÃƒÂ§ÃƒÂ£o sintÃƒÂ¡tica correta
        LocalDate start = startDate;
        LocalDate end = endDate;

        // Construindo a Specification a partir dos parÃƒÂ¢metros do request
        FinancialTransactionSpecification spec = FinancialTransactionSpecification.builder()
            .startDate(start).endDate(end).build();
        
        // Executando a consulta usando a Specification
        List<FinancialTransaction> transactions = transactionRepository.findAll(spec);

        // Agrupa por ticketId e produz linhas a partir de stock_movements referenciando o ticket
        List<TransactionLineDTO> lines = new ArrayList<>();

        // Cache de nomes para evitar N+1
        Map<UUID, String> userNameCache = new HashMap<>();
        Map<UUID, String> sectorNameCache = new HashMap<>();

        for (FinancialTransaction tx : transactions) {
            if (tx.getTicketId() == null) continue;

            String prefix = "TICKET:" + tx.getTicketId();
            List<StockMovement> movements = stockMovementRepository.findByReferenceStartingWithOrderByDateDesc(prefix);

            String destinationName;
            if (tx.getTargetType() == FinancialTransaction.TargetType.DOCTOR) {
                UUID uid = tx.getTargetId();
                destinationName = userNameCache.computeIfAbsent(uid, k -> userRepository.findById(k).map(u -> u.getName()).orElse("Doctor " + k));
            } else {
                UUID sid = tx.getTargetId();
                destinationName = sectorNameCache.computeIfAbsent(sid, k -> sectorRepository.findById(k).map(s -> s.getName()).orElse("Sector " + k));
            }

            for (StockMovement m : movements) {
                String itemName = itemRepository.findById(m.getItemId()).map(i -> i.getName()).orElse("-");
                BigDecimal rawAmount = m.getUnitPriceAtTime();
                long amountCents = rawAmount != null ? rawAmount.movePointRight(2).longValue() : 0L;
                lines.add(new TransactionLineDTO(
                        tx.getId(),
                        m.getDate(),
                        tx.getTargetType().name(),
                        destinationName,
                        itemName,
                        m.getQuantity(),
                        amountCents
                ));
            }
        }

        return ResponseEntity.ok(lines);
    }

        public record TransactionLineDTO(
            UUID transactionId,
            java.time.LocalDateTime date,
            String targetType,
            String destination,
            String item,
            int quantity,
            long amountCents) {}

}

