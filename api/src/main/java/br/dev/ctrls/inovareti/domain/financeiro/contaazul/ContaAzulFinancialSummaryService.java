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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.dev.ctrls.inovareti.domain.financeiro.ProcessedSaleRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço que recupera um resumo financeiro mensal a partir da API da Conta Azul.
 *
 * O resumo agrega valores pagos e em aberto no mês atual, converte para centavos
 * e fornece um contador de recibos já sincronizados localmente. O serviço trata
 * automaticamente refresh de token quando necessário e normaliza formatos numéricos
 * retornados pela API.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContaAzulFinancialSummaryService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private final ContaAzulTokenService contaAzulTokenService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ProcessedSaleRepository processedSaleRepository;

    @Value("${app.contaazul.payments-url}")
    private String paymentsUrl;

    @Value("${app.contaazul.api-v2-base-url:https://api-v2.contaazul.com}")
    private String contaAzulApiV2BaseUrl;

    @PostConstruct
    public void logV2BaseConfiguration() {
        log.info("ContaAzulFinancialSummaryService configurado com base URL v2: {}", contaAzulApiV2BaseUrl);
    }

    /**
     * Recupera o resumo financeiro do mês atual.
     *
     * - `balanceCents`: saldo (representado aqui como total pago em centavos);
     * - `totalPendingCents`: total em aberto em centavos;
     * - `totalPaidCents`: total pago em centavos;
     * - `syncedReceiptsCount`: quantidade de recibos já registrados localmente.
     */
    public FinancialSummary fetchSummary() {
        String accessToken = contaAzulTokenService.getValidAccessToken();

        long totalPaidCents = fetchTotalByStatus(accessToken, ContaAzulStatus.RECEBIDO);
        long totalPendingCents = fetchTotalByStatus(accessToken, ContaAzulStatus.EM_ABERTO);
        long balanceCents = totalPaidCents;
        long syncedReceiptsCount = processedSaleRepository.count();

        return new FinancialSummary(balanceCents, totalPendingCents, totalPaidCents, "BRL", syncedReceiptsCount);
    }

    // Recupera o total agregado (em centavos) para o `status` informado usando o accessToken.
    // Trata 401 autorizando refresh automático do token quando aplicável.
    private long fetchTotalByStatus(String accessToken, String status) {
        try {
            ResponseEntity<String> response = executePaymentsRequest(status, accessToken);

            return extractTotalCents(response.getBody(), status);
        } catch (Unauthorized ex) {
            String errorBody = ex.getResponseBodyAsString();
            log.warn(
                    "Token expirado ou inválido ao buscar pagamentos com status='{}'. Tentando refresh automático. Resposta: {}",
                    status,
                    errorBody);

            try {
                String newToken = contaAzulTokenService.forceRefresh();
                ResponseEntity<String> retryResponse = executePaymentsRequest(status, newToken);
                return extractTotalCents(retryResponse.getBody(), status);
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

    // Executa a requisição para o endpoint de pagamentos da Conta Azul com o status
    // e o token fornecidos. Retorna o corpo da resposta encapsulado em `ResponseEntity<String>`.
    private ResponseEntity<String> executePaymentsRequest(String status, String accessToken) {
        LocalDate hoje = LocalDate.now();
        LocalDate inicioMesAtual = hoje.withDayOfMonth(1);
        String dataVencimentoDe = inicioMesAtual.format(DATE_FORMATTER);
        String dataVencimentoAte = hoje.format(DATE_FORMATTER);

        log.debug(
            "Parâmetros ContaAzul (resumo mensal): vencimento_de={}, vencimento_ate={}, status={}",
            dataVencimentoDe,
            dataVencimentoAte,
            status);

        String uri = paymentsUrl
                + "?pagina=1"
                + "&tamanho_pagina=100"
                + "&data_vencimento_de=" + dataVencimentoDe
                + "&data_vencimento_ate=" + dataVencimentoAte
                + "&status=" + status;

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
        return responseEntity;
    }

    // Extrai do JSON retornado o total (campo em path configurado) e converte para centavos.
    private long extractTotalCents(String jsonPayload, String status) {
        if (jsonPayload == null || jsonPayload.isBlank()) {
            return 0L;
        }

        try {
            JsonNode root = objectMapper.readTree(jsonPayload.getBytes(StandardCharsets.UTF_8));

            String totalPath;
            if (null == status) {
                throw new IllegalStateException("Status não suportado para cálculo do resumo: " + status);
            } else switch (status) {
                case ContaAzulStatus.RECEBIDO -> totalPath = "totais.pago.valor";
                case ContaAzulStatus.EM_ABERTO -> totalPath = "totais.aberto.valor";
                default -> throw new IllegalStateException("Status não suportado para cálculo do resumo: " + status);
            }

            BigDecimal total = readDecimal(root, totalPath);
            return total != null ? toCents(total) : 0L;
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Falha ao calcular resumo financeiro da Conta Azul.", ex);
        }
    }

    // Lê um valor decimal a partir do `JsonNode` navegando pelo `path` (pontos como separador).
    // Suporta números e strings com formatação localizada (R$ 1.234,56).
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

    // Converte `BigDecimal` em centavos (long) arredondando HALF_UP.
    private long toCents(BigDecimal value) {
        return value
                .movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();
    }

        /**
         * Resumo financeiro retornado pela API.
         *
         * Campos em centavos para evitar problemas de ponto flutuante em somas e comparações.
         */
        public record FinancialSummary(
            long balanceCents,
            long totalPendingCents,
            long totalPaidCents,
            String currency,
            long syncedReceiptsCount) {
        }
}
