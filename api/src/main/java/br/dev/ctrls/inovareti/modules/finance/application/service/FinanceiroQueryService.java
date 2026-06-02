package br.dev.ctrls.inovareti.modules.finance.application.service;

import io.micrometer.observation.annotation.Observed;

import br.dev.ctrls.inovareti.modules.finance.domain.model.ProcessedReceipt;
import br.dev.ctrls.inovareti.modules.finance.domain.model.SystemAlert;
import br.dev.ctrls.inovareti.modules.finance.domain.model.ProcessedReceiptStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import br.dev.ctrls.inovareti.core.shared.domain.model.exception.BadRequestException;
import br.dev.ctrls.inovareti.modules.finance.infrastructure.adapter.input.FinanceiroController.FinanceAlertResponseDTO;
import br.dev.ctrls.inovareti.modules.finance.infrastructure.adapter.input.FinanceiroController.FinanceReceiptResponseDTO;
import br.dev.ctrls.inovareti.modules.finance.infrastructure.adapter.input.FinanceiroController.FinanceSummaryResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * ServiÃ§o de aplicaÃ§Ã£o focado em consultas e mapeamentos do subsistema financeiro.
 *
 * Restabelece a separaÃ§Ã£o de responsabilidades da Arquitetura Hexagonal, removendo lÃ³gicas
 * de agregaÃ§Ã£o, filtros, paginaÃ§Ã£o e validaÃ§Ã£o do controlador de entrada REST.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Observed
public class FinanceiroQueryService {

    private final FinanceiroOperationsService financeiroOperationsService;
    private final ContaAzulFinancialSummaryService contaAzulFinancialSummaryService;
    private final ContaAzulTokenService contaAzulTokenService;

    /**
     * Valida se o intervalo de datas fornecido para a automaÃ§Ã£o Ã© coerente.
     *
     * @param dataInicio Data de inÃ­cio da busca.
     * @param dataFim Data final da busca.
     * @throws BadRequestException se o perÃ­odo for invÃ¡lido.
     */
    public void validarIntervaloDatas(LocalDate dataInicio, LocalDate dataFim) {
        if (dataInicio == null || dataFim == null) {
            throw new BadRequestException("As datas de inÃ­cio e fim nÃ£o podem ser nulas.");
        }
        if (dataInicio.isAfter(dataFim)) {
            throw new BadRequestException("A data de inÃ­cio nÃ£o pode ser posterior Ã  data de fim.");
        }
        if (dataInicio.plusDays(365).isBefore(dataFim)) {
            throw new BadRequestException("O perÃ­odo mÃ¡ximo permitido para sincronizaÃ§Ã£o Ã© de 365 dias.");
        }
    }

    /**
     * Consolida e constrÃ³i o resumo financeiro atualizado, verificando tambÃ©m o status da integraÃ§Ã£o.
     *
     * @return DTO com o resumo consolidado dos faturamentos e status do Conta Azul.
     */
    public FinanceSummaryResponseDTO getResumoFinanceiro() {
        boolean integrationActive = contaAzulTokenService.hasPersistedTokenRecord();

        if (!integrationActive) {
            return new FinanceSummaryResponseDTO(
                    0L,
                    0L,
                    0L,
                    "BRL",
                    0L,
                    false,
                    false,
                    null);
        }

        ContaAzulFinancialSummaryService.FinancialSummary summary = contaAzulFinancialSummaryService.fetchSummary();
        return new FinanceSummaryResponseDTO(
                summary.balanceCents(),
                summary.totalPendingCents(),
                summary.totalPaidCents(),
                summary.currency(),
                summary.syncedReceiptsCount(),
                summary.externalServiceAvailable(),
                true,
                summary.lastUpdatedAt());
    }

    /**
     * Lista recibos com paginaÃ§Ã£o em memÃ³ria e filtros por status opcionais.
     *
     * @param status Status de processamento do recibo (opcional).
     * @param page NÃºmero da pÃ¡gina a ser retornada (0-indexed, opcional).
     * @param size Quantidade de elementos por pÃ¡gina (opcional).
     * @return Lista filtrada e paginada de recibos formatados em DTO.
     */
    public List<FinanceReceiptResponseDTO> listReceipts(ProcessedReceiptStatus status, Integer page, Integer size) {
        List<ProcessedReceipt> allReceipts = financeiroOperationsService.listReceipts();

        // Filtragem por status
        List<ProcessedReceipt> filtered = allReceipts;
        if (status != null) {
            filtered = allReceipts.stream()
                    .filter(r -> r.getStatus() == status)
                    .toList();
        }

        // Mapeamento para DTOs
        List<FinanceReceiptResponseDTO> mapped = filtered.stream()
                .map(this::mapReceipt)
                .toList();

        // PaginaÃ§Ã£o customizada em sublista com tratamento seguro de Ã­ndices
        if (page != null && size != null && size > 0 && page >= 0) {
            int fromIndex = page * size;
            if (fromIndex >= mapped.size()) {
                return List.of();
            }
            int toIndex = Math.min(fromIndex + size, mapped.size());
            return mapped.subList(fromIndex, toIndex);
        }

        return mapped;
    }

    /**
     * Lista e formata todos os alertas de faturamento e erros de processamento do motor financeiro.
     *
     * @return Lista de alertas mapeados em DTO.
     */
    public List<FinanceAlertResponseDTO> listAlerts() {
        return financeiroOperationsService.listAlerts()
                .stream()
                .map(this::mapAlert)
                .toList();
    }

    private FinanceReceiptResponseDTO mapReceipt(ProcessedReceipt receipt) {
        String commercialNumber = resolvePayloadText(receipt.getPayload(), "numero", "numero_venda", "saleNumber");
        String referenceCode = resolvePayloadText(receipt.getPayload(), "codigo_referencia", "codigoReferencia", "referenceCode");
        String displayIdentifier = StringUtils.hasText(commercialNumber)
                ? commercialNumber
                : (StringUtils.hasText(referenceCode) ? referenceCode : receipt.getParcelaId());

        return new FinanceReceiptResponseDTO(
                receipt.getId(),
                receipt.getParcelaId(),
                commercialNumber,
                referenceCode,
                displayIdentifier,
                receipt.getOriginalRecipientEmail(),
                receipt.getStatus(),
                receipt.getRetryCount(),
                receipt.getProcessedAt(),
                receipt.getPayload());
    }

    private String resolvePayloadText(Map<String, Object> payload, String... keys) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }

        for (String key : keys) {
            Object value = payload.get(key);
            if (value == null) {
                continue;
            }

            String resolved = String.valueOf(value).trim();
            if (StringUtils.hasText(resolved)) {
                return resolved;
            }
        }

        return null;
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
}




