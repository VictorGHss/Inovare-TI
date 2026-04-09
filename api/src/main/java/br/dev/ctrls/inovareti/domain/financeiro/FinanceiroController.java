package br.dev.ctrls.inovareti.domain.financeiro;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.dev.ctrls.inovareti.core.exception.BadRequestException;
import br.dev.ctrls.inovareti.domain.financeiro.contaazul.ContaAzulAutomationService;
import br.dev.ctrls.inovareti.domain.financeiro.contaazul.ContaAzulFinancialSummaryService;
import br.dev.ctrls.inovareti.domain.financeiro.contaazul.ContaAzulHttpException;
import br.dev.ctrls.inovareti.domain.financeiro.contaazul.ContaAzulPaymentParcel;
import br.dev.ctrls.inovareti.domain.financeiro.contaazul.ContaAzulTokenService;
import br.dev.ctrls.inovareti.domain.financeiro.contaazul.SyncDoctorsResult;
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
    private final ContaAzulAutomationService contaAzulAutomationService;
    private final ContaAzulTokenService contaAzulTokenService;
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
    @GetMapping("/parcelas/{id}/processar")
    public ResponseEntity<FinanceiroOperationsService.ParcelProcessingResult> processParcelById(
            @PathVariable("id") String parcelaId) {
        return ResponseEntity.ok(financeiroOperationsService.processParcelById(parcelaId));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/resumo")
    public ResponseEntity<FinanceSummaryResponseDTO> getResumoFinanceiro() {
        boolean integrationActive = contaAzulTokenService.hasPersistedTokenRecord();

        if (!integrationActive) {
            return ResponseEntity.ok(new FinanceSummaryResponseDTO(
                0L,
                0L,
                0L,
                "BRL",
                0L,
                false,
                false));
        }

        ContaAzulFinancialSummaryService.FinancialSummary summary = contaAzulFinancialSummaryService.fetchSummary();
        return ResponseEntity.ok(new FinanceSummaryResponseDTO(
                summary.balanceCents(),
                summary.totalPendingCents(),
                summary.totalPaidCents(),
                summary.currency(),
                summary.syncedReceiptsCount(),
            summary.externalServiceAvailable(),
            true));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/autonacao/executar")
        public ResponseEntity<AutomationExecutionResponseDTO> executeAutomationNow(
            @RequestParam LocalDate dataInicio,
            @RequestParam LocalDate dataFim) {
        log.info("Iniciando sincronização solicitada pelo usuário: Período [{}] a [{}]", dataInicio, dataFim);
        try {
            long start = System.currentTimeMillis();
            var result = contaAzulAutomationService.processAcquittedSales(dataInicio, dataFim);
            long durationMs = System.currentTimeMillis() - start;
            log.info("Automação financeira manual concluída em {} ms.", durationMs);

            boolean hasErrors = result.errors() != null && !result.errors().isEmpty();
            String status = hasErrors ? "warning" : "ok";
            String message = hasErrors
                ? "Automação executada manualmente com avisos. Verifique os logs para detalhes."
                : "Automação executada manualmente com sucesso após conclusão do processamento.";

            return ResponseEntity.ok(new AutomationExecutionResponseDTO(
                status,
                message,
                durationMs,
                result.errors(),
                result.noAttachmentWarnings(),
                result.mappingWarnings()));
        } catch (RuntimeException ex) {
            log.error("Falha ao executar automação financeira manual.", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                new AutomationExecutionResponseDTO(
                    "erro",
                    "Falha ao executar automação manual. Verifique os logs para detalhes.",
                    0L,
                    List.of(ex.getMessage()),
                    0,
                    0));
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/medicos/sincronizar-base")
    public ResponseEntity<SyncDoctorsResponseDTO> syncDoctorsBase() {
        try {
            SyncDoctorsResult result = contaAzulAutomationService.syncAllDoctorsFromContaAzul();
            return ResponseEntity.ok(new SyncDoctorsResponseDTO(
                    result.novos(),
                    result.atualizados()));
        } catch (RuntimeException ex) {
            // Blindagem obrigatória do endpoint de sincronização: erro externo não derruba o processo da API.
            if (ex instanceof ContaAzulHttpException httpEx
                    && httpEx.isStatus(403)
                    && isPlanIneligibleResponse(httpEx.getResponseBody())) {
                log.warn("Sincronização de médicos indisponível: conta Conta Azul sem elegibilidade de API (END_TRIAL). Retornando resultado vazio.");
            } else {
                log.error("Falha de integração ao sincronizar base de médicos com a Conta Azul. Retornando resultado vazio para manter serviço ativo.", ex);
            }

            return ResponseEntity.ok(new SyncDoctorsResponseDTO(0, 0));
        }
    }

    private boolean isPlanIneligibleResponse(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return false;
        }

        String normalized = responseBody.toUpperCase();
        return normalized.contains("END_TRIAL") || normalized.contains("NAO ESTA ELEGIVEL");
    }

    @GetMapping("/trigger-test-receipt")
    public ResponseEntity<Map<String, String>> triggerTestReceipt() {
        log.info("Iniciando envio de e-mail de teste (MODO TESTE ATIVO)");

        ContaAzulPaymentParcel parcelaTeste = new ContaAzulPaymentParcel(
                "TESTE-PARCELA-202603",
                "TESTE-CUSTOMER-DR-VICTOR",
                "Dr. Victor",
            "destinatario.original@inovareti.local",
            "10275");

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
            String currency,
            long syncedReceiptsCount,
            boolean externalServiceAvailable,
            boolean integrationActive) {
        }

        public record SyncDoctorsResponseDTO(
            int novos,
            int atualizados) {
        }

        public record AutomationExecutionResponseDTO(
            String status,
            String message,
            long durationMs,
            List<String> errors,
            int noAttachmentWarnings,
            int mappingWarnings) {
        }

            // Endpoint temporário de simulação removido.
}
