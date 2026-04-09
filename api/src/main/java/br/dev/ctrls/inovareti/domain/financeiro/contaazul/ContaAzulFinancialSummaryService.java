package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

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
    private static final ZoneOffset BRASILIA_OFFSET = ZoneOffset.ofHours(-3);
    private static final int SUMMARY_PAGE_SIZE = 100;
    private static final int SUMMARY_MAX_PAGES = 30;
    private static final Set<String> ASSET_ACCOUNT_TYPES = Set.of(
        "CONTA_CORRENTE",
        "POUPANCA",
        "INVESTIMENTO",
        "APLICACAO",
        "CAIXINHA");
    private static final Set<String> LIABILITY_ACCOUNT_TYPES = Set.of(
        "CARTAO_CREDITO",
        "CARTAO_DE_CREDITO");

    private final ContaAzulTokenService contaAzulTokenService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final JsonSafeReader jsonSafeReader;
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
        long syncedReceiptsCount = processedSaleRepository.count();
        ExecutorService executor = buildSummaryExecutor();

        try {
            String accessToken = contaAzulTokenService.getValidAccessToken();

            // Busca parcelas recebidas para calcular total pago real por baixas.
            ReceivedParcelsResult receivedParcelsResult = safeFetchReceivedParcels(accessToken);
            StatusResult pendingResult = safeFetchTotalByStatus(accessToken, ContaAzulStatus.EM_ABERTO);
            StatusResult paidResult = safeFetchTotalPaidByBaixas(accessToken, receivedParcelsResult.parcels(), executor);
            StatusResult balanceResult = safeFetchConsolidatedBalance(accessToken, executor);

            long totalPaidCents = paidResult.total();
            long totalPendingCents = pendingResult.total();
            long balanceCents = balanceResult.total();

            boolean externalServiceAvailable =
                    receivedParcelsResult.available()
                            && pendingResult.available()
                            && paidResult.available()
                            && balanceResult.available();

            return new FinancialSummary(
                    balanceCents,
                    totalPendingCents,
                    totalPaidCents,
                    "BRL",
                    syncedReceiptsCount,
                    externalServiceAvailable,
                    resolveSummaryLastUpdatedAt(receivedParcelsResult.lastUpdatedAt()));
        } catch (Exception ex) {
            log.warn("Falha ao montar resumo financeiro da Conta Azul. Retornando fallback com serviço externo indisponível.", ex);
            return new FinancialSummary(
                    0L,
                    0L,
                    0L,
                    "BRL",
                    syncedReceiptsCount,
                    false,
                    resolveSummaryLastUpdatedAt(null));
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
        int poolSize = Math.max(2, Math.min(8, Runtime.getRuntime().availableProcessors()));
        return Executors.newFixedThreadPool(poolSize);
    }

    private StatusResult safeFetchTotalByStatus(String accessToken, String status) {
        try {
            return fetchTotalByStatus(accessToken, status);
        } catch (Exception ex) {
            log.warn("Falha ao consultar status '{}' na Conta Azul. Considerando indisponível para este status.", status, ex);
            return new StatusResult(0L, false);
        }
    }

    private ReceivedParcelsResult safeFetchReceivedParcels(String accessToken) {
        try {
            return fetchReceivedParcels(accessToken);
        } catch (Exception ex) {
            log.warn("Falha ao consultar parcelas RECEBIDO para cálculo real por baixas.", ex);
            return new ReceivedParcelsResult(List.of(), false, null);
        }
    }

    private StatusResult safeFetchConsolidatedBalance(String accessToken, ExecutorService executor) {
        try {
            return fetchConsolidatedBalance(accessToken, executor);
        } catch (Exception ex) {
            log.warn("Falha ao consolidar saldos das contas financeiras da Conta Azul.", ex);
            return new StatusResult(0L, false);
        }
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

    // Recupera o total agregado (em centavos) para o `status` informado usando o accessToken.
    // Trata 401 autorizando refresh automático do token quando aplicável.
    private StatusResult fetchTotalByStatus(String accessToken, String status) {
        try {
            ResponseEntity<String> response = executePaymentsRequest(status, accessToken, 1);

            long total = extractTotalCents(response.getBody(), status);
            return new StatusResult(total, true);
        } catch (Unauthorized ex) {
            String errorBody = ex.getResponseBodyAsString();
            log.warn(
                    "Token expirado ou inválido ao buscar pagamentos com status='{}'. Tentando refresh automático. Resposta: {}",
                    status,
                    errorBody);

            try {
                String newToken = contaAzulTokenService.forceRefresh();
                ResponseEntity<String> retryResponse = executePaymentsRequest(status, newToken, 1);
                long total = extractTotalCents(retryResponse.getBody(), status);
                return new StatusResult(total, true);
            } catch (Exception refreshEx) {
                log.error("Refresh também falhou. Re-autorização manual necessária.", refreshEx);
                throw new ContaAzulAuthException(
                        "Token inválido e refresh falhou. Refaça o login na Conta Azul.",
                        refreshEx);
            }
        } catch (HttpClientErrorException ex) {
            String errorBody = ex.getResponseBodyAsString();

            // Tratar 403 (forbidden) como caso de conta não elegível para uso da API
            if (ex.getStatusCode().value() == 403) {
                log.warn(
                        "ContaAzul API retornou 403 FORBIDDEN ao buscar pagamentos com status='{}'. Resposta: {}",
                        status, errorBody);
                // Não interromper a visualização do financeiro: retornar 0 para este status e sinalizar indisponibilidade.
                return new StatusResult(0L, false);
            }

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

    private static record StatusResult(long total, boolean available) {}

    private static record ReceivedParcelsResult(
            List<ReceivableParcelRef> parcels,
            boolean available,
            String lastUpdatedAt) {
    }

    private static record ReceivableParcelRef(String parcelaId, String displayIdentifier) {
    }

    private static record ReceivablesPageData(
            List<ReceivableParcelRef> parcels,
            OffsetDateTime latestUpdate) {
    }

    private static record FinancialAccountRef(String accountId, String type, boolean active) {
    }

    private StatusResult fetchConsolidatedBalance(String accessToken, ExecutorService executor) {
        List<FinancialAccountRef> financialAccounts = fetchFinancialAccounts(accessToken);
        List<FinancialAccountRef> eligibleAccounts = financialAccounts.stream()
                .filter(FinancialAccountRef::active)
                .filter(account -> isAssetAccountType(account.type()))
                .toList();

        long ignoredLiabilityAccounts = financialAccounts.stream()
                .filter(FinancialAccountRef::active)
                .filter(account -> isLiabilityAccountType(account.type()))
                .count();

        if (ignoredLiabilityAccounts > 0) {
            // Regra de negócio oficial: cartão de crédito é passivo e não entra no saldo consolidado de caixa.
            log.info("Ignorando {} conta(s) do tipo CARTAO_CREDITO no saldo consolidado.", ignoredLiabilityAccounts);
        }

        if (eligibleAccounts.isEmpty()) {
            return new StatusResult(0L, true);
        }

        AtomicBoolean available = new AtomicBoolean(true);
        List<CompletableFuture<Long>> futures = eligibleAccounts.stream()
                .map(account -> CompletableFuture.supplyAsync(() -> {
                    try {
                        return fetchAccountCurrentBalanceCents(accessToken, account.accountId());
                    } catch (Exception ex) {
                        available.set(false);
                        log.warn("Falha ao consultar saldo atual da conta financeira {}.", account.accountId(), ex);
                        return 0L;
                    }
                }, executor))
                .toList();

        long totalBalance = futures.stream()
                .mapToLong(CompletableFuture::join)
                .sum();

        return new StatusResult(totalBalance, available.get());
    }

    private List<FinancialAccountRef> fetchFinancialAccounts(String accessToken) {
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

                boolean active = readBooleanFromPaths(accountNode, "ativo", "active", "is_active", "isActive");
                String accountType = normalizeAccountType(jsonSafeReader.readText(accountNode, "tipo", "type", "categoria"));
                accounts.add(new FinancialAccountRef(accountId.trim(), accountType, active));
            }

            return accounts;
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Falha ao interpretar lista de contas financeiras da Conta Azul.", ex);
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

    private boolean isAssetAccountType(String accountType) {
        return ASSET_ACCOUNT_TYPES.contains(accountType);
    }

    private boolean isLiabilityAccountType(String accountType) {
        return LIABILITY_ACCOUNT_TYPES.contains(accountType);
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

        // Filtro rigoroso: somente contas explicitamente ativas entram no saldo.
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

    private long fetchAccountCurrentBalanceCents(String accessToken, String accountId) {
        String uri = normalizeContaAzulBaseUrl() + "/v1/conta-financeira/" + accountId + "/saldo-atual";
        ResponseEntity<String> response = executeGetWithRefresh(uri, accessToken);

        if (response.getBody() == null || response.getBody().isBlank()) {
            return 0L;
        }

        try {
            JsonNode root = objectMapper.readTree(response.getBody().getBytes(StandardCharsets.UTF_8));
            BigDecimal balance = readDecimalFromPaths(
                    root,
                    "saldo.valor",
                    "saldo",
                    "saldo_atual.valor",
                    "saldo_atual",
                    "valor",
                    "data.saldo",
                    "data.valor");
            return balance != null ? toCents(balance) : 0L;
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Falha ao interpretar saldo da conta financeira " + accountId + ".", ex);
        }
    }

    private StatusResult fetchTotalPaidByBaixas(
            String accessToken,
            List<ReceivableParcelRef> parcels,
            ExecutorService executor) {
        if (parcels == null || parcels.isEmpty()) {
            return new StatusResult(0L, true);
        }

        AtomicBoolean available = new AtomicBoolean(true);
        List<CompletableFuture<Long>> futures = parcels.stream()
                .map(parcel -> CompletableFuture.supplyAsync(() -> {
                    try {
                        return fetchParcelaPaidCentsByBaixas(accessToken, parcel);
                    } catch (Exception ex) {
                        available.set(false);
                        String displayId = StringUtils.hasText(parcel.displayIdentifier())
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

        return new StatusResult(totalPaidCents, available.get());
    }

    private long fetchParcelaPaidCentsByBaixas(String accessToken, ReceivableParcelRef parcel) {
        String uri = normalizeContaAzulBaseUrl() + "/v1/financeiro/eventos-financeiros/parcelas/" + parcel.parcelaId();
        ResponseEntity<String> response = executeGetWithRefresh(uri, accessToken);

        if (response.getBody() == null || response.getBody().isBlank()) {
            return 0L;
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
                return 0L;
            }

            BigDecimal total = BigDecimal.ZERO;
            for (JsonNode baixaNode : baixasNode) {
                BigDecimal baixaAmount = readDecimalFromPaths(
                        baixaNode,
                        "valor_liquido",
                        "valorLiquido",
                        "valor_liquido.valor",
                        "liquido.valor");

                // Regra oficial V2: total pago deve refletir exclusivamente a soma de valor_liquido das baixas.
                if (baixaAmount != null) {
                    total = total.add(baixaAmount);
                }
            }

            return toCents(total);
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Falha ao interpretar detalhe de baixas da parcela " + parcel.parcelaId() + ".", ex);
        }
    }

    private ReceivedParcelsResult fetchReceivedParcels(String accessToken) {
        Map<String, ReceivableParcelRef> parcelMap = new LinkedHashMap<>();
        OffsetDateTime latestUpdate = null;

        for (int page = 1; page <= SUMMARY_MAX_PAGES; page++) {
            ResponseEntity<String> response = executePaymentsRequest(ContaAzulStatus.RECEBIDO, accessToken, page);
            ReceivablesPageData pageData = parseReceivablesPage(response.getBody());

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
                    (existing, incoming) -> StringUtils.hasText(existing.displayIdentifier()) ? existing : incoming);
            }

            if (pageData.parcels().size() < SUMMARY_PAGE_SIZE) {
                break;
            }
        }

        return new ReceivedParcelsResult(
                new ArrayList<>(parcelMap.values()),
                true,
                latestUpdate != null ? latestUpdate.toString() : null);
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

    private String resolveSummaryLastUpdatedAt(String rawLastUpdatedAt) {
        OffsetDateTime parsed = parseApiDateToBrasiliaOffsetDateTime(rawLastUpdatedAt);
        if (parsed != null) {
            return parsed.withOffsetSameInstant(BRASILIA_OFFSET).toString();
        }

        return OffsetDateTime.now(BRASILIA_OFFSET).toString();
    }

    private OffsetDateTime parseApiDateToBrasiliaOffsetDateTime(String rawDate) {
        if (!StringUtils.hasText(rawDate)) {
            return null;
        }

        String trimmed = rawDate.trim();

        try {
            if (trimmed.endsWith("Z") || trimmed.endsWith("z")) {
                // Se vier com Z, trata como UTC e converte para horário de Brasília.
                return Instant.parse(trimmed).atOffset(BRASILIA_OFFSET);
            }
        } catch (DateTimeParseException ignored) {
            // Continua para os próximos parseadores.
        }

        try {
            return OffsetDateTime.parse(trimmed).withOffsetSameInstant(BRASILIA_OFFSET);
        } catch (DateTimeParseException ignored) {
            // Continua para parse sem offset.
        }

        try {
            LocalDateTime localDateTime = LocalDateTime.parse(trimmed);
            return localDateTime.atOffset(BRASILIA_OFFSET);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }

        return null;
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

    // Executa a requisição para o endpoint de pagamentos da Conta Azul com o status
    // e o token fornecidos. Retorna o corpo da resposta encapsulado em `ResponseEntity<String>`.
    private ResponseEntity<String> executePaymentsRequest(String status, String accessToken, int page) {
        LocalDate hoje = LocalDate.now();
        LocalDate inicioMesAtual = hoje.withDayOfMonth(1);
        String dataVencimentoDe = formatDateForContaAzul(inicioMesAtual);
        String dataVencimentoAte = formatDateForContaAzul(hoje);

        log.debug(
            "Parâmetros ContaAzul (resumo mensal): vencimento_de={}, vencimento_ate={}, status={}",
            dataVencimentoDe,
            dataVencimentoAte,
            status);

        // Envia explicitamente os parâmetros obrigatórios de vencimento no formato YYYY-MM-DD.
        // Normaliza a URL para impedir envio de /api/v1 e manter somente o formato oficial /v1.
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

    private String normalizeContaAzulBaseUrl() {
        String normalized = StringUtils.hasText(contaAzulApiV2BaseUrl)
                ? contaAzulApiV2BaseUrl.trim()
                : "https://api-v2.contaazul.com";

        normalized = normalized.replace("https://api.contaazul.com", "https://api-v2.contaazul.com");
        normalized = normalized.replaceAll("/+$", "");
        normalized = normalized.replaceAll("(?i)https://api-v2\\.contaazul\\.com/api$", "https://api-v2.contaazul.com");
        return normalized;
    }

    // Centraliza a formatação de data exigida pela API da Conta Azul (YYYY-MM-DD).
    private String formatDateForContaAzul(LocalDate date) {
        return date.format(DATE_FORMATTER);
    }

    private String normalizePaymentsUrl() {
        String normalized = paymentsUrl != null ? paymentsUrl.trim() : "";
        if (normalized.isBlank()) {
            return "https://api-v2.contaazul.com/v1/financeiro/eventos-financeiros/contas-a-receber/buscar";
        }

        normalized = normalized.replace("https://api.contaazul.com", "https://api-v2.contaazul.com");
        // Remove barra final da base para evitar variações de concatenação em ambiente.
        normalized = normalized.replaceAll("/+$", "");
        normalized = normalized.replaceAll("(?i)/api/v1/", "/v1/");
        normalized = normalized.replaceAll("(?i)https://api-v2\\.contaazul\\.com/api/", "https://api-v2.contaazul.com/");

        // Corrige configuração antiga sem /eventos-financeiros e/ou sem /buscar.
        if (normalized.matches("(?i).*/v1/financeiro/contas-a-receber$")) {
            normalized = normalized.replaceAll(
                    "(?i)/v1/financeiro/contas-a-receber$",
                    "/v1/financeiro/eventos-financeiros/contas-a-receber/buscar");
        } else if (normalized.matches("(?i).*/v1/financeiro/eventos-financeiros/contas-a-receber$")) {
            normalized = normalized + "/buscar";
        }

        return normalized;
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

    private BigDecimal readDecimalFromPaths(JsonNode node, String... paths) {
        for (String path : paths) {
            BigDecimal value = readDecimal(node, path);
            if (value != null) {
                return value;
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
            long syncedReceiptsCount,
            boolean externalServiceAvailable,
            String lastUpdatedAt) {
        }
}
