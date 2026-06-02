package br.dev.ctrls.inovareti.modules.finance.application.service;

import br.dev.ctrls.inovareti.modules.finance.domain.model.ProcessedReceipt;
import br.dev.ctrls.inovareti.modules.finance.domain.model.SystemAlert;
import br.dev.ctrls.inovareti.modules.finance.domain.model.ProcessedReceiptStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import br.dev.ctrls.inovareti.core.exception.BadRequestException;
import br.dev.ctrls.inovareti.modules.finance.infrastructure.adapter.input.FinanceiroController.FinanceAlertResponseDTO;
import br.dev.ctrls.inovareti.modules.finance.infrastructure.adapter.input.FinanceiroController.FinanceReceiptResponseDTO;
import br.dev.ctrls.inovareti.modules.finance.infrastructure.adapter.input.FinanceiroController.FinanceSummaryResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço de aplicação focado em consultas e mapeamentos do subsistema financeiro.
 *
 * Restabelece a separação de responsabilidades da Arquitetura Hexagonal, removendo lógicas
 * de agregação, filtros, paginação e validação do controlador de entrada REST.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FinanceiroQueryService {

    private final FinanceiroOperationsService financeiroOperationsService;
    private final ContaAzulFinancialSummaryService contaAzulFinancialSummaryService;
    private final ContaAzulTokenService contaAzulTokenService;

    /**
     * Valida se o intervalo de datas fornecido para a automação é coerente.
     *
     * @param dataInicio Data de início da busca.
     * @param dataFim Data final da busca.
     * @throws BadRequestException se o período for inválido.
     */
    public void validarIntervaloDatas(LocalDate dataInicio, LocalDate dataFim) {
        if (dataInicio == null || dataFim == null) {
            throw new BadRequestException("As datas de início e fim não podem ser nulas.");
        }
        if (dataInicio.isAfter(dataFim)) {
            throw new BadRequestException("A data de início não pode ser posterior à data de fim.");
        }
        if (dataInicio.plusDays(365).isBefore(dataFim)) {
            throw new BadRequestException("O período máximo permitido para sincronização é de 365 dias.");
        }
    }

    /**
     * Consolida e constrói o resumo financeiro atualizado, verificando também o status da integração.
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
     * Lista recibos com paginação em memória e filtros por status opcionais.
     *
     * @param status Status de processamento do recibo (opcional).
     * @param page Número da página a ser retornada (0-indexed, opcional).
     * @param size Quantidade de elementos por página (opcional).
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

        // Paginação customizada em sublista com tratamento seguro de índices
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


