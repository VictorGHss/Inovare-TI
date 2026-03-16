package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpClientErrorException.Unauthorized;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContaAzulFinancialSummaryService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private final ContaAzulTokenService contaAzulTokenService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.contaazul.payments-url}")
    private String paymentsUrl;

    public FinancialSummary fetchSummary() {
        String accessToken = contaAzulTokenService.getValidAccessToken();

        long totalPaidCents = fetchTotalByStatus(accessToken, "PAGO");
        long totalPendingCents = fetchTotalByStatus(accessToken, "PENDENTE");
        long balanceCents = totalPaidCents - totalPendingCents;

        return new FinancialSummary(balanceCents, totalPendingCents, totalPaidCents, "BRL");
    }

    private long fetchTotalByStatus(String accessToken, String status) {
        boolean includePaymentWindow = "PAGO".equalsIgnoreCase(status);

        try {
            ResponseEntity<String> response = executePaymentsRequest(status, accessToken, includePaymentWindow);

            return sumAmountCents(response.getBody());
        } catch (Unauthorized ex) {
            String errorBody = ex.getResponseBodyAsString();
            log.warn(
                    "Token expirado ou inválido ao buscar pagamentos com status='{}'. Tentando refresh automático. Resposta: {}",
                    status,
                    errorBody);

            try {
                String newToken = contaAzulTokenService.forceRefresh();
                ResponseEntity<String> retryResponse = executePaymentsRequest(status, newToken, includePaymentWindow);
                return sumAmountCents(retryResponse.getBody());
            } catch (Exception refreshEx) {
                log.error("Refresh também falhou. Re-autorização manual necessária.", refreshEx);
                throw new ContaAzulAuthException(
                        "Token inválido e refresh falhou. Refaça o login na Conta Azul.",
                        refreshEx);
            }
        } catch (HttpClientErrorException ex) {
            String errorBody = ex.getResponseBodyAsString();
            
            if (ex.getStatusCode().value() == 401) {
                log.error(
                        "ContaAzul API retornou 401 (Unauthorized) ao buscar pagamentos com status='{}'. " +
                        "Possíveis causas: token expirado, token revogado ou app sem permissões configuradas no portal Conta Azul. " +
                        "Resposta da API: {}",
                        status, errorBody, ex);
            } else {
                log.error(
                        "ContaAzul API retornou erro {} ao buscar pagamentos com status='{}'. " +
                        "Resposta: {}",
                        ex.getStatusCode(), status, errorBody, ex);
            }
            
            throw new IllegalStateException(
                    "Falha ao recuperar resumo financeiro da Conta Azul [status=" + status + ", http=" + ex.getStatusCode() + "]. " +
                    "Verifique token OAuth e permissões do aplicativo no portal da Conta Azul.", ex);
        }
    }

    private ResponseEntity<String> executePaymentsRequest(String status, String accessToken, boolean includePaymentWindow) {
        LocalDate hoje = LocalDate.now();
        LocalDate janelaVencimentoDe = hoje.minusDays(90);
        LocalDate janelaVencimentoAte = hoje.plusDays(30);
        LocalDate dataPagamentoDe = includePaymentWindow ? hoje.minusDays(30) : null;
        LocalDate dataPagamentoAte = includePaymentWindow ? hoje : null;

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(paymentsUrl)
                .queryParam("data_vencimento_de", janelaVencimentoDe.format(DATE_FORMATTER))
                .queryParam("data_vencimento_ate", janelaVencimentoAte.format(DATE_FORMATTER))
                .queryParam("status", status)
                .queryParam("tamanho_pagina", 100)
                .queryParam("pagina", 1);

        if (includePaymentWindow) {
            uriBuilder
                .queryParam("data_pagamento_de", dataPagamentoDe.format(DATE_FORMATTER))
                .queryParam("data_pagamento_ate", dataPagamentoAte.format(DATE_FORMATTER));
        }

        log.debug(
            "Parâmetros ContaAzul: vencimento_de={}, vencimento_ate={}, pagamento_de={}, pagamento_ate={}, status={}",
            janelaVencimentoDe,
            janelaVencimentoAte,
            dataPagamentoDe,
            dataPagamentoAte,
            status);

        String uri = uriBuilder
                .build()
                .encode()
                .toUriString();

        log.debug("Chamando ContaAzul: {}", uri);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        return restTemplate.exchange(
                uri,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);
    }

    private long sumAmountCents(String jsonPayload) {
        if (jsonPayload == null || jsonPayload.isBlank()) {
            return 0L;
        }

        try {
            JsonNode root = objectMapper.readTree(jsonPayload.getBytes(StandardCharsets.UTF_8));
            JsonNode entries = resolveArrayNode(root);

            long total = 0L;
            for (JsonNode node : entries) {
                total += toCents(resolveAmount(node));
            }
            return total;
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Falha ao calcular resumo financeiro da Conta Azul.", ex);
        }
    }

    private JsonNode resolveArrayNode(JsonNode root) {
        if (root.isArray()) {
            return root;
        }

        if (root.has("data") && root.get("data").isArray()) {
            return root.get("data");
        }

        if (root.has("items") && root.get("items").isArray()) {
            return root.get("items");
        }

        return objectMapper.createArrayNode();
    }

    private BigDecimal resolveAmount(JsonNode node) {
        for (String path : List.of(
                "valor",
                "valor_total",
                "amount",
                "total",
                "parcela.valor",
                "parcela.valor_total",
                "payment.amount",
                "payment.total")) {
            BigDecimal value = readDecimal(node, path);
            if (value != null) {
                return value;
            }
        }

        return BigDecimal.ZERO;
    }

    private BigDecimal readDecimal(JsonNode node, String path) {
        JsonNode current = node;
        for (String segment : path.split("\\.")) {
            if (current == null) {
                return null;
            }
            current = current.get(segment);
        }

        if (current == null || current.isNull()) {
            return null;
        }

        if (current.isNumber()) {
            return current.decimalValue();
        }

        if (current.isTextual()) {
            String raw = current.asText().trim();
            if (raw.isBlank()) {
                return null;
            }

            String normalized = raw.replace("R$", "").replace(" ", "");
            if (normalized.contains(",") && normalized.contains(".")) {
                normalized = normalized.replace(".", "").replace(",", ".");
            } else if (normalized.contains(",")) {
                normalized = normalized.replace(",", ".");
            }

            try {
                return new BigDecimal(normalized);
            } catch (NumberFormatException ex) {
                return null;
            }
        }

        return null;
    }

    private long toCents(BigDecimal value) {
        return value
                .movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();
    }

    public record FinancialSummary(
            long balanceCents,
            long totalPendingCents,
            long totalPaidCents,
            String currency) {
    }
}
