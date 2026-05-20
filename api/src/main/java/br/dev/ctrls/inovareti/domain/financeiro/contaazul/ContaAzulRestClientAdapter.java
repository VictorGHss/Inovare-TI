package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.dev.ctrls.inovareti.modules.finance.infrastructure.config.ContaAzulProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Adaptador de saída (Outbound Adapter) responsável pela comunicação direta com a API da Conta Azul.
 * Trata o tratamento de tokens, renovação (OAuth) e parsing dos payloads JSON brutos retornando DTOs/records limpos.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContaAzulRestClientAdapter {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final ZoneOffset BRASILIA_OFFSET = ZoneOffset.ofHours(-3);
    private static final int SUMMARY_PAGE_SIZE = 100;
    
    private static final LocalDate RECEIVED_DUE_DATE_FROM = LocalDate.of(2000, 1, 1);
    private static final LocalDate RECEIVED_DUE_DATE_TO = LocalDate.of(2099, 12, 31);

    private final ContaAzulTokenService contaAzulTokenService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final JsonSafeReader jsonSafeReader;
    private final ContaAzulProperties properties;

    /**
     * Recupera as contas financeiras registradas na Conta Azul.
     */
    public List<FinancialAccountRef> fetchFinancialAccounts(String accessToken) {
        String uri = normalizeContaAzulBaseUrl() + "/v1/conta-financeira";
        ResponseEntity<String> response = executeGetWithRefresh(uri, accessToken);

        if (response.getBody() == null || response.getBody().isBlank()) {
            return List.of();
        }

        try {
            JsonNode root = objectMapper.readTree(response.getBody().getBytes(StandardCharsets.UTF_8));
            JsonNode entries = jsonSafeReader.resolveArrayNode(root);

            List<FinancialAccountRef> accounts = new ArrayList<>();
            for (JsonNode accountNode : entries) {
                String accountId = jsonSafeReader.readText(accountNode, "id", "conta_financeira_id", "contaFinanceiraId");
                if (!StringUtils.hasText(accountId)) {
                    continue;
                }

                String accountName = jsonSafeReader.readText(accountNode, "nome", "name", "descricao", "description");
                boolean active = readBooleanFromPaths(accountNode, "ativo", "active", "is_active", "isActive");
                String accountType = normalizeAccountType(jsonSafeReader.readText(accountNode, "tipo", "type", "categoria"));
                accounts.add(new FinancialAccountRef(
                        accountId.trim(),
                        StringUtils.hasText(accountName) ? accountName.trim() : accountId.trim(),
                        accountType,
                        active));
            }

            return accounts;
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Falha ao interpretar lista de contas financeiras da Conta Azul.", ex);
        }
    }

    /**
     * Recupera o saldo atual bruto (BigDecimal) de uma determinada conta.
     */
    public BigDecimal fetchAccountCurrentBalance(String accessToken, String accountId) {
        String uri = normalizeContaAzulBaseUrl() + "/v1/conta-financeira/" + accountId + "/saldo-atual";
        ResponseEntity<String> response = executeGetWithRefresh(uri, accessToken);

        if (response.getBody() == null || response.getBody().isBlank()) {
            return BigDecimal.ZERO;
        }

        try {
            JsonNode root = objectMapper.readTree(response.getBody().getBytes(StandardCharsets.UTF_8));
            BigDecimal saldoAtual = readDecimalFromPaths(
                    root,
                    "saldo_atual",
                    "saldo_atual.valor",
                    "data.saldo_atual",
                    "data.saldo_atual.valor");

            return saldoAtual != null ? saldoAtual : BigDecimal.ZERO;
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Falha ao interpretar saldo da conta financeira " + accountId + ".", ex);
        }
    }

    /**
     * Recupera a lista de valores líquidos (BigDecimal) das baixas de uma parcela.
     */
    public List<BigDecimal> fetchParcelaBaixasValorLiquido(String accessToken, String parcelaId) {
        String uri = normalizeContaAzulBaseUrl() + "/v1/financeiro/eventos-financeiros/parcelas/" + parcelaId;
        ResponseEntity<String> response = executeGetWithRefresh(uri, accessToken);

        if (response.getBody() == null || response.getBody().isBlank()) {
            return List.of();
        }

        try {
            JsonNode root = objectMapper.readTree(response.getBody().getBytes(StandardCharsets.UTF_8));
            JsonNode baixasNode = jsonSafeReader.readArrayNode(
                    root,
                    "baixas",
                    "evento.baixas",
                    "evento_financeiro.baixas",
                    "data.baixas",
                    "content.baixas");

            if (baixasNode == null || !baixasNode.isArray() || baixasNode.isEmpty()) {
                return List.of();
            }

            List<BigDecimal> valoresLiquidos = new ArrayList<>();
            for (JsonNode baixaNode : baixasNode) {
                BigDecimal baixaValorLiquido = readDecimalFromPaths(
                        baixaNode,
                        "composicao_valor.valor_liquido",
                        "composicaoValor.valorLiquido",
                        "valor_composicao.valor_liquido",
                        "valorComposicao.valorLiquido",
                        "valor_liquido",
                        "valorLiquido");

                if (baixaValorLiquido != null) {
                    valoresLiquidos.add(baixaValorLiquido);
                }
            }

            return valoresLiquidos;
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Falha ao interpretar detalhe de baixas da parcela " + parcelaId + ".", ex);
        }
    }

    /**
     * Busca uma página de parcelas a receber filtrando por data de vencimento.
     */
    public ReceivablesPageData fetchReceivablesPageByDueDate(
            String accessToken,
            String status,
            int page,
            LocalDate dataVencimentoDe,
            LocalDate dataVencimentoAte) {
        ResponseEntity<String> response = executePaymentsRequest(status, accessToken, page, dataVencimentoDe, dataVencimentoAte);
        return parseReceivablesPage(response.getBody());
    }

    /**
     * Busca uma página de parcelas a receber filtrando por data de pagamento (caixa).
     */
    public ReceivablesPageData fetchReceivablesPageByPaymentDate(
            String accessToken,
            String status,
            int page,
            LocalDate dataPagamentoDe,
            LocalDate dataPagamentoAte) {
        ResponseEntity<String> response = executePaymentsRequestByPaymentDate(status, accessToken, page, dataPagamentoDe, dataPagamentoAte);
        return parseReceivablesPage(response.getBody());
    }

    /**
     * Recupera o total agregado de pagamentos direto pelo status informado para a página 1 (totais gerais do mês).
     */
    public BigDecimal fetchTotalAmountByStatus(String accessToken, String status) {
        try {
            ResponseEntity<String> response = executePaymentsRequestByStatus(status, accessToken, 1);
            return extractTotalDecimal(response.getBody(), status);
        } catch (Unauthorized ex) {
            String errorBody = ex.getResponseBodyAsString();
            log.warn("Token expirado ou inválido ao buscar pagamentos com status='{}'. Tentando refresh automático. Resposta: {}", status, errorBody);

            try {
                String newToken = contaAzulTokenService.forceRefresh();
                ResponseEntity<String> retryResponse = executePaymentsRequestByStatus(status, newToken, 1);
                return extractTotalDecimal(retryResponse.getBody(), status);
            } catch (Exception refreshEx) {
                log.error("Refresh também falhou. Re-autorização manual necessária.", refreshEx);
                throw new ContaAzulAuthException("Token inválido e refresh falhou. Refaça o login na Conta Azul.", refreshEx);
            }
        } catch (HttpClientErrorException ex) {
            String errorBody = ex.getResponseBodyAsString();

            if (ex.getStatusCode().value() == 403) {
                log.warn("ContaAzul API retornou 403 FORBIDDEN ao buscar pagamentos com status='{}'. Resposta: {}", status, errorBody);
                return null; // Retorna nulo para indicar 403 / indisponível de forma segura
            }

            if (ex.getStatusCode().value() == 401) {
                log.error("ContaAzul API retornou 401 (Unauthorized) ao buscar pagamentos com status='{}'. Resposta da API: {}", status, errorBody, ex);
            } else {
                log.error("ContaAzul API retornou erro {} ao buscar pagamentos com status='{}'. Resposta: {}", ex.getStatusCode(), status, errorBody, ex);
            }

            throw new IllegalStateException("Falha ao recuperar resumo financeiro da Conta Azul [status=" + status + ", http=" + ex.getStatusCode() + "].", ex);
        }
    }

    private BigDecimal extractTotalDecimal(String jsonPayload, String status) {
        if (jsonPayload == null || jsonPayload.isBlank()) {
            return BigDecimal.ZERO;
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

            return readDecimalFromPaths(root, totalPath);
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Falha ao calcular resumo financeiro da Conta Azul.", ex);
        }
    }

    private ReceivablesPageData parseReceivablesPage(String jsonPayload) {
        if (!StringUtils.hasText(jsonPayload)) {
            return new ReceivablesPageData(List.of(), null);
        }

        try {
            JsonNode root = objectMapper.readTree(jsonPayload.getBytes(StandardCharsets.UTF_8));
            JsonNode entries = jsonSafeReader.resolveArrayNode(root);

            List<ReceivableParcelRef> parcels = new ArrayList<>();
            OffsetDateTime latestUpdate = parseApiDateToBrasiliaOffsetDateTime(jsonSafeReader.readText(
                    root,
                    "atualizado_em",
                    "updated_at",
                    "ultima_atualizacao",
                    "data_atualizacao"));

            for (JsonNode parcelNode : entries) {
                String parcelaId = jsonSafeReader.readText(parcelNode, "parcela_id", "id", "parcela.id");
                if (!StringUtils.hasText(parcelaId)) {
                    continue;
                }

                String commercialNumber = jsonSafeReader.readText(
                        parcelNode,
                        "numero",
                        "numero_venda",
                        "venda.numero",
                        "evento_financeiro.referencia.numero",
                        "referencia.numero");

                String referenceCode = jsonSafeReader.readText(
                    parcelNode,
                    "codigo_referencia",
                    "codigoReferencia",
                    "referencia.codigo",
                    "evento_financeiro.referencia.codigo");

                String displayIdentifier = firstNonBlank(commercialNumber, referenceCode);
                String descricaoParcela = firstNonBlank(
                    jsonSafeReader.readText(
                        parcelNode,
                        "descricao",
                        "description",
                        "referencia.descricao",
                        "evento_financeiro.referencia.descricao"),
                    displayIdentifier,
                    parcelaId);
                BigDecimal valorBruto = readDecimalFromPaths(
                    parcelNode,
                    "total",
                    "valor",
                    "valor_total",
                    "valorTotal",
                    "valor_bruto",
                    "valorBruto");

                log.info("Parcela encontrada: ID={}, Descrição={}, Valor Bruto={}", parcelaId, descricaoParcela, valorBruto);

                parcels.add(new ReceivableParcelRef(parcelaId.trim(), displayIdentifier));

                OffsetDateTime parcelUpdatedAt = parseApiDateToBrasiliaOffsetDateTime(jsonSafeReader.readText(
                        parcelNode,
                        "data_alteracao",
                        "dataAlteracao",
                        "updated_at",
                        "atualizado_em",
                        "evento.data_alteracao",
                        "evento_financeiro.data_alteracao"));

                if (parcelUpdatedAt != null
                        && (latestUpdate == null || parcelUpdatedAt.toInstant().isAfter(latestUpdate.toInstant()))) {
                    latestUpdate = parcelUpdatedAt;
                }
            }

            return new ReceivablesPageData(parcels, latestUpdate);
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Falha ao interpretar payload de parcelas recebidas da Conta Azul.", ex);
        }
    }

    public OffsetDateTime parseApiDateToBrasiliaOffsetDateTime(String rawDate) {
        if (!StringUtils.hasText(rawDate)) {
            return null;
        }

        String trimmed = rawDate.trim();

        try {
            if (trimmed.endsWith("Z") || trimmed.endsWith("z")) {
                return Instant.parse(trimmed).atOffset(BRASILIA_OFFSET);
            }
        } catch (DateTimeParseException ignored) {
        }

        try {
            return OffsetDateTime.parse(trimmed).withOffsetSameInstant(BRASILIA_OFFSET);
        } catch (DateTimeParseException ignored) {
        }

        try {
            LocalDateTime localDateTime = LocalDateTime.parse(trimmed);
            return localDateTime.atOffset(BRASILIA_OFFSET);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private ResponseEntity<String> executeGetWithRefresh(String uri, String accessToken) {
        try {
            return executeGetRequest(uri, accessToken);
        } catch (Unauthorized ex) {
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

    private ResponseEntity<String> executePaymentsRequest(String status, String accessToken, int page, LocalDate from, LocalDate to) {
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
        return responseEntity;
    }

    private ResponseEntity<String> executePaymentsRequestByStatus(String status, String accessToken, int page) {
        if (ContaAzulStatus.RECEBIDO.equals(status) || ContaAzulStatus.QUITADO.equals(status)) {
            LocalDate hoje = LocalDate.now();
            LocalDate inicioMesAtual = hoje.withDayOfMonth(1);
            return executePaymentsRequestByPaymentDate(status, accessToken, page, inicioMesAtual, hoje);
        }

        LocalDate hoje = LocalDate.now();
        LocalDate inicioMesAtual = hoje.withDayOfMonth(1);
        return executePaymentsRequest(status, accessToken, page, inicioMesAtual, hoje);
    }

    private ResponseEntity<String> executePaymentsRequestByPaymentDate(
            String status,
            String accessToken,
            int page,
            LocalDate dataPagamentoDeDate,
            LocalDate dataPagamentoAteDate) {
        String dataVencimentoDe = formatDateForContaAzul(RECEIVED_DUE_DATE_FROM);
        String dataVencimentoAte = formatDateForContaAzul(RECEIVED_DUE_DATE_TO);
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
        return responseEntity;
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

    public void applyThrottlingDelay() {
        try {
            Thread.sleep(300L);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean readBooleanFromPaths(JsonNode node, String... paths) {
        for (String path : paths) {
            JsonNode valueNode = readNode(node, path);
            if (valueNode == null || valueNode.isNull()) {
                continue;
            }

            if (valueNode.isBoolean()) {
                return valueNode.booleanValue();
            }

            if (valueNode.isTextual()) {
                String raw = valueNode.asText().trim();
                if ("true".equalsIgnoreCase(raw)) {
                    return true;
                }
                if ("false".equalsIgnoreCase(raw)) {
                    return false;
                }
            }
        }
        return false;
    }

    private JsonNode readNode(JsonNode node, String path) {
        JsonNode current = node;
        for (String segment : path.split("\\.")) {
            if (current == null) {
                return null;
            }
            current = current.get(segment);
        }
        return current;
    }

    private BigDecimal readDecimalFromPaths(JsonNode node, String... paths) {
        for (String path : paths) {
            JsonNode valueNode = readNode(node, path);
            if (valueNode == null || valueNode.isNull()) {
                continue;
            }

            BigDecimal parsed = parseDecimalValue(valueNode);
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    private BigDecimal parseDecimalValue(JsonNode valueNode) {
        if (valueNode.isNumber()) {
            return valueNode.decimalValue();
        }

        if (!valueNode.isTextual()) {
            return null;
        }

        String raw = valueNode.asText().trim();
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

    private String normalizeAccountType(String rawType) {
        if (!StringUtils.hasText(rawType)) {
            return "";
        }

        return rawType.trim().toUpperCase()
                .replace('-', '_')
                .replace(' ', '_');
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }
}
