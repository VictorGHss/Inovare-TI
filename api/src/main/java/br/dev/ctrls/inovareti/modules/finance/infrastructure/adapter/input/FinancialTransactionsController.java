package br.dev.ctrls.inovareti.modules.finance.infrastructure.adapter.input;

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

import br.dev.ctrls.inovareti.modules.finance.application.service.FinancialTransactionSpecification;
import br.dev.ctrls.inovareti.modules.finance.domain.model.FinancialTransaction;
import br.dev.ctrls.inovareti.modules.finance.domain.port.FinancialTransactionRepository;
import br.dev.ctrls.inovareti.modules.inventory.domain.model.StockMovement;
import br.dev.ctrls.inovareti.modules.inventory.domain.port.output.ItemRepositoryPort;
import br.dev.ctrls.inovareti.modules.inventory.domain.port.output.StockMovementRepositoryPort;
import br.dev.ctrls.inovareti.modules.user.domain.port.output.SectorRepositoryPort;
import br.dev.ctrls.inovareti.modules.user.domain.port.output.UserRepositoryPort;
import lombok.RequiredArgsConstructor;

/**
 * Controller que expõe transações financeiras internas e suas linhas derivadas
 * a partir de movimentos de estoque (stock_movements).
 * <p>Role necessária: ADMIN ou FINANCE_MANAGER</p>
 */
@RestController
@RequestMapping("/financial")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'FINANCE_MANAGER')")
public class FinancialTransactionsController {

    private final FinancialTransactionRepository transactionRepository;
    private final StockMovementRepositoryPort stockMovementRepository;
    private final ItemRepositoryPort itemRepository;
    private final UserRepositoryPort userRepository;
    private final SectorRepositoryPort sectorRepository;

    // Usando o Specification Pattern para construir a consulta dinamicamente.
    @GetMapping("/transactions")
    public ResponseEntity<List<TransactionLineDTO>> listTransactions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        
        // Declaração local para garantir resolução sintática correta
        LocalDate start = startDate;
        LocalDate end = endDate;

        // Construindo a Specification a partir dos parâmetros do request
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

