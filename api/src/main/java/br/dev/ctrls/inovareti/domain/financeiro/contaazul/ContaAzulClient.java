package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ContaAzulClient {

    private static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES = 30;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ContaAzulTokenService contaAzulTokenService;

    @Value("${app.contaazul.payments-url:}")
    private String receivableEventsSearchUrl;

    @Value("${app.contaazul.sales-pdf-v1-url-template:https://api-v2.contaazul.com/v1/venda/{id}/imprimir}")
    private String salePdfV1UrlTemplate;

    @Value("${app.contaazul.sales-v2-url:https://api-v2.contaazul.com/v1/venda/busca}")
    private String salesV2Url;

    @Value("${app.contaazul.sales-v2-stable-url:https://api.contaazul.com/v2/sales}")
    private String salesV2StableUrl;

    @Value("${app.contaazul.customers-v1-url:https://api-v2.contaazul.com/v1/pessoas}")
    private String customersV1Url;

    @Value("${app.contaazul.customer-by-id-v1-url-template:https://api-v2.contaazul.com/v1/pessoas/{id}}")
    private String customerByIdV1UrlTemplate;

    public boolean hasSalesConfiguration() {
        return StringUtils.hasText(receivableEventsSearchUrl) && StringUtils.hasText(salePdfV1UrlTemplate);
    }

    /**
     * Consulta eventos financeiros (contas a receber) e extrai o sale_id (venda_id) para download de PDF.
     */
    public List<SaleItem> fetchAcquittedSales() {
        LocalDate hoje = LocalDate.now();
        String dataVencimentoDe = hoje.withDayOfMonth(1).format(DATE_FORMATTER);
        String dataVencimentoAte = hoje.format(DATE_FORMATTER);
        return fetchAcquittedSales(dataVencimentoDe, dataVencimentoAte);
        }

        public List<SaleItem> fetchAcquittedSales(String dataVencimentoDe, String dataVencimentoAte) {
        List<SaleItem> sales = new ArrayList<>();

        for (int page = 1; page <= MAX_PAGES; page++) {
            String uri = buildReceivableSearchUri(page, dataVencimentoDe, dataVencimentoAte);

            log.info("Solicitando lista de vendas ao Conta Azul: {}", uri);
            log.debug("Consultando vendas na Conta Azul. pagina={}, uri={}", page, uri);

            String payload = executeJsonGetWithRefresh(uri);
            log.debug("JSON bruto retornado pela Conta Azul (pagina {}): {}", page, payload);
            List<SaleItem> pageItems = parseAcquittedSales(payload);

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
     * Baixa o PDF do recibo da venda usando o endpoint v1 da Conta Azul.
     */
    public byte[] downloadSalePdf(String saleId) {
        String normalizedTemplate = normalizeSalePrintTemplate(salePdfV1UrlTemplate);
        String uri = normalizedTemplate.replace("{id}", saleId).replace("{saleId}", saleId);
        String token = contaAzulTokenService.getValidAccessToken();

        log.info("Sincronizando recibos via endpoint de impressão: /v1/venda/{}/imprimir", saleId);

        try {
            return executePdfGet(uri, token);
        } catch (HttpClientErrorException.Unauthorized ex) {
            log.warn("Token expirado ao baixar PDF da venda {}. Tentando refresh.", saleId);
            String refreshedToken = contaAzulTokenService.forceRefresh();
            return executePdfGet(uri, refreshedToken);
        }
    }

    /**
     * Busca o ID do cliente na Conta Azul por e-mail.
     */
    public Optional<String> findCustomerIdByEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return Optional.empty();
        }

        String normalizedEmail = email.trim();
        log.info("Buscando cliente no Conta Azul pelo e-mail: {}", normalizedEmail);

        String uri = UriComponentsBuilder.fromUriString(customersV1Url)
            .queryParam("emails", normalizedEmail)
            .build()
            .encode(StandardCharsets.UTF_8)
            .toUriString();

        try {
            String payload = executeJsonGetWithRefresh(uri);
            log.debug("Resposta da API Conta Azul: {}", payload);
            return parseCustomerIdByEmail(payload, normalizedEmail);
        } catch (RuntimeException ex) {
            log.warn("Falha ao consultar cliente Conta Azul por e-mail {}: {}", email, ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Busca uma venda liquidada específica pelo seu ID.
     */
    public Optional<SaleItem> findAcquittedSaleById(String saleId) {
        if (!StringUtils.hasText(saleId)) {
            return Optional.empty();
        }

        String normalizedSaleId = saleId.trim();
        LocalDate hoje = LocalDate.now();
        String dataVencimentoDe = hoje.withDayOfMonth(1).format(DATE_FORMATTER);
        String dataVencimentoAte = hoje.format(DATE_FORMATTER);

        for (int page = 1; page <= MAX_PAGES; page++) {
            String uri = buildReceivableSearchUri(page, dataVencimentoDe, dataVencimentoAte);

            String payload = executeJsonGetWithRefresh(uri);
            List<SaleItem> pageItems = parseAcquittedSales(payload);

            Optional<SaleItem> match = pageItems.stream()
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
     * Busca o e-mail do cliente pelo ID na Conta Azul usando /v1/pessoas/{id}.
     */
    public Optional<String> findCustomerEmailById(String customerId) {
        if (!StringUtils.hasText(customerId)) {
            return Optional.empty();
        }

        String uri = customerByIdV1UrlTemplate
                .replace("{id}", customerId.trim())
                .replace("{customerId}", customerId.trim());

        log.debug("Solicitando detalhes do médico em: /v1/pessoas/{}", customerId.trim());

        try {
            String payload = executeJsonGetWithRefresh(uri);
            return parseCustomerEmailById(payload);
        } catch (RuntimeException ex) {
            log.warn("Falha ao consultar e-mail do cliente Conta Azul (id={}): {}", customerId, ex.getMessage());
            return Optional.empty();
        }
    }

    public List<PessoaItem> fetchAllPessoas() {
        List<PessoaItem> pessoas = new ArrayList<>();
        Long totalExpected = null;

        for (int page = 1; page <= MAX_PAGES; page++) {
            String uri = UriComponentsBuilder.fromUriString(customersV1Url)
                    .queryParam("pagina", page)
                    .queryParam("tamanho_pagina", PAGE_SIZE)
                    .build()
                    .toUriString();

            String payload = executeJsonGetWithRefresh(uri);
            PessoasPage pageResult = parsePessoasPage(payload);

            if (pageResult.total() != null && pageResult.total() > 0) {
                totalExpected = pageResult.total();
            }

            if (pageResult.itens().isEmpty()) {
                break;
            }

            pessoas.addAll(pageResult.itens());

            if (totalExpected != null && pessoas.size() >= totalExpected) {
                break;
            }

            if (pageResult.itens().size() < PAGE_SIZE) {
                break;
            }
        }

        return pessoas;
    }

    public List<SaleItem> fetchCommittedSalesWithAcquittedParcels() {
        List<SaleItem> sales = new ArrayList<>();

        for (int page = 1; page <= MAX_PAGES; page++) {
            String payload = fetchCommittedSalesPagePayload(page);
            log.debug("JSON de Vendas (Fallback): {}", payload);
            List<SaleItem> pageItems = parseCommittedSalesWithAcquittedParcels(payload);

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

    public Optional<SaleItem> fetchSaleByNumber(Integer numero) {
        if (numero == null) {
            return Optional.empty();
        }

        String normalizedNumber = String.valueOf(numero);
        String uri = UriComponentsBuilder.fromUriString("https://api-v2.contaazul.com/v1/venda/busca")
                .queryParam("numero", normalizedNumber)
                .build()
                .toUriString();

        String payload = executeJsonGetWithRefresh(uri);
        try {
            SaleByNumberResponseDTO response = objectMapper.readValue(
                    payload.getBytes(StandardCharsets.UTF_8),
                    SaleByNumberResponseDTO.class);

            if (response == null || response.itens() == null || response.itens().isEmpty()) {
                return Optional.empty();
            }

            SaleByNumberItemDTO matchedItem = response.itens().stream()
                    .filter(item -> item != null && StringUtils.hasText(item.id()))
                    .filter(item -> {
                        String itemNumber = StringUtils.hasText(item.numero())
                                ? item.numero().trim()
                                : (StringUtils.hasText(item.number()) ? item.number().trim() : null);
                        return normalizedNumber.equals(itemNumber);
                    })
                    .findFirst()
                    .orElse(null);

            if (matchedItem == null || !StringUtils.hasText(matchedItem.id())) {
                log.warn("Sniper: Nenhuma venda com número exato {} foi encontrada no retorno de /v1/venda/busca.", normalizedNumber);
                return Optional.empty();
            }

            String resolvedNumber = StringUtils.hasText(matchedItem.numero())
                    ? matchedItem.numero().trim()
                    : (StringUtils.hasText(matchedItem.number()) ? matchedItem.number().trim() : normalizedNumber);

            boolean hasAcquittedInstallment = hasAcquittedInstallmentInSaleByNumberItem(matchedItem);

            log.info("!!! [SNIPER DEBUG] JSON recebido para a venda [{}]: [{}]", resolvedNumber, matchedItem.id().trim());

            return Optional.of(new SaleItem(
                    matchedItem.id().trim(),
                    null,
                    null,
                    null,
                    "VENDA",
                    new VendaRef(matchedItem.id().trim()),
                    matchedItem.id().trim(),
                    matchedItem.id().trim(),
                    null,
                    resolvedNumber,
                    hasAcquittedInstallment));
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao parsear payload de venda por número da Conta Azul.", ex);
        }
    }

    public Optional<SaleItem> findSaleByNumber(String saleNumber) {
        if (!StringUtils.hasText(saleNumber)) {
            return Optional.empty();
        }

        try {
            return fetchSaleByNumber(Integer.valueOf(saleNumber.trim()));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private String fetchCommittedSalesPagePayload(int page) {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        String dataAlteracaoDe = LocalDateTime.of(yesterday, java.time.LocalTime.MIDNIGHT).format(DATE_TIME_FORMATTER);
        String dataAlteracaoAte = LocalDateTime.of(today, java.time.LocalTime.of(23, 59, 59)).format(DATE_TIME_FORMATTER);

        log.debug("Buscando vendas alteradas no período completo: {} até {}", dataAlteracaoDe, dataAlteracaoAte);

        String primaryUri = UriComponentsBuilder.fromUriString(normalizeSaleSearchBaseUrl(salesV2Url))
                .queryParam("pagina", page)
                .queryParam("tamanho_pagina", PAGE_SIZE)
                .queryParam("data_alteracao_de", dataAlteracaoDe)
                .queryParam("data_alteracao_ate", dataAlteracaoAte)
                .build()
                .toUriString();

        try {
            return executeJsonGetWithRefresh(primaryUri);
        } catch (HttpClientErrorException.NotFound ex) {
            String stableUri = UriComponentsBuilder.fromUriString(salesV2StableUrl)
                    .queryParam("pagina", page)
                    .queryParam("tamanho_pagina", PAGE_SIZE)
                    .queryParam("data_alteracao_de", dataAlteracaoDe)
                    .queryParam("data_alteracao_ate", dataAlteracaoAte)
                    .build()
                    .toUriString();

            log.warn("Endpoint de vendas não encontrado em {}. Tentando fallback estável em {}.", primaryUri, stableUri);
            return executeJsonGetWithRefresh(stableUri);
        }
    }

    private String executeJsonGetWithRefresh(String uri) {
        ContaAzulOAuthToken token = contaAzulTokenService.getValidTokenFromDatabase();

        try {
            return executeJsonGet(uri, token);
        } catch (HttpClientErrorException.Unauthorized ex) {
            log.warn("Token expirado ao consultar vendas. Tentando refresh.");
            token = contaAzulTokenService.forceRefreshAndReloadFromDatabase();
            return executeJsonGet(uri, token);
        }
    }

    private String executeJsonGet(String uri, ContaAzulOAuthToken token) {
        String authorizationHeader = "Bearer " + token.getAccessToken();
        String sanitizedAuthorizationHeader = sanitizeAuthorizationHeader(authorizationHeader);

        log.debug(
                "Enviando requisição para {} com Token iniciado em {}...",
                uri,
                sanitizeTokenPrefix(token.getAccessToken()));
        log.trace("Header Authorization sanitizado enviado: {}", sanitizedAuthorizationHeader);

        RequestEntity<Void> requestEntity = RequestEntity
            .get(java.net.URI.create(uri))
            .header("Authorization", authorizationHeader)
            .accept(MediaType.APPLICATION_JSON)
            .build();

        try {
            ResponseEntity<String> response = restTemplate.exchange(requestEntity, String.class);

            return response.getBody();
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.BadRequest | HttpClientErrorException.NotFound ex) {
            log.error(
                    "Conta Azul retornou {} ao consultar URI {}. Corpo do erro: {}",
                    ex.getStatusCode(),
                    uri,
                    ex.getResponseBodyAsString());
            throw ex;
        }
    }

    private byte[] executePdfGet(String uri, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + token);
        headers.setAccept(List.of(MediaType.APPLICATION_PDF));

        ResponseEntity<byte[]> response = restTemplate.exchange(
                uri,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                byte[].class);

        return response.getBody() != null ? response.getBody() : new byte[0];
    }

    private List<SaleItem> parseAcquittedSales(String jsonPayload) {
        if (!StringUtils.hasText(jsonPayload)) {
            return List.of();
        }

        try {
            JsonNode root = objectMapper.readTree(jsonPayload.getBytes(StandardCharsets.UTF_8));
            JsonNode entries = resolveArrayNodeFromDtoOrFallback(root);
            if (entries == null || !entries.isArray() || entries.isEmpty()) {
                return List.of();
            }

            List<SaleItem> sales = new ArrayList<>();
            for (JsonNode node : entries) {
                String parcelaId = readText(
                        node,
                        "id",
                        "parcela_id",
                        "parcela.id",
                        "titulo.id");

                String origem = readText(
                    node,
                    "origem",
                    "source");

                String vendaNestedId = readText(
                    node,
                    "venda.id",
                    "sale.id");

                VendaRef venda = StringUtils.hasText(vendaNestedId)
                    ? new VendaRef(vendaNestedId)
                    : null;

                String origemSaleId = readText(
                        node,
                        "origem.venda_id",
                        "origem.sale_id",
                        "origem.venda.id",
                        "origem.id");

                String vendaId = readText(
                        node,
                        "venda_id",
                        "sale_id",
                        "sale.id",
                        "venda.id");

                String descricao = readText(
                    node,
                    "descricao",
                    "description",
                    "historico",
                    "observacao");

                String saleId = venda != null && StringUtils.hasText(venda.id())
                    ? venda.id()
                    : (StringUtils.hasText(origemSaleId)
                        ? origemSaleId
                        : (StringUtils.hasText(vendaId) ? vendaId : null));

                String status = readText(node, "status", "sale.status", "situacao", "estado");

                if (!isReceivableItemPaid(status)) {
                    continue;
                }

                String customerUuid = readText(
                        node,
                        "customer.id",
                        "customer.uuid",
                        "customerId",
                        "customer_uuid",
                        "cliente.id",
                        "cliente.uuid",
                        "contato.id",
                        "pessoa.id",
                        "origem.cliente.id");
                    String customerName = readText(
                        node,
                        "customer.name",
                        "customer_name",
                        "cliente.nome",
                        "nome",
                        "contato.nome",
                        "pessoa.nome");

                        sales.add(new SaleItem(saleId, customerUuid, customerName, parcelaId, origem, venda, origemSaleId, vendaId, descricao, null, true));
            }

            return sales;
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao parsear payload de vendas da Conta Azul.", ex);
        }
    }

    private JsonNode resolveArrayNode(JsonNode root) {
        if (root == null || root.isNull()) {
            return objectMapper.createArrayNode();
        }

        if (root.isArray()) {
            return root;
        }

        if (root.has("data") && root.get("data").isArray()) {
            return root.get("data");
        }

        if (root.has("items") && root.get("items").isArray()) {
            return root.get("items");
        }

        if (root.has("content") && root.get("content").isArray()) {
            return root.get("content");
        }

        if (root.has("content") && root.get("content").isObject()) {
            JsonNode content = root.get("content");

            if (content.has("items") && content.get("items").isArray()) {
                return content.get("items");
            }

            if (content.has("data") && content.get("data").isArray()) {
                return content.get("data");
            }
        }

        return objectMapper.createArrayNode();
    }

    private JsonNode resolveArrayNodeFromDtoOrFallback(JsonNode root) {
        try {
            ReceivablesSearchResponseDTO dto = objectMapper.treeToValue(root, ReceivablesSearchResponseDTO.class);
            if (dto != null) {
                if (dto.itens() != null && !dto.itens().isEmpty()) {
                    return objectMapper.valueToTree(dto.itens());
                }

                if (dto.content() != null && dto.content().itens() != null && !dto.content().itens().isEmpty()) {
                    return objectMapper.valueToTree(dto.content().itens());
                }
            }
        } catch (IOException | IllegalArgumentException ex) {
            log.debug("Falha ao mapear DTO de parcelas (itens). Aplicando fallback por JsonNode.", ex);
        }

        return resolveArrayNode(root);
    }

    private boolean isReceivableItemPaid(String status) {
        if (!StringUtils.hasText(status)) {
            return true;
        }

        String normalizedStatus = status.trim();
        return "ACQUITTED".equalsIgnoreCase(normalizedStatus)
                || "RECEBIDO".equalsIgnoreCase(normalizedStatus)
                || "PAGO".equalsIgnoreCase(normalizedStatus)
                || "PAID".equalsIgnoreCase(normalizedStatus)
                || "LIQUIDADO".equalsIgnoreCase(normalizedStatus);
    }

    private String buildReceivableSearchUri(int page, String dataVencimentoDe, String dataVencimentoAte) {
        return UriComponentsBuilder.fromUriString(receivableEventsSearchUrl)
                .queryParam("pagina", page)
                .queryParam("tamanho_pagina", PAGE_SIZE)
                .queryParam("status", "RECEBIDO")
                .queryParam("data_vencimento_de", dataVencimentoDe)
                .queryParam("data_vencimento_ate", dataVencimentoAte)
                .build()
                .toUriString();
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

    private String normalizeSalePrintTemplate(String rawTemplate) {
        if (!StringUtils.hasText(rawTemplate)) {
            return "https://api-v2.contaazul.com/v1/venda/{id}/imprimir";
        }

        String normalized = rawTemplate.trim();
        if (normalized.contains("/v1/sales/")) {
            normalized = normalized.replace("/v1/sales/", "/v1/venda/");
        }

        if (normalized.endsWith("/pdf")) {
            normalized = normalized.substring(0, normalized.length() - 4) + "/imprimir";
        }

        return normalized;
    }

    private String sanitizeTokenPrefix(String accessToken) {
        if (!StringUtils.hasText(accessToken)) {
            return "n/a";
        }

        String normalized = accessToken.trim();
        return normalized.length() <= 4 ? normalized : normalized.substring(0, 4);
    }

    private String sanitizeAuthorizationHeader(String authorizationHeader) {
        if (!StringUtils.hasText(authorizationHeader)) {
            return "Authorization: n/a";
        }

        String value = authorizationHeader.trim();
        if (!value.startsWith("Bearer ")) {
            return "Authorization: [formato inválido]";
        }

        String token = value.substring("Bearer ".length());
        if (!StringUtils.hasText(token)) {
            return "Authorization: Bearer [vazio]";
        }

        String normalizedToken = token.trim();
        if (normalizedToken.length() <= 8) {
            return "Authorization: Bearer " + normalizedToken;
        }

        String start = normalizedToken.substring(0, 4);
        String end = normalizedToken.substring(normalizedToken.length() - 4);
        return "Authorization: Bearer " + start + "..." + end;
    }

    private Optional<String> parseCustomerIdByEmail(String jsonPayload, String email) {
        if (!StringUtils.hasText(jsonPayload)) {
            return Optional.empty();
        }

        try {
            JsonNode root = objectMapper.readTree(jsonPayload.getBytes(StandardCharsets.UTF_8));
            String normalizedEmail = email.trim();

            if (root.isObject()) {
                String directEmail = readText(root, "email", "data.email", "emails.0.address", "emails.0.email");
                String directId = readText(root, "id", "uuid", "data.id", "data.uuid");
                if (StringUtils.hasText(directEmail)
                        && StringUtils.hasText(directId)
                        && normalizedEmail.equalsIgnoreCase(directEmail.trim())) {
                    return Optional.of(directId);
                }
            }

            JsonNode entries = resolveArrayNode(root);
            if (entries == null || !entries.isArray()) {
                return Optional.empty();
            }

            if (entries.isEmpty()) {
                log.info("Nenhum cliente encontrado para o e-mail informado.");
                return Optional.empty();
            }

            for (JsonNode node : entries) {
                String nodeEmail = readText(node, "email", "emails.0.address", "emails.0.email");
                if (StringUtils.hasText(nodeEmail) && normalizedEmail.equalsIgnoreCase(nodeEmail.trim())) {
                    String customerId = readText(node, "id", "uuid", "customer.id", "customer.uuid");
                    if (StringUtils.hasText(customerId)) {
                        return Optional.of(customerId);
                    }
                }
            }
        } catch (IOException ex) {
            log.warn("Falha ao parsear retorno de cliente Conta Azul por e-mail {}.", email, ex);
        }

        return Optional.empty();
    }

    private Optional<String> parseCustomerEmailById(String jsonPayload) {
        if (!StringUtils.hasText(jsonPayload)) {
            return Optional.empty();
        }

        try {
            JsonNode root = objectMapper.readTree(jsonPayload.getBytes(StandardCharsets.UTF_8));
            String email = readText(
                    root,
                    "email",
                    "data.email",
                    "customer.email",
                    "person.email",
                    "emails.0.address",
                    "emails.0.email",
                    "contacts.0.email");

            return StringUtils.hasText(email) ? Optional.of(email) : Optional.empty();
        } catch (IOException ex) {
            log.warn("Falha ao parsear retorno de e-mail do cliente na Conta Azul.", ex);
            return Optional.empty();
        }
    }

    private PessoasPage parsePessoasPage(String jsonPayload) {
        if (!StringUtils.hasText(jsonPayload)) {
            return new PessoasPage(List.of(), null);
        }

        try {
            JsonNode root = objectMapper.readTree(jsonPayload.getBytes(StandardCharsets.UTF_8));
            Long total = readLong(root, "total", "content.total", "paginacao.total", "meta.total");

            JsonNode entries = resolveArrayNode(root);
            if (entries == null || !entries.isArray() || entries.isEmpty()) {
                return new PessoasPage(List.of(), total);
            }

            List<PessoaItem> itens = new ArrayList<>();
            for (JsonNode node : entries) {
                String id = readText(node, "id", "uuid", "pessoa.id");
                String nome = readText(node, "nome", "name", "razao_social", "fantasia");
                String email = readText(node, "email", "emails.0.address", "emails.0.email");

                if (!StringUtils.hasText(id)) {
                    continue;
                }

                itens.add(new PessoaItem(id, nome, email));
            }

            return new PessoasPage(itens, total);
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao parsear payload de pessoas da Conta Azul.", ex);
        }
    }

    private List<SaleItem> parseCommittedSalesWithAcquittedParcels(String jsonPayload) {
        if (!StringUtils.hasText(jsonPayload)) {
            return List.of();
        }

        try {
            JsonNode root = objectMapper.readTree(jsonPayload.getBytes(StandardCharsets.UTF_8));
            JsonNode entries = resolveArrayNode(root);
            if (entries == null || !entries.isArray() || entries.isEmpty()) {
                return List.of();
            }

            List<SaleItem> sales = new ArrayList<>();
            for (JsonNode saleNode : entries) {
                String saleId = readText(saleNode, "id", "sale_id", "venda.id");
                if (!StringUtils.hasText(saleId)) {
                    continue;
                }

                String customerUuid = readText(
                        saleNode,
                        "customer.id",
                        "customer.uuid",
                        "cliente.id",
                        "cliente.uuid",
                        "person.id",
                        "pessoa.id");

                String customerName = readText(
                        saleNode,
                        "customer.name",
                        "cliente.nome",
                        "person.nome",
                        "person.name");

                String saleNumber = readText(
                    saleNode,
                    "number",
                    "numero",
                    "sale.number",
                    "venda.numero");

                sales.add(new SaleItem(
                        saleId,
                        customerUuid,
                        customerName,
                        null,
                        "VENDA",
                        new VendaRef(saleId),
                        saleId,
                    saleId,
                    null,
                    saleNumber,
                    hasAcquittedInstallmentInSaleNode(saleNode)));
            }

            return sales;
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao parsear payload de vendas COMMITTED da Conta Azul.", ex);
        }
    }

    private boolean hasAcquittedInstallmentInSaleByNumberItem(SaleByNumberItemDTO saleItem) {
        if (saleItem == null) {
            return false;
        }

        if (saleItem.installments() != null) {
            for (SaleInstallmentDTO installment : saleItem.installments()) {
                if (installment != null && "ACQUITTED".equalsIgnoreCase(installment.status())) {
                    return true;
                }
            }
        }

        if (saleItem.parcelas() != null) {
            for (SaleInstallmentDTO parcela : saleItem.parcelas()) {
                if (parcela != null && "ACQUITTED".equalsIgnoreCase(parcela.status())) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean hasAcquittedInstallmentInSaleNode(JsonNode saleNode) {
        if (saleNode == null || saleNode.isNull()) {
            return false;
        }

        JsonNode installments = saleNode.get("installments");
        if (installments != null && installments.isArray()) {
            for (JsonNode installment : installments) {
                String installmentStatus = readText(installment, "status", "situacao", "estado");
                if ("ACQUITTED".equalsIgnoreCase(installmentStatus)) {
                    return true;
                }
            }
        }

        JsonNode parcelas = saleNode.get("parcelas");
        if (parcelas != null && parcelas.isArray()) {
            for (JsonNode parcela : parcelas) {
                String parcelaStatus = readText(parcela, "status", "situacao", "estado");
                if ("ACQUITTED".equalsIgnoreCase(parcelaStatus)) {
                    return true;
                }
            }
        }

        return false;
    }

    private String readText(JsonNode node, String... paths) {
        for (String path : paths) {
            JsonNode current = node;
            for (String segment : path.split("\\.")) {
                if (current == null) {
                    break;
                }

                if (current.isArray()) {
                    int index;
                    try {
                        index = Integer.parseInt(segment);
                    } catch (NumberFormatException ex) {
                        current = null;
                        break;
                    }

                    current = index >= 0 && index < current.size() ? current.get(index) : null;
                    continue;
                }

                current = current.get(segment);
            }

            if (current != null && !current.isNull()) {
                String value = current.asText();
                if (StringUtils.hasText(value)) {
                    return value;
                }
            }
        }

        return null;
    }

    private Long readLong(JsonNode node, String... paths) {
        String value = readText(node, paths);
        if (!StringUtils.hasText(value)) {
            return null;
        }

        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

        @JsonIgnoreProperties(ignoreUnknown = true)
        private record ReceivablesSearchResponseDTO(
            @JsonProperty("itens") List<JsonNode> itens,
            @JsonProperty("content") ReceivablesContentDTO content) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        private record ReceivablesContentDTO(
            @JsonProperty("itens") List<JsonNode> itens) {
        }

        public record VendaRef(String id) {
        }

        public record SaleItem(
            String saleId,
            String customerUuid,
            String customerName,
            String parcelaId,
            String origem,
            VendaRef venda,
            String origemSaleId,
            String vendaId,
            String descricao,
            String saleNumber,
            boolean hasAcquittedInstallment) {
    }

            @JsonIgnoreProperties(ignoreUnknown = true)
            private record SaleByNumberResponseDTO(
                @JsonProperty("itens") List<SaleByNumberItemDTO> itens) {
            }

            @JsonIgnoreProperties(ignoreUnknown = true)
            private record SaleByNumberItemDTO(
                @JsonProperty("id") String id,
                @JsonProperty("numero") String numero,
                @JsonProperty("number") String number,
                @JsonProperty("installments") List<SaleInstallmentDTO> installments,
                @JsonProperty("parcelas") List<SaleInstallmentDTO> parcelas) {
            }

            @JsonIgnoreProperties(ignoreUnknown = true)
            private record SaleInstallmentDTO(
                @JsonProperty("status") String status) {
            }

    private record PessoasPage(List<PessoaItem> itens, Long total) {
    }

    public record PessoaItem(
            String id,
            String nome,
            String email) {
    }
}