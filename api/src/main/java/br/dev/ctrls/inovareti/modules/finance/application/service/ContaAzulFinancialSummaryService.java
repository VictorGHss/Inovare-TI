package br.dev.ctrls.inovareti.modules.finance.application.service;

import io.micrometer.observation.annotation.Observed;

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

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
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
 * Serviço que atua como orquestrador da camada de aplicação para recuperar o resumo financeiro mensal.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Observed
public class ContaAzulFinancialSummaryService {

    private final ContaAzulTokenService contaAzulTokenService;
    private final ProcessedSaleRepository processedSaleRepository;
    private final ContaAzulRestClientAdapter restClientAdapter;
    private final ContaAzulSummaryCalculator summaryCalculator;
    private final ContaAzulProperties properties;
    private final ContaAzulReceivablesFetcher contaAzulReceivablesFetcher;

    @PostConstruct
    public void logV2BaseConfiguration() {
        log.info("ContaAzulFinancialSummaryService configurado com base URL v2: {}", properties.getApiV2BaseUrl());
    }

    @Cacheable(value = "contaAzulSummary", key = "'dashboard'")
    public FinancialSummary fetchSummary() {
        long syncedReceiptsCount = processedSaleRepository.count();
        ExecutorService executor = buildSummaryExecutor();

        try {
            String accessToken = contaAzulTokenService.getValidAccessToken();

            CompletableFuture<ReceivedParcelsResult> receivedParcelsFuture = CompletableFuture.supplyAsync(
                    () -> safeFetchReceivedParcels(accessToken), executor);

            CompletableFuture<StatusResult> pendingFuture = CompletableFuture.supplyAsync(
                    () -> safeFetchTotalByStatus(accessToken, ContaAzulStatus.EM_ABERTO), executor);

            CompletableFuture<StatusResult> balanceFuture = CompletableFuture.supplyAsync(
                    () -> safeFetchConsolidatedBalance(accessToken, executor), executor);

            CompletableFuture.allOf(receivedParcelsFuture, pendingFuture, balanceFuture).join();

            ReceivedParcelsResult receivedParcelsResult = receivedParcelsFuture.join();
            StatusResult pendingResult = pendingFuture.join();
            StatusResult balanceResult = balanceFuture.join();

            log.info("Resumo financeiro: parcelas recebidas identificadas = {}", receivedParcelsResult.parcels().size());
            StatusResult paidResult = safeFetchTotalPaidByBaixas(accessToken, receivedParcelsResult.parcels(), executor);

            return new FinancialSummary(
                    balanceResult.total(),
                    pendingResult.total(),
                    paidResult.total(),
                    "BRL",
                    syncedReceiptsCount,
                    balanceResult.available(),
                    summaryCalculator.resolveSummaryLastUpdatedAt(receivedParcelsResult.lastUpdatedAt()));
        } catch (Exception ex) {
            log.warn("Falha ao montar resumo financeiro. Retornando fallback.", ex);
            return new FinancialSummary(0L, 0L, 0L, "BRL", syncedReceiptsCount, false,
                    summaryCalculator.resolveSummaryLastUpdatedAt(null));
        } finally {
            shutdownExecutor(executor);
        }
    }

