package br.dev.ctrls.inovareti.domain.financeiro;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.dev.ctrls.inovareti.core.exception.BadRequestException;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/financeiro")
@RequiredArgsConstructor
public class FinanceiroController {

    private final FinanceiroOperationsService financeiroOperationsService;

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/recibos")
    public ResponseEntity<List<FinanceReceiptResponseDTO>> listReceipts() {
        List<FinanceReceiptResponseDTO> response = financeiroOperationsService.listReceipts()
                .stream()
                .map(this::mapReceipt)
                .toList();

        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/alertas")
    public ResponseEntity<List<FinanceAlertResponseDTO>> listAlerts() {
        List<FinanceAlertResponseDTO> response = financeiroOperationsService.listAlerts()
                .stream()
                .map(this::mapAlert)
                .toList();

        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/alertas/{alertId}/reenviar")
    public ResponseEntity<Void> requeueAlert(@PathVariable UUID alertId) {
        financeiroOperationsService.requeueAlertReceipt(alertId, getAuthenticatedUserId());
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/backfill")
    public ResponseEntity<FinanceiroOperationsService.BackfillResult> runBackfill() {
        return ResponseEntity.ok(financeiroOperationsService.runBackfillLast30Days());
    }

    private FinanceReceiptResponseDTO mapReceipt(ProcessedReceipt receipt) {
        return new FinanceReceiptResponseDTO(
                receipt.getId(),
                receipt.getParcelaId(),
                receipt.getOriginalRecipientEmail(),
                receipt.getStatus(),
                receipt.getRetryCount(),
                receipt.getProcessedAt(),
                receipt.getPayload());
    }

    private FinanceAlertResponseDTO mapAlert(SystemAlert alert) {
        return new FinanceAlertResponseDTO(
                alert.getId(),
                alert.getAlertType(),
                alert.getSeverity(),
                alert.getSource(),
                alert.getTitle(),
                alert.getDetails(),
                alert.isResolved(),
                alert.getCreatedAt(),
                alert.getResolvedAt(),
                alert.getResolvedBy(),
                alert.getContext());
    }

    private UUID getAuthenticatedUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            throw new BadRequestException("Usuário autenticado não encontrado.");
        }

        try {
            return UUID.fromString(auth.getPrincipal().toString());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Identificador do usuário autenticado inválido.");
        }
    }

    public record FinanceReceiptResponseDTO(
            UUID id,
            String parcelaId,
            String originalRecipientEmail,
            ProcessedReceiptStatus status,
            int retryCount,
            LocalDateTime processedAt,
            Map<String, Object> payload) {
    }

    public record FinanceAlertResponseDTO(
            UUID id,
            String alertType,
            String severity,
            String source,
            String title,
            String details,
            boolean resolved,
            LocalDateTime createdAt,
            LocalDateTime resolvedAt,
            UUID resolvedBy,
            Map<String, Object> context) {
    }
}
