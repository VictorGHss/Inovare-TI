package br.dev.ctrls.inovareti.modules.finance.infrastructure.adapter.input;

import io.micrometer.observation.annotation.Observed;

import br.dev.ctrls.inovareti.modules.finance.application.service.FinanceiroQueryService;
import br.dev.ctrls.inovareti.modules.finance.application.service.FinanceiroOperationsService;
import br.dev.ctrls.inovareti.modules.finance.domain.model.ProcessedReceiptStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.MDC;
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

import br.dev.ctrls.inovareti.core.shared.domain.model.exception.BadRequestException;
import br.dev.ctrls.inovareti.core.shared.domain.port.output.AuditPort;
import br.dev.ctrls.inovareti.modules.finance.application.service.ContaAzulAutomationService;
import br.dev.ctrls.inovareti.modules.finance.domain.model.ContaAzulHttpException;
import br.dev.ctrls.inovareti.modules.finance.domain.model.ContaAzulPaymentParcel;
import br.dev.ctrls.inovareti.modules.finance.domain.model.SyncDoctorsResult;
import br.dev.ctrls.inovareti.modules.notification.application.service.FinanceEmailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Adaptador de entrada REST para operações financeiras e faturamentos.
 *
 * Atua puramente como um controlador REST magro (Thin Controller), delegando lógicas
 * de validação, consultas e paginação de dados para o FinanceiroQueryService.
 */
@Slf4j
@RestController
@RequestMapping("/financeiro")
@RequiredArgsConstructor
@Tag(name = "Financeiro & Faturamento", description = "Operações administrativas do motor financeiro e integração com Conta Azul")
@Observed
public class FinanceiroController {

    private final FinanceiroOperationsService financeiroOperationsService;
    private final FinanceiroQueryService financeiroQueryService;
    private final ContaAzulAutomationService contaAzulAutomationService;
    private final FinanceEmailService receiptService;
    private final AuditPort auditPort;

    /**
     * Lista os recibos processados pelo motor financeiro.
     * <p>Role necessária: ADMIN ou FINANCE_MANAGER</p>
     */
    @Operation(
        summary = "Lista os recibos processados pelo motor financeiro",
        description = "Retorna os recibos consolidados e gerados a partir do faturamento de parcelas de vendas de médicos."
    )
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE_MANAGER')")
    @GetMapping("/recibos")
    public ResponseEntity<List<FinanceReceiptResponseDTO>> listReceipts(
            @RequestParam(required = false) ProcessedReceiptStatus status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        List<FinanceReceiptResponseDTO> response = financeiroQueryService.listReceipts(status, page, size);
        return ResponseEntity.ok(response);
    }

    /**
     * Lista os alertas do sistema financeiro.
     * <p>Role necessária: ADMIN ou FINANCE_MANAGER</p>
     */
    @Operation(
        summary = "Lista os alertas do sistema financeiro",
        description = "Busca logs e registros de notificações de alerta ou erros de processamento e e-mail."
    )
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE_MANAGER')")
    @GetMapping("/alertas")
    public ResponseEntity<List<FinanceAlertResponseDTO>> listAlerts() {
        return ResponseEntity.ok(financeiroQueryService.listAlerts());
    }

    /**
     * Reenvia um recibo pendente ou com falha.
     * <p>Role necessária: ADMIN ou FINANCE_MANAGER</p>
     */
    @Operation(
        summary = "Reenvia um recibo pendente ou com falha",
        description = "Envia novamente um recibo por e-mail de forma assíncrona."
    )
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE_MANAGER')")
    @PostMapping("/alertas/{alertId}/reenviar")
    public ResponseEntity<Void> requeueAlert(@PathVariable UUID alertId) {
        financeiroOperationsService.requeueAlertReceipt(alertId, getAuthenticatedUserId());
        return ResponseEntity.noContent().build();
    }

    /**
     * Executa a rotina de backfill dos últimos 30 dias.
     * <p>Role necessária: ADMIN ou FINANCE_MANAGER</p>
     */
    @Operation(
        summary = "Executa a rotina de backfill dos últimos 30 dias",
        description = "Processa retroativamente as vendas e liquidações financeiras da Conta Azul para o período recente."
    )
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE_MANAGER')")
    @PostMapping("/backfill")
    public ResponseEntity<FinanceiroOperationsService.BackfillResult> runBackfill() {
        return ResponseEntity.ok(financeiroOperationsService.executarBackfillUltimos30Dias());
    }

