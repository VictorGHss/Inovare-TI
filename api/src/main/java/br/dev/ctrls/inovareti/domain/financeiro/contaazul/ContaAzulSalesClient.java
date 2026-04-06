package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
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

    @Value("${app.contaazul.payments-url}")
    private String receivableEventsSearchUrl;

    @Value("${app.contaazul.sales-pdf-v1-url-template:https://api-v2.contaazul.com/v1/venda/{id}/imprimir}")
    private String salePdfV1UrlTemplate;

    @Value("${app.contaazul.sales-v2-url:https://api-v2.contaazul.com/v1/venda/busca}")
    private String salesV2Url;

    @Value("${app.contaazul.sales-v2-stable-url:https://api.contaazul.com/v2/sales}")
    private String salesV2StableUrl;

    /**
     * Indica se existe configuração para operações de venda.
     */
    public boolean hasSalesConfiguration() {
        return StringUtils.hasText(salePdfV1UrlTemplate);
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

        String primaryUri = UriComponentsBuilder.fromUriString(normalizeSaleSearchBaseUrl(salesV2Url))
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

            String stableUri = UriComponentsBuilder.fromUriString(salesV2StableUrl)
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
        if (!StringUtils.hasText(receivableEventsSearchUrl)) {
            return "https://api-v2.contaazul.com/v1/financeiro/eventos-financeiros/contas-a-receber/buscar";
        }
        return receivableEventsSearchUrl.trim();
    }

    private String normalizeSaleSearchBaseUrl(String rawUrl) {
        if (!StringUtils.hasText(rawUrl)) {
            return "https://api-v2.contaazul.com/v1/venda/busca";
        }

        String normalized = rawUrl.trim();
        if (normalized.contains("/v1/sales")) {
            return normalized.replace("/v1/sales", "/v1/venda/busca");
        }

        return normalized;
    }
}
