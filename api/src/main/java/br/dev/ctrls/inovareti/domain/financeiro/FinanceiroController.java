package br.dev.ctrls.inovareti.domain.financeiro;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.dev.ctrls.inovareti.core.exception.BadRequestException;
import br.dev.ctrls.inovareti.domain.financeiro.contaazul.ContaAzulFinancialSummaryService;
import br.dev.ctrls.inovareti.domain.financeiro.contaazul.ContaAzulPaymentParcel;
import br.dev.ctrls.inovareti.domain.notification.FinanceEmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/financeiro")
@RequiredArgsConstructor
public class FinanceiroController {

    private final FinanceiroOperationsService financeiroOperationsService;
    private final ContaAzulFinancialSummaryService contaAzulFinancialSummaryService;
    private final FinanceEmailService receiptService;

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

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/polling/reprocessar")
    public ResponseEntity<PaymentPollingJob.PollingProcessingResult> reprocessPollingWindow(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(financeiroOperationsService.reprocessPollingWindow(from, to));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/resumo")
    public ResponseEntity<FinanceSummaryResponseDTO> getResumoFinanceiro() {
        ContaAzulFinancialSummaryService.FinancialSummary summary = contaAzulFinancialSummaryService.fetchSummary();
        return ResponseEntity.ok(new FinanceSummaryResponseDTO(
                summary.balanceCents(),
                summary.totalPendingCents(),
                summary.totalPaidCents(),
                summary.currency()));
    }

    @GetMapping("/trigger-test-receipt")
    public ResponseEntity<Map<String, String>> triggerTestReceipt() {
        log.info("Iniciando envio de e-mail de teste (MODO TESTE ATIVO)");

        ContaAzulPaymentParcel parcelaTeste = new ContaAzulPaymentParcel(
                "TESTE-PARCELA-202603",
                "TESTE-CUSTOMER-DR-VICTOR",
                "Dr. Victor",
                "destinatario.original@inovareti.local");

        receiptService.sendReceiptEmail(parcelaTeste);

        String resultado = "E-mail de teste disparado para parcela " + parcelaTeste.parcelaId();
        log.info("{}", resultado);

        return ResponseEntity.ok(Map.of("status", "ok", "message", resultado));
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

    public record FinanceSummaryResponseDTO(
            long balanceCents,
            long totalPendingCents,
            long totalPaidCents,
            String currency) {
    }
}