    /**
     * Processa individualmente uma parcela informada por ID.
     * <p>Role necessária: ADMIN ou FINANCE_MANAGER</p>
     */
    @Operation(
        summary = "Processa individualmente uma parcela informada por ID",
        description = "Efetua a baixa e gera a notificação de recibo para uma parcela individual de venda da Conta Azul."
    )
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE_MANAGER')")
    @GetMapping("/parcelas/{id}/processar")
    public ResponseEntity<FinanceiroOperationsService.ParcelProcessingResult> processParcelById(
            @PathVariable("id") String parcelaId) {
        FinanceiroOperationsService.ParcelProcessingResult result =
            financeiroOperationsService.conciliarParcelaPorId(parcelaId);
        registrarAuditoria(
            "FATURAMENTO_GERADO",
            "Processamento manual de parcela executado. parcelaId=" + parcelaId);
        return ResponseEntity.ok(result);
    }

    /**
     * Retorna o resumo consolidado do fluxo financeiro.
     * <p>Role necessária: ADMIN ou FINANCE_MANAGER</p>
     */
    @Operation(
        summary = "Retorna o resumo consolidado do fluxo financeiro",
        description = "Disponibiliza os totais faturados, saldos pendentes e status da integração ativa da Conta Azul."
    )
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE_MANAGER')")
    @GetMapping("/resumo")
    public ResponseEntity<FinanceSummaryResponseDTO> getResumoFinanceiro() {
        return ResponseEntity.ok(financeiroQueryService.getResumoFinanceiro());
    }

    /**
     * Executa manualmente a automação de vendas da Conta Azul.
     * <p>Role necessária: ADMIN ou FINANCE_MANAGER</p>
     */
    @Operation(
        summary = "Executa manualmente a automação de vendas da Conta Azul",
        description = "Dispara de forma síncrona a busca e processamento de vendas quitadas em um período específico na Conta Azul."
    )
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE_MANAGER')")
    @PostMapping("/autonacao/executar")
    public ResponseEntity<AutomationExecutionResponseDTO> executeAutomationNow(
            @RequestParam LocalDate dataInicio,
            @RequestParam LocalDate dataFim) {
        log.info("Iniciando sincronização solicitada pelo usuário: Período [{}] a [{}]", dataInicio, dataFim);
        financeiroQueryService.validarIntervaloDatas(dataInicio, dataFim);
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

            registrarAuditoria(
                "FATURAMENTO_GERADO",
                "Automação financeira manual concluída. periodo=" + dataInicio + " a " + dataFim
                    + ", duracaoMs=" + durationMs);

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

    /**
     * Sincroniza a base de médicos da Conta Azul com o banco local.
     * <p>Role necessária: ADMIN ou FINANCE_MANAGER</p>
     */
    @Operation(
        summary = "Sincroniza a base de médicos com a Conta Azul",
        description = "Busca todos os médicos mapeados como clientes na Conta Azul e atualiza os registros locais."
    )
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE_MANAGER')")
    @PostMapping("/medicos/sincronizar-base")
    public ResponseEntity<SyncDoctorsResponseDTO> syncDoctorsBase() {
        try {
            SyncDoctorsResult result = contaAzulAutomationService.syncAllDoctorsFromContaAzul();
            return ResponseEntity.ok(new SyncDoctorsResponseDTO(
                    result.novos(),
                    result.atualizados()));
        } catch (RuntimeException ex) {
            if (ex instanceof ContaAzulHttpException httpEx
                    && httpEx.isStatus(403)
                    && isPlanIneligibleResponse(httpEx.getResponseBody())) {
                log.warn("Sincronização de médicos indisponível: conta Conta Azul sem elegibilidade de API (END_TRIAL). Retornando resultado vazio.");
            } else {
                log.error("Falha de integração ao sincronizar base de médicos com a Conta Azul. Retornando resultado vazio para manter serviço ativo.", ex);
            }

            registrarAuditoria(
                    "CIRCUIT_BREAKER_FALLBACK",
                    "Sincronização de médicos falhou. Retornando resultado vazio para manter o serviço ativo.");

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

    /**
     * Dispara um envio de recibo de teste por e-mail.
     * <p>Role necessária: ADMIN ou FINANCE_MANAGER</p>
     */
    @Operation(
        summary = "Dispara um envio de recibo de teste por e-mail",
        description = "Envia um recibo de layout estruturado com dados mockados para testes do template de faturamento."
    )
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE_MANAGER')")
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

    private void registrarAuditoria(String action, String details) {
        auditPort.record("FINANCEIRO", action, details, resolveTraceId());
    }

    private String resolveTraceId() {
        String traceId = MDC.get("traceId");
        if (traceId == null || traceId.isBlank()) {
            traceId = MDC.get("trace_id");
        }
        return traceId;
    }

    public record FinanceReceiptResponseDTO(
            UUID id,
            String parcelaId,
            String commercialNumber,
            String referenceCode,
            String displayIdentifier,
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
            boolean integrationActive,
            String lastUpdatedAt) {
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
}




