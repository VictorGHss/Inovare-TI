package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import br.dev.ctrls.inovareti.modules.finance.infrastructure.config.ContaAzulProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Cliente especializado para operações de vendas na Conta Azul.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContaAzulSalesClient {

    private static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES = 30;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final ContaAzulRequestExecutor requestExecutor;
    private final SalesResponseMapper salesResponseMapper;
    private final ContaAzulProperties properties;









    /**
     * Indica se existe configuração para operações de venda.
     */
    public boolean hasSalesConfiguration() {
        return StringUtils.hasText(properties.getSalesPdfV1UrlTemplate());
    }

    /**
     * Consulta vendas liquidadas no período do mês atual.
     */
    public List<ContaAzulClient.SaleItem> fetchAcquittedSales() {
        LocalDate hoje = LocalDate.now();
        String dataVencimentoDe = hoje.withDayOfMonth(1).format(DATE_FORMATTER);
        String dataVencimentoAte = hoje.format(DATE_FORMATTER);
        return fetchAcquittedSales(dataVencimentoDe, dataVencimentoAte);
    }

    /**
     * Consulta vendas liquidadas no período informado.
     */
    public List<ContaAzulClient.SaleItem> fetchAcquittedSales(String dataVencimentoDe, String dataVencimentoAte) {
        List<ContaAzulClient.SaleItem> sales = new ArrayList<>();

        for (int page = 1; page <= MAX_PAGES; page++) {
            String uri = buildReceivableSearchUri(page, dataVencimentoDe, dataVencimentoAte);
            String payload = requestExecutor.executeJsonGetWithRefresh(uri);
            List<ContaAzulClient.SaleItem> pageItems = salesResponseMapper.parseAcquittedSales(payload);

            if (pageItems.isEmpty()) {
                break;
            }

            sales.addAll(pageItems);

            if (pageItems.size() < PAGE_SIZE) {
                break;
            }
        }

        return sales;
    }

    /**
     * Localiza uma venda liquidada específica por ID.
     */
    public Optional<ContaAzulClient.SaleItem> findAcquittedSaleById(String saleId) {
        if (!StringUtils.hasText(saleId)) {
            return Optional.empty();
        }

        String normalizedSaleId = saleId.trim();
        LocalDate hoje = LocalDate.now();
        String dataVencimentoDe = hoje.withDayOfMonth(1).format(DATE_FORMATTER);
        String dataVencimentoAte = hoje.format(DATE_FORMATTER);

        for (int page = 1; page <= MAX_PAGES; page++) {
            String uri = buildReceivableSearchUri(page, dataVencimentoDe, dataVencimentoAte);
            String payload = requestExecutor.executeJsonGetWithRefresh(uri);
            List<ContaAzulClient.SaleItem> pageItems = salesResponseMapper.parseAcquittedSales(payload);

            Optional<ContaAzulClient.SaleItem> match = pageItems.stream()
                    .filter(item -> normalizedSaleId.equals(item.saleId()))
                    .findFirst();

            if (match.isPresent()) {
                return match;
            }

            if (pageItems.isEmpty() || pageItems.size() < PAGE_SIZE) {
                break;
            }
        }

        return Optional.empty();
    }

    /**
     * Recupera vendas committed com heurística para parcelas liquidadas.
     */
    public List<ContaAzulClient.SaleItem> fetchCommittedSalesWithAcquittedParcels() {
        List<ContaAzulClient.SaleItem> sales = new ArrayList<>();

        for (int page = 1; page <= MAX_PAGES; page++) {
            String payload = fetchCommittedSalesPagePayload(page);
            List<ContaAzulClient.SaleItem> pageItems = salesResponseMapper.parseCommittedSalesWithAcquittedParcels(payload);

            if (pageItems.isEmpty()) {
                break;
            }

            sales.addAll(pageItems);

            if (pageItems.size() < PAGE_SIZE) {
                break;
            }
        }

        return sales;
    }

    private String fetchCommittedSalesPagePayload(int page) {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        String dataAlteracaoDe = LocalDateTime.of(yesterday, java.time.LocalTime.MIDNIGHT).format(DATE_TIME_FORMATTER);
        String dataAlteracaoAte = LocalDateTime.of(today, java.time.LocalTime.of(23, 59, 59)).format(DATE_TIME_FORMATTER);

        String primaryUri = UriComponentsBuilder.fromUriString(normalizeSaleSearchBaseUrl(properties.getSalesV2Url()))
                .queryParam("pagina", page)
                .queryParam("tamanho_pagina", PAGE_SIZE)
                .queryParam("data_alteracao_de", dataAlteracaoDe)
                .queryParam("data_alteracao_ate", dataAlteracaoAte)
                .build()
                .toUriString();

        try {
            return requestExecutor.executeJsonGetWithRefresh(primaryUri);
        } catch (ContaAzulHttpException ex) {
            if (!ex.isStatus(404)) {
                throw ex;
            }

            String stableUri = UriComponentsBuilder.fromUriString(properties.getSalesV2StableUrl())
                    .queryParam("pagina", page)
                    .queryParam("tamanho_pagina", PAGE_SIZE)
                    .queryParam("data_alteracao_de", dataAlteracaoDe)
                    .queryParam("data_alteracao_ate", dataAlteracaoAte)
                    .build()
                    .toUriString();

            log.warn("Endpoint de vendas não encontrado em {}. Tentando fallback estável em {}.", primaryUri, stableUri);
            return requestExecutor.executeJsonGetWithRefresh(stableUri);
        }
    }

    private String buildReceivableSearchUri(int page, String dataVencimentoDe, String dataVencimentoAte) {
        return UriComponentsBuilder.fromUriString(normalizeReceivablesBaseUrl())
                .queryParam("pagina", page)
                .queryParam("tamanho_pagina", PAGE_SIZE)
                .queryParam("status", "RECEBIDO")
                .queryParam("data_vencimento_de", dataVencimentoDe)
                .queryParam("data_vencimento_ate", dataVencimentoAte)
                .build()
                .toUriString();
    }

    private String normalizeReceivablesBaseUrl() {
        if (!StringUtils.hasText(properties.getPaymentsUrl())) {
            return "https://api-v2.contaazul.com/v1/financeiro/eventos-financeiros/contas-a-receber/buscar";
        }

        // Normaliza host/caminho para evitar regressão com endpoints legados contendo /api.
        String normalized = properties.getPaymentsUrl().trim();
        normalized = normalized.replace("https://api.contaazul.com", "https://api-v2.contaazul.com");
        normalized = normalized.replaceAll("/+$", "");
        normalized = normalized.replaceAll("(?i)/api/v1/", "/v1/");
        normalized = normalized.replaceAll("(?i)https://api-v2\\.contaazul\\.com/api/", "https://api-v2.contaazul.com/");

        // Garante endpoint V2 de busca com filtros, mesmo quando vier configuração antiga.
        if (normalized.matches("(?i).*/v1/financeiro/contas-a-receber$")) {
            normalized = normalized.replaceAll(
                    "(?i)/v1/financeiro/contas-a-receber$",
                    "/v1/financeiro/eventos-financeiros/contas-a-receber/buscar");
        } else if (normalized.matches("(?i).*/v1/financeiro/eventos-financeiros/contas-a-receber$")) {
            normalized = normalized + "/buscar";
        }

        return normalized;
    }

    private String normalizeSaleSearchBaseUrl(String rawUrl) {
        if (!StringUtils.hasText(rawUrl)) {
            return "https://api-v2.contaazul.com/v1/venda/busca";
        }

        String normalized = rawUrl.trim();
        // Força domínio oficial v2 e remove prefixo /api legado quando presente.
        normalized = normalized.replace("https://api.contaazul.com", "https://api-v2.contaazul.com");
        normalized = normalized.replaceAll("(?i)/api/v1/", "/v1/");
        normalized = normalized.replaceAll("(?i)https://api-v2\\.contaazul\\.com/api/", "https://api-v2.contaazul.com/");
        if (normalized.contains("/v1/sales")) {
            return normalized.replace("/v1/sales", "/v1/venda/busca");
        }

        return normalized;
    }
}