    private ExecutorService buildSummaryExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    private void shutdownExecutor(ExecutorService executor) {
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

    private StatusResult safeFetchTotalByStatus(String accessToken, String status) {
        try {
            BigDecimal totalDecimal = restClientAdapter.fetchTotalAmountByStatus(accessToken, status);
            if (totalDecimal == null) {
                return new StatusResult(0L, false);
            }
            long totalCents = summaryCalculator.normalizeAmountToCents(totalDecimal, status.equals(ContaAzulStatus.RECEBIDO) ? "totais.pago.valor" : "totais.aberto.valor");
            return new StatusResult(totalCents, true);
        } catch (Exception ex) {
            log.warn("Falha ao consultar status '{}' na Conta Azul.", status, ex);
            return new StatusResult(0L, false);
        }
    }

    private ReceivedParcelsResult safeFetchReceivedParcels(String accessToken) {
        try {
            return contaAzulReceivablesFetcher.fetchReceivedParcels(accessToken);
        } catch (Exception ex) {
            log.warn("Falha ao consultar parcelas RECEBIDO para cálculo real por baixas.", ex);
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
            log.warn("Falha ao calcular total pago com base nas baixas.", ex);
            return new StatusResult(0L, false);
        }
    }

    private StatusResult fetchConsolidatedBalance(String accessToken, ExecutorService executor) {
        List<FinancialAccountRef> financialAccounts;
        try {
            financialAccounts = restClientAdapter.fetchFinancialAccounts(accessToken);
        } catch (Exception ex) {
            log.warn("Falha ao listar contas financeiras na Conta Azul.", ex);
            return new StatusResult(0L, false);
        }

        if (financialAccounts.isEmpty()) {
            return new StatusResult(0L, true);
        }

        List<CompletableFuture<AccountBalanceAudit>> futures = financialAccounts.stream()
                .map(account -> CompletableFuture.supplyAsync(() -> queryAccountBalance(accessToken, account), executor))
                .toList();

        List<AccountBalanceAudit> audits = futures.stream()
                .map(f -> f.join())
                .toList();

        long totalBalance = audits.stream()
                .filter(audit -> audit.includedInBalance())
                .mapToLong(audit -> audit.balanceCents())
                .sum();

        return new StatusResult(totalBalance, true);
    }

    private AccountBalanceAudit queryAccountBalance(String accessToken, FinancialAccountRef account) {
        try {
            restClientAdapter.applyThrottlingDelay();
            BigDecimal balanceDecimal = restClientAdapter.fetchAccountCurrentBalance(accessToken, account.accountId());
            long accountBalanceCents = summaryCalculator.toCents(balanceDecimal);
            boolean includeInBalance = summaryCalculator.shouldIncludeAccountInBalance(account.type(), account.active(), accountBalanceCents);

            log.info("CONTA DETECTADA: Nome={}, Tipo={}, Saldo={}, Ativa={}", account.name(), account.type(), accountBalanceCents, account.active());
            return new AccountBalanceAudit(account, accountBalanceCents, includeInBalance);
        } catch (Exception ex) {
            log.warn("Falha ao consultar saldo atual da conta financeira {}.", account.accountId(), ex);
            return new AccountBalanceAudit(account, 0L, false);
        }
    }

    private StatusResult fetchTotalPaidByBaixas(
            String accessToken,
            List<ReceivableParcelRef> parcels,
            ExecutorService executor) {
        if (parcels == null || parcels.isEmpty()) {
            return new StatusResult(0L, true);
        }

        List<CompletableFuture<Long>> futures = parcels.stream()
                .map(parcel -> CompletableFuture.supplyAsync(() -> queryParcelBaixas(accessToken, parcel), executor))
                .toList();

        long totalPaidCents = futures.stream()
                .mapToLong(f -> f.join())
                .sum();

        log.info("Soma total de baixas calculada: {}", totalPaidCents);
        return new StatusResult(totalPaidCents, true);
    }

    private Long queryParcelBaixas(String accessToken, ReceivableParcelRef parcel) {
        try {
            restClientAdapter.applyThrottlingDelay();
            List<BigDecimal> baixas = restClientAdapter.fetchParcelaBaixasValorLiquido(accessToken, parcel.parcelaId());
            BigDecimal totalLiquido = BigDecimal.ZERO;
            for (BigDecimal baixaValorLiquido : baixas) {
                totalLiquido = totalLiquido.add(baixaValorLiquido);
            }
            return summaryCalculator.toCents(totalLiquido);
        } catch (Exception ex) {
            String displayId = org.springframework.util.StringUtils.hasText(parcel.displayIdentifier()) ? parcel.displayIdentifier() : parcel.parcelaId();
            log.warn("Falha ao consultar baixas da parcela {}.", displayId, ex);
            return 0L;
        }
    }

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


