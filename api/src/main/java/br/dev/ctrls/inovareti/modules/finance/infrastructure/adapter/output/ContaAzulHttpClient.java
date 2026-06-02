package br.dev.ctrls.inovareti.modules.finance.infrastructure.adapter.output;

import br.dev.ctrls.inovareti.modules.finance.application.service.ContaAzulTokenService;
import br.dev.ctrls.inovareti.modules.finance.domain.model.ContaAzulStatus;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpClientErrorException.Unauthorized;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import br.dev.ctrls.inovareti.modules.finance.infrastructure.config.ContaAzulProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Cliente especialista responsável apenas pela comunicação física HTTP de baixo nível com a API da Conta Azul,
 * gerenciando injeção de Headers, renovação automática de tokens OAuth e normalizações de rotas.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContaAzulHttpClient {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final int SUMMARY_PAGE_SIZE = 100;



    private final ContaAzulTokenService contaAzulTokenService;
    private final RestTemplate restTemplate;
    private final ContaAzulProperties properties;

    /**
     * Recupera as contas financeiras registradas na Conta Azul (retorno bruto em JSON).
     */
    public String fetchFinancialAccountsRaw(String accessToken) {
        String uri = normalizeContaAzulBaseUrl() + "/v1/conta-financeira";
        ResponseEntity<String> response = executeGetWithRefresh(uri, accessToken);
        return response.getBody();
    }

    /**
     * Recupera o saldo atual de uma conta (retorno bruto em JSON).
     */
    public String fetchAccountCurrentBalanceRaw(String accessToken, String accountId) {
        String uri = normalizeContaAzulBaseUrl() + "/v1/conta-financeira/" + accountId + "/saldo-atual";
        ResponseEntity<String> response = executeGetWithRefresh(uri, accessToken);
        return response.getBody();
    }

    /**
     * Recupera as baixas de uma parcela (retorno bruto em JSON).
     */
    public String fetchParcelaBaixasValorLiquidoRaw(String accessToken, String parcelaId) {
        String uri = normalizeContaAzulBaseUrl() + "/v1/financeiro/eventos-financeiros/parcelas/" + parcelaId;
        ResponseEntity<String> response = executeGetWithRefresh(uri, accessToken);
        return response.getBody();
    }

    /**
     * Busca uma página de parcelas a receber por data de vencimento (retorno bruto em JSON).
     */
    public String executePaymentsRequestRaw(String status, String accessToken, int page, LocalDate from, LocalDate to) {
        String dataVencimentoDe = formatDateForContaAzul(from);
        String dataVencimentoAte = formatDateForContaAzul(to);

        log.debug("Parâmetros ContaAzul (resumo mensal): vencimento_de={}, vencimento_ate={}, status={}", dataVencimentoDe, dataVencimentoAte, status);

        String uri = UriComponentsBuilder.fromUriString(normalizePaymentsUrl())
            .queryParam("pagina", page)
            .queryParam("tamanho_pagina", SUMMARY_PAGE_SIZE)
            .queryParam("data_vencimento_de", dataVencimentoDe)
            .queryParam("data_vencimento_ate", dataVencimentoAte)
            .queryParam("status", status)
            .build()
            .toUriString();

        log.debug("Chamando ContaAzul: {}", uri);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        ResponseEntity<String> responseEntity = restTemplate.exchange(
                uri,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);

        log.debug("ContaAzul response body (resumo, status={}): {}", status, responseEntity.getBody());
        return responseEntity.getBody();
    }

    /**
     * Busca uma página de parcelas a receber por data de pagamento (retorno bruto em JSON).
     */
    public String executePaymentsRequestByPaymentDateRaw(
            String status,
            String accessToken,
            int page,
            LocalDate dataPagamentoDeDate,
            LocalDate dataPagamentoAteDate) {
        LocalDate receivedDueDateFrom = LocalDate.now().withDayOfYear(1);
        LocalDate receivedDueDateTo = LocalDate.now().withMonth(12).withDayOfMonth(31);
        String dataVencimentoDe = formatDateForContaAzul(receivedDueDateFrom);
        String dataVencimentoAte = formatDateForContaAzul(receivedDueDateTo);
        String dataPagamentoDe = formatDateForContaAzul(dataPagamentoDeDate);
        String dataPagamentoAte = formatDateForContaAzul(dataPagamentoAteDate);

        log.debug("Parâmetros ContaAzul (resumo mensal): data_vencimento_de={}, data_vencimento_ate={}, data_pagamento_de={}, data_pagamento_ate={}, status={}",
            dataVencimentoDe, dataVencimentoAte, dataPagamentoDe, dataPagamentoAte, status);

        String uri = UriComponentsBuilder.fromUriString(normalizePaymentsUrl())
            .queryParam("pagina", page)
            .queryParam("tamanho_pagina", SUMMARY_PAGE_SIZE)
            .queryParam("data_vencimento_de", dataVencimentoDe)
            .queryParam("data_vencimento_ate", dataVencimentoAte)
            .queryParam("data_pagamento_de", dataPagamentoDe)
            .queryParam("data_pagamento_ate", dataPagamentoAte)
            .queryParam("status", status)
            .build()
            .toUriString();

        log.debug("Chamando ContaAzul (recebidos por pagamento): {}", uri);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        ResponseEntity<String> responseEntity;
        try {
            responseEntity = restTemplate.exchange(
                uri,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);
        } catch (HttpClientErrorException ex) {
            log.error("Erro ao buscar recebidos na ContaAzul [httpStatus={}, status={}, pagina={}, data_vencimento_de={}, data_vencimento_ate={}, data_pagamento_de={}, data_pagamento_ate={}, uri={}, body={}]",
                ex.getStatusCode(), status, page, dataVencimentoDe, dataVencimentoAte, dataPagamentoDe, dataPagamentoAte, uri, ex.getResponseBodyAsString(), ex);
            throw ex;
        }

        log.debug("ContaAzul response body (pagamento, status={}): {}", status, responseEntity.getBody());
        return responseEntity.getBody();
    }

    /**
     * Busca parcelas a receber por status na página 1 (retorno bruto em JSON).
     */
    public String executePaymentsRequestByStatusRaw(String status, String accessToken, int page) {
        if (ContaAzulStatus.RECEBIDO.equals(status) || ContaAzulStatus.QUITADO.equals(status)) {
            LocalDate hoje = LocalDate.now();
            LocalDate inicioMesAtual = hoje.withDayOfMonth(1);
            return executePaymentsRequestByPaymentDateRaw(status, accessToken, page, inicioMesAtual, hoje);
        }

        LocalDate hoje = LocalDate.now();
        LocalDate inicioMesAtual = hoje.withDayOfMonth(1);
        return executePaymentsRequestRaw(status, accessToken, page, inicioMesAtual, hoje);
    }

    private ResponseEntity<String> executeGetWithRefresh(String uri, String accessToken) {
        try {
            return executeGetRequest(uri, accessToken);
        } catch (Unauthorized ex) {
            log.warn("Token expirado ao chamar URI {}. Solicitando renovação automática de OAuth.", uri);
            String refreshedToken = contaAzulTokenService.forceRefresh();
            return executeGetRequest(uri, refreshedToken);
        }
    }

    private ResponseEntity<String> executeGetRequest(String uri, String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        return restTemplate.exchange(
                uri,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);
    }

    public String forceTokenRefresh() {
        return contaAzulTokenService.forceRefresh();
    }

    private String normalizeContaAzulBaseUrl() {
        String normalized = StringUtils.hasText(properties.getApiV2BaseUrl())
                ? properties.getApiV2BaseUrl().trim()
                : "https://api-v2.contaazul.com";

        normalized = normalized.replace("https://api.contaazul.com", "https://api-v2.contaazul.com");
        normalized = normalized.replaceAll("/+$", "");
        normalized = normalized.replaceAll("(?i)https://api-v2\\.contaazul\\.com/api$", "https://api-v2.contaazul.com");
        return normalized;
    }

    private String formatDateForContaAzul(LocalDate date) {
        return date.format(DATE_FORMATTER);
    }

    private String normalizePaymentsUrl() {
        String normalized = properties.getPaymentsUrl() != null ? properties.getPaymentsUrl().trim() : "";
        if (normalized.isBlank()) {
            return "https://api-v2.contaazul.com/v1/financeiro/eventos-financeiros/contas-a-receber/buscar";
        }

        normalized = normalized.replace("https://api.contaazul.com", "https://api-v2.contaazul.com");
        normalized = normalized.replaceAll("/+$", "");
        normalized = normalized.replaceAll("(?i)/api/v1/", "/v1/");
        normalized = normalized.replaceAll("(?i)https://api-v2\\.contaazul\\.com/api/", "https://api-v2.contaazul.com/");

        if (normalized.matches("(?i).*/v1/financeiro/contas-a-receber$")) {
            normalized = normalized.replaceAll("(?i)/v1/financeiro/contas-a-receber$", "/v1/financeiro/eventos-financeiros/contas-a-receber/buscar");
        } else if (normalized.matches("(?i).*/v1/financeiro/eventos-financeiros/contas-a-receber$")) {
            normalized = normalized + "/buscar";
        }

        return normalized;
    }
}

