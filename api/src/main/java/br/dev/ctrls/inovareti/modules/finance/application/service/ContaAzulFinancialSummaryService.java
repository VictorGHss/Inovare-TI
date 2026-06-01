package br.dev.ctrls.inovareti.modules.finance.application.service;

import br.dev.ctrls.inovareti.modules.finance.domain.model.AccountBalanceAudit;
import br.dev.ctrls.inovareti.modules.finance.domain.model.ContaAzulStatus;
import br.dev.ctrls.inovareti.modules.finance.domain.model.FinancialAccountRef;
import br.dev.ctrls.inovareti.modules.finance.domain.model.ReceivableParcelRef;
import br.dev.ctrls.inovareti.modules.finance.domain.model.ReceivedParcelsResult;
import br.dev.ctrls.inovareti.modules.finance.domain.model.StatusResult;
import br.dev.ctrls.inovareti.modules.finance.domain.port.ProcessedSaleRepository;
import br.dev.ctrls.inovareti.modules.finance.infrastructure.adapter.output.ContaAzulRestClientAdapter;
import br.dev.ctrls.inovareti.modules.finance.infrastructure.adapter.output.ContaAzulSummaryCalculator;
import br.dev.ctrls.inovareti.modules.finance.infrastructure.config.ContaAzulProperties;
import br.dev.ctrls.inovareti.modules.finance.domain.model.ReceivablesPageData;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * ServiÃƒÆ’Ã‚Â§o que atua como orquestrador da camada de aplicaÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Â£o (Application Service) para recuperar o resumo financeiro mensal.
 *
 * Ele coordena o fluxo chamando o adaptador de infraestrutura para buscar dados,
 * repassando para o calculador de domÃƒÆ’Ã‚Â­nio processar e consolidando o resultado final.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContaAzulFinancialSummaryService {

    private static final int SUMMARY_PAGE_SIZE = 100;
    private static final int SUMMARY_MAX_PAGES = 30;
    private static final LocalDate DIAGNOSTIC_PAYMENT_DATE_FROM = LocalDate.of(2026, 1, 1);

    private final ContaAzulTokenService contaAzulTokenService;
    private final ProcessedSaleRepository processedSaleRepository;
    private final ContaAzulRestClientAdapter restClientAdapter;
    private final ContaAzulSummaryCalculator summaryCalculator;
    private final ContaAzulProperties properties;

    @PostConstruct
    public void logV2BaseConfiguration() {
        log.info("ContaAzulFinancialSummaryService configurado com base URL v2: {}", properties.getApiV2BaseUrl());
    }

    /**
     * Recupera o resumo financeiro do mÃƒÆ’Ã‚Âªs atual.
     *
     * - `balanceCents`: saldo (representado aqui como total pago em centavos);
     * - `totalPendingCents`: total em aberto em centavos;
     * - `totalPaidCents`: total pago em centavos;
     * - `syncedReceiptsCount`: quantidade de recibos jÃƒÆ’Ã‚Â¡ registrados localmente.
     */
    @Cacheable(value = "contaAzulSummary", key = "'dashboard'")
    public FinancialSummary fetchSummary() {
        long syncedReceiptsCount = processedSaleRepository.count();
        ExecutorService executor = buildSummaryExecutor();

        try {
            String accessToken = contaAzulTokenService.getValidAccessToken();

            // Busca parcelas recebidas, contas em aberto e saldos em paralelo usando CompletableFuture.supplyAsync().
            CompletableFuture<ReceivedParcelsResult> receivedParcelsFuture = CompletableFuture.supplyAsync(
                    () -> safeFetchReceivedParcels(accessToken), executor);

            CompletableFuture<StatusResult> pendingFuture = CompletableFuture.supplyAsync(
                    () -> safeFetchTotalByStatus(accessToken, ContaAzulStatus.EM_ABERTO), executor);

            CompletableFuture<StatusResult> balanceFuture = CompletableFuture.supplyAsync(
                    () -> safeFetchConsolidatedBalance(accessToken, executor), executor);

            // Utiliza CompletableFuture.allOf(...).join() para unificar e aguardar o retorno de ambos os payloads simultaneamente
            CompletableFuture.allOf(receivedParcelsFuture, pendingFuture, balanceFuture).join();

            ReceivedParcelsResult receivedParcelsResult = receivedParcelsFuture.join();
            StatusResult pendingResult = pendingFuture.join();
            StatusResult balanceResult = balanceFuture.join();

            log.info("Resumo financeiro: parcelas recebidas identificadas no perÃƒÆ’Ã‚Â­odo = {}", receivedParcelsResult.parcels().size());
            StatusResult paidResult = safeFetchTotalPaidByBaixas(accessToken, receivedParcelsResult.parcels(), executor);

            long totalPaidCents = paidResult.total();
            long totalPendingCents = pendingResult.total();
            long balanceCents = balanceResult.total();

            // Regra de robustez: disponibilidade externa sÃƒÆ’Ã‚Â³ cai quando falha a listagem inicial de contas.
            boolean externalServiceAvailable = balanceResult.available();

            return new FinancialSummary(
                    balanceCents,
                    totalPendingCents,
                    totalPaidCents,
                    "BRL",
                    syncedReceiptsCount,
                    externalServiceAvailable,
                    summaryCalculator.resolveSummaryLastUpdatedAt(receivedParcelsResult.lastUpdatedAt()));
        } catch (Exception ex) {
            log.warn("Falha ao montar resumo financeiro da Conta Azul. Retornando fallback com serviÃƒÆ’Ã‚Â§o externo indisponÃƒÆ’Ã‚Â­vel.", ex);
            return new FinancialSummary(
                    0L,
                    0L,
                    0L,
                    "BRL",
                    syncedReceiptsCount,
                    false,
                    summaryCalculator.resolveSummaryLastUpdatedAt(null));
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException interruptedEx) {
                Thread.currentThread().interrupt();
                executor.shutdownNow();
            }
        }
    }

    private ExecutorService buildSummaryExecutor() {
        // Utiliza o pool dedicado de Virtual Threads (uma thread virtual por tarefa).
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    private StatusResult safeFetchTotalByStatus(String accessToken, String status) {
        try {
            BigDecimal totalDecimal = restClientAdapter.fetchTotalAmountByStatus(accessToken, status);
            if (totalDecimal == null) {
                // Caso 403 FORBIDDEN: retornar zero e marcar como indisponÃƒÆ’Ã‚Â­vel
                return new StatusResult(0L, false);
            }
            long totalCents = summaryCalculator.normalizeAmountToCents(totalDecimal, status.equals(ContaAzulStatus.RECEBIDO) ? "totais.pago.valor" : "totais.aberto.valor");
            return new StatusResult(totalCents, true);
        } catch (Exception ex) {
            log.warn("Falha ao consultar status '{}' na Conta Azul. Considerando indisponÃƒÆ’Ã‚Â­vel para este status.", status, ex);
            return new StatusResult(0L, false);
        }
    }

    private ReceivedParcelsResult safeFetchReceivedParcels(String accessToken) {
        try {
            return fetchReceivedParcels(accessToken);
        } catch (Exception ex) {
            log.warn("Falha ao consultar parcelas RECEBIDO para cÃƒÆ’Ã‚Â¡lculo real por baixas.", ex);
            return new ReceivedParcelsResult(List.of(), false, null);
        }
    }

    private StatusResult safeFetchConsolidatedBalance(String accessToken, ExecutorService executor) {
        return fetchConsolidatedBalance(accessToken, executor);
    }

    private StatusResult safeFetchTotalPaidByBaixas(
            String accessToken,
            List<ReceivableParcelRef> parcels,
            ExecutorService executor) {
        try {
            return fetchTotalPaidByBaixas(accessToken, parcels, executor);
        } catch (Exception ex) {
            log.warn("Falha ao calcular total pago com base nas baixas detalhadas das parcelas.", ex);
            return new StatusResult(0L, false);
        }
    }

    private StatusResult fetchConsolidatedBalance(String accessToken, ExecutorService executor) {
        List<FinancialAccountRef> financialAccounts;
        try {
            financialAccounts = restClientAdapter.fetchFinancialAccounts(accessToken);
        } catch (Exception ex) {
            // SÃƒÆ’Ã‚Â³ marca indisponÃƒÆ’Ã‚Â­vel quando a listagem inicial de contas falha por completo.
            log.warn("Falha ao listar contas financeiras na Conta Azul. Dashboard seguirÃƒÆ’Ã‚Â¡ indisponÃƒÆ’Ã‚Â­vel atÃƒÆ’Ã‚Â© nova leitura.", ex);
            return new StatusResult(0L, false);
        }

        if (financialAccounts.isEmpty()) {
            return new StatusResult(0L, true);
        }

        List<CompletableFuture<AccountBalanceAudit>> futures = financialAccounts.stream()
                .map(account -> CompletableFuture.supplyAsync(() -> {
                    try {
                        restClientAdapter.applyThrottlingDelay();
                        BigDecimal balanceDecimal = restClientAdapter.fetchAccountCurrentBalance(accessToken, account.accountId());
                        long accountBalanceCents = summaryCalculator.toCents(balanceDecimal);
                        
                        // Regra de negÃƒÆ’Ã‚Â³cio: saldo negativo nÃƒÆ’Ã‚Â£o compÃƒÆ’Ã‚Âµe balanceCents consolidado por padrÃƒÆ’Ã‚Â£o.
                        boolean includeInBalance = summaryCalculator.shouldIncludeAccountInBalance(account.type(), account.active(), accountBalanceCents);

                        // Auditoria fixa para identificar rapidamente contas que distorcem o saldo.
                        log.info(
                                "CONTA DETECTADA: Nome={}, Tipo={}, Saldo={}, Ativa={}",
                                account.name(),
                                account.type(),
                                accountBalanceCents,
                                account.active());

                        if (account.active() && summaryCalculator.isLiabilityAccountType(account.type())) {
                            // Regra de negÃƒÆ’Ã‚Â³cio: cartÃƒÆ’Ã‚Â£o de crÃƒÆ’Ã‚Â©dito representa passivo e nÃƒÆ’Ã‚Â£o compÃƒÆ’Ã‚Âµe saldo consolidado de caixa.
                            log.info("Conta {} ignorada no consolidado por ser passivo (tipo {}).", account.name(), account.type());
                        }

                        if (!account.active()) {
                            log.info("Conta {} ignorada no consolidado por estar inativa.", account.name());
                        }

                        if (account.active() && !summaryCalculator.isAssetAccountType(account.type()) && !summaryCalculator.isLiabilityAccountType(account.type())) {
                            log.info("Conta {} ignorada por tipo fora da whitelist de ativos: {}.", account.name(), account.type());
                        }

                        if (account.active() && summaryCalculator.isAssetAccountType(account.type()) && accountBalanceCents < 0) {
                            log.info("Conta {} ignorada por saldo negativo.", account.name());
                        }

                        return new AccountBalanceAudit(account, accountBalanceCents, includeInBalance);
                    } catch (Exception ex) {
                        // Falha individual nÃƒÆ’Ã‚Â£o pode derrubar o dashboard.
                        log.warn("Falha ao consultar saldo atual da conta financeira {}.", account.accountId(), ex);
                        return new AccountBalanceAudit(account, 0L, false);
                    }
                }, executor))
                .toList();

        List<AccountBalanceAudit> audits = futures.stream()
                .map(CompletableFuture::join)
                .toList();

        long totalBalance = audits.stream()
                .filter(AccountBalanceAudit::includedInBalance)
                .mapToLong(AccountBalanceAudit::balanceCents)
                .sum();

        return new StatusResult(totalBalance, true);
    }

    private StatusResult fetchTotalPaidByBaixas(
            String accessToken,
            List<ReceivableParcelRef> parcels,
            ExecutorService executor) {
        if (parcels == null || parcels.isEmpty()) {
            return new StatusResult(0L, true);
        }

        List<CompletableFuture<Long>> futures = parcels.stream()
                .map(parcel -> CompletableFuture.supplyAsync(() -> {
                    try {
                        restClientAdapter.applyThrottlingDelay();
                        List<BigDecimal> baixas = restClientAdapter.fetchParcelaBaixasValorLiquido(accessToken, parcel.parcelaId());
                        
                        BigDecimal totalLiquido = BigDecimal.ZERO;
                        for (BigDecimal baixaValorLiquido : baixas) {
                            String vendaId = org.springframework.util.StringUtils.hasText(parcel.displayIdentifier()) ? parcel.displayIdentifier() : parcel.parcelaId();
                            log.info("Venda {} - Somando baixa de valor: {}", vendaId, baixaValorLiquido);
                            log.info("Somando Baixa: Valor LÃƒÆ’Ã‚Â­quido detectado = {}", baixaValorLiquido);
                            totalLiquido = totalLiquido.add(baixaValorLiquido);
                        }

                        return summaryCalculator.toCents(totalLiquido);
                    } catch (Exception ex) {
                        // Falha individual de parcela nÃƒÆ’Ã‚Â£o deve zerar nem indisponibilizar o resumo inteiro.
                        String displayId = org.springframework.util.StringUtils.hasText(parcel.displayIdentifier())
                                ? parcel.displayIdentifier()
                                : parcel.parcelaId();
                        log.warn("Falha ao consultar baixas da parcela {}.", displayId, ex);
                        return 0L;
                    }
                }, executor))
                .toList();

        long totalPaidCents = futures.stream()
                .mapToLong(CompletableFuture::join)
                .sum();

        log.info("Soma total de baixas calculada: {}", totalPaidCents);

        return new StatusResult(totalPaidCents, true);
    }

    private ReceivedParcelsResult fetchReceivedParcels(String accessToken) {
        Map<String, ReceivableParcelRef> parcelMap = new java.util.LinkedHashMap<>();
        OffsetDateTime latestUpdate = null;

        // ProduÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Â£o: mantÃƒÆ’Ã‚Â©m a busca do primeiro dia do mÃƒÆ’Ã‚Âªs atual atÃƒÆ’Ã‚Â© hoje.
        LocalDate hoje = LocalDate.now();
        LocalDate inicioMesAtual = hoje.withDayOfMonth(1);
        log.info("Consultando parcelas RECEBIDO no perÃƒÆ’Ã‚Â­odo {} atÃƒÆ’Ã‚Â© {}.", inicioMesAtual, hoje);

        latestUpdate = collectReceivedParcelsByStatusAndRange(
            accessToken,
            ContaAzulStatus.RECEBIDO,
            inicioMesAtual,
            hoje,
            parcelMap,
            latestUpdate);

        if (parcelMap.isEmpty()) {
            // DiagnÃƒÆ’Ã‚Â³stico: forÃƒÆ’Ã‚Â§a janela maior para validar se o cÃƒÆ’Ã‚Â¡lculo estÃƒÆ’Ã‚Â¡ correto fora do mÃƒÆ’Ã‚Âªs corrente.
            log.warn("Nenhuma parcela RECEBIDO encontrada no mÃƒÆ’Ã‚Âªs atual. Reexecutando diagnÃƒÆ’Ã‚Â³stico com data_pagamento_de={}.", DIAGNOSTIC_PAYMENT_DATE_FROM);
            latestUpdate = collectReceivedParcelsByStatusAndRange(
                accessToken,
                ContaAzulStatus.RECEBIDO,
                DIAGNOSTIC_PAYMENT_DATE_FROM,
                hoje,
                parcelMap,
                latestUpdate);
        }

        if (parcelMap.isEmpty()) {
            // Compatibilidade: algumas versÃƒÆ’Ã‚Âµes da API V2 usam QUITADO no lugar de RECEBIDO.
            log.warn("Nenhuma parcela com status RECEBIDO. Tentando fallback com status QUITADO no mÃƒÆ’Ã‚Âªs atual.");
            latestUpdate = collectReceivedParcelsByStatusAndRange(
                accessToken,
                ContaAzulStatus.QUITADO,
                inicioMesAtual,
                hoje,
                parcelMap,
                latestUpdate);
        }

        if (parcelMap.isEmpty()) {
            log.warn("Nenhuma parcela QUITADO no mÃƒÆ’Ã‚Âªs atual. Reexecutando QUITADO com data_pagamento_de={}.", DIAGNOSTIC_PAYMENT_DATE_FROM);
            latestUpdate = collectReceivedParcelsByStatusAndRange(
                accessToken,
                ContaAzulStatus.QUITADO,
                DIAGNOSTIC_PAYMENT_DATE_FROM,
                hoje,
                parcelMap,
                latestUpdate);
        }

        return new ReceivedParcelsResult(
            new ArrayList<>(parcelMap.values()),
            true,
            latestUpdate != null ? latestUpdate.toString() : null);
    }

    private OffsetDateTime collectReceivedParcelsByStatusAndRange(
            String accessToken,
            String status,
            LocalDate dataPagamentoDe,
            LocalDate dataPagamentoAte,
            Map<String, ReceivableParcelRef> parcelMap,
            OffsetDateTime latestUpdate) {
        for (int page = 1; page <= SUMMARY_MAX_PAGES; page++) {
            ReceivablesPageData pageData = restClientAdapter.fetchReceivablesPageByPaymentDate(
                accessToken,
                status,
                page,
                dataPagamentoDe,
                dataPagamentoAte);

            if (pageData.latestUpdate() != null
                    && (latestUpdate == null || pageData.latestUpdate().toInstant().isAfter(latestUpdate.toInstant()))) {
                latestUpdate = pageData.latestUpdate();
            }

            if (pageData.parcels().isEmpty()) {
                break;
            }

            for (ReceivableParcelRef currentParcel : pageData.parcels()) {
                parcelMap.merge(
                        currentParcel.parcelaId(),
                        currentParcel,
                    (existing, incoming) -> org.springframework.util.StringUtils.hasText(existing.displayIdentifier()) ? existing : incoming);
            }

            if (pageData.parcels().size() < SUMMARY_PAGE_SIZE) {
                break;
            }
        }

        return latestUpdate;
    }

    /**
     * Resumo financeiro retornado pela API.
     *
     * Campos em centavos para evitar problemas de ponto flutuante em somas e comparaÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Âµes.
     */
    public record FinancialSummary(
        long balanceCents,
        long totalPendingCents,
        long totalPaidCents,
        String currency,
        long syncedReceiptsCount,
        boolean externalServiceAvailable,
        String lastUpdatedAt) implements Serializable {

        private static final long serialVersionUID = 1L;
    }
}


