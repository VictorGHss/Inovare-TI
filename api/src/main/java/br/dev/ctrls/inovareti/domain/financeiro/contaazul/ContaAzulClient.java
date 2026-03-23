package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
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

    private final ObjectMapper objectMapper;
    private final ContaAzulTokenService contaAzulTokenService;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${app.contaazul.payments-url}")
    private String receivableEventsSearchUrl;

    @Value("${app.contaazul.baixa-details-url}")
    private String baixaDetailsUrl;

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
        return StringUtils.hasText(salePdfV1UrlTemplate);
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
     * Baixa o PDF do recibo da baixa financeira usando o endpoint oficial de baixas.
     * Novo fluxo: https://api-v2.contaazul.com/v1/financeiro/eventos-financeiros/parcelas/baixa/{baixa_id}
     * Busca o anexo do tipo RECIBO_DIGITAL ou RECIBO e faz o download.
     */
    public byte[] downloadReceiptPdf(String baixaId) {
        if (!StringUtils.hasText(baixaId)) {
            throw new IllegalArgumentException("baixaId não pode ser vazio para download do recibo");
        }
        String uri = "https://api-v2.contaazul.com/v1/financeiro/eventos-financeiros/parcelas/baixa/" + baixaId.trim();
        log.info("Baixando recibo via endpoint oficial de baixas: {}", uri);
        try {
            String payload = executeJsonGetWithRefresh(uri);
            JsonNode root = objectMapper.readTree(payload.getBytes(StandardCharsets.UTF_8));
            JsonNode anexosNode = readArrayNode(root, "anexos", "evento.anexos", "data.anexos", "content.anexos");
            if (anexosNode != null && anexosNode.isArray()) {
                for (JsonNode anexoNode : anexosNode) {
                    String tipo = readText(anexoNode, "tipo", "type", "categoria");
                    String url = readText(anexoNode, "url", "link", "download_url", "arquivo.url");
                    if (StringUtils.hasText(tipo) && ("RECIBO_DIGITAL".equalsIgnoreCase(tipo) || "RECIBO".equalsIgnoreCase(tipo)) && StringUtils.hasText(url)) {
                        return downloadFile(url);
                    }
                }
            }
            log.warn("Nenhum anexo do tipo RECIBO_DIGITAL ou RECIBO encontrado para baixa {}.", baixaId);
            return new byte[0];
        } catch (ContaAzulHttpException ex) {
            if (!ex.isStatus(401)) {
                throw ex;
            }
            log.warn("Token expirado ao baixar recibo da baixa {}. Tentando refresh.", baixaId);
            // Renova token e recarrega do banco para obter o objeto com metadata
            ContaAzulOAuthToken refreshed = contaAzulTokenService.forceRefreshAndReloadFromDatabase();
            // Tenta novamente usando explicitamente o token renovado
            String uriRetry = "https://api-v2.contaazul.com/v1/financeiro/eventos-financeiros/parcelas/baixa/" + baixaId.trim();
            try {
                String payload = executeJsonGetResponse(uriRetry, refreshed).body();
                JsonNode root = objectMapper.readTree(payload.getBytes(StandardCharsets.UTF_8));
                JsonNode anexosNode = readArrayNode(root, "anexos", "evento.anexos", "data.anexos", "content.anexos");
                if (anexosNode != null && anexosNode.isArray()) {
                    for (JsonNode anexoNode : anexosNode) {
                        String tipo = readText(anexoNode, "tipo", "type", "categoria");
                        String url = readText(anexoNode, "url", "link", "download_url", "arquivo.url");
                        if (StringUtils.hasText(tipo) && ("RECIBO_DIGITAL".equalsIgnoreCase(tipo) || "RECIBO".equalsIgnoreCase(tipo)) && StringUtils.hasText(url)) {
                            return downloadFile(url, refreshed.getAccessToken());
                        }
                    }
                }
            } catch (IOException e) {
                log.warn("Falha ao parsear payload de anexo após refresh do token para baixa {}.", baixaId, e);
            }
            return new byte[0];
        } catch (IOException | IllegalStateException e) {
            log.warn("Falha ao baixar recibo da baixa {}.", baixaId, e);
            return new byte[0];
        }
    }

    /**
     * Busca a baixa financeira de uma parcela pelo ID da parcela.
     * Endpoint: GET https://api-v2.contaazul.com/v1/financeiro/eventos-financeiros/parcelas/{parcela_id}/baixa
     * Retorna o id da primeira baixa encontrada.
     */
    public Optional<String> fetchBaixaIdByParcelaId(String parcelaId) {
        if (!StringUtils.hasText(parcelaId)) {
            return Optional.empty();
        }
        String uri = "https://api-v2.contaazul.com/v1/financeiro/eventos-financeiros/parcelas/" + parcelaId.trim() + "/baixa";
        try {
            String payload = executeJsonGetWithRefresh(uri);
            JsonNode root = objectMapper.readTree(payload.getBytes(StandardCharsets.UTF_8));
            // O endpoint pode retornar um array de baixas ou um objeto único
            if (root.isArray() && root.size() > 0) {
                JsonNode first = root.get(0);
                String baixaId = readText(first, "id", "baixa_id");
                return StringUtils.hasText(baixaId) ? Optional.of(baixaId) : Optional.empty();
            } else if (root.isObject()) {
                String baixaId = readText(root, "id", "baixa_id");
                return StringUtils.hasText(baixaId) ? Optional.of(baixaId) : Optional.empty();
            }
        } catch (ContaAzulHttpException ex) {
            log.warn("Falha ao buscar baixa para parcela {}: erro HTTP ContaAzul.", parcelaId, ex);
        } catch (IOException ex) {
            log.warn("Falha ao parsear resposta de baixa para parcela {}.", parcelaId, ex);
        }
        return Optional.empty();
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

    public Optional<ParcelaDetailDTO> fetchParcelaDetail(String uuidParcela) {
        if (!StringUtils.hasText(uuidParcela)) {
            return Optional.empty();
        }

        String normalizedParcelaUuid = uuidParcela.trim();
        // Detalhe da parcela utiliza endpoint específico (não o /buscar de lista resumida)
        String uri = "https://api-v2.contaazul.com/v1/financeiro/contas-a-receber/" + normalizedParcelaUuid;

        try {
            String payload = executeJsonGetWithRefresh(uri);
            if (!StringUtils.hasText(payload)) {
                return Optional.empty();
            }

            log.info("!!! [PARCELA DEBUG] JSON recebido: {}", payload);

            JsonNode root = objectMapper.readTree(payload.getBytes(StandardCharsets.UTF_8));
            String origem = readText(
                    root,
                    "evento.referencia.origem",
                    "evento_financeiro.referencia.origem",
                    "referencia.origem");

            String saleId = null;
            if ("VENDA".equalsIgnoreCase(origem)) {
                saleId = readText(
                        root,
                        "evento.referencia.id",
                        "evento_financeiro.referencia.id",
                        "referencia.id");
            }

            if (!StringUtils.hasText(saleId)) {
                saleId = readText(
                        root,
                        "venda_id",
                        "sale_id",
                        "venda.id",
                        "sale.id",
                        "origem.venda_id",
                        "origem.sale_id",
                        "origem.venda.id");
            }

            String baixaId = readText(
                    root,
                    "evento.baixa.id",
                    "evento.baixa_id",
                    "evento.id_baixa",
                    "baixa.id",
                    "baixa_id",
                    "id_baixa",
                    "baixas.0.id",
                    "evento.baixas.0.id",
                    "itens.0.id_baixa",
                    "itens.0.baixa.id",
                    "itens.0.baixado_em");

                String normalizedSaleId = Optional.ofNullable(saleId)
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .orElse(null);

                String normalizedBaixaId = Optional.ofNullable(baixaId)
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .orElse(null);

            if (!StringUtils.hasText(normalizedSaleId) && !StringUtils.hasText(normalizedBaixaId)) {
                return Optional.empty();
            }

            return Optional.of(new ParcelaDetailDTO(normalizedSaleId, normalizedBaixaId));
        } catch (ContaAzulHttpException ex) {
            if (!ex.isStatus(404)) {
                throw ex;
            }
            log.warn("Detalhe da parcela {} não encontrado no Conta Azul.", normalizedParcelaUuid);
            return Optional.empty();
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao parsear payload de detalhe da parcela da Conta Azul.", ex);
        }
    }

    public Optional<BaixaDetailDTO> fetchBaixaDetail(String baixaId) {
        if (!StringUtils.hasText(baixaId)) {
            return Optional.empty();
        }

        String normalizedBaixaId = baixaId.trim();
        String uri = normalizeBaixaBaseUrl().replace("{id}", normalizedBaixaId);

        try {
            String payload = executeJsonGetWithRefresh(uri);
            if (!StringUtils.hasText(payload)) {
                return Optional.empty();
            }

            log.debug("JSON bruto retornado pela Conta Azul (baixa {}): {}", normalizedBaixaId, payload);

            JsonNode root = objectMapper.readTree(payload.getBytes(StandardCharsets.UTF_8));
            JsonNode anexosNode = readArrayNode(root, "anexos", "evento.anexos", "data.anexos", "content.anexos");

            if (anexosNode == null || !anexosNode.isArray() || anexosNode.isEmpty()) {
                return Optional.of(new BaixaDetailDTO(List.of()));
            }

            List<BaixaAttachmentDTO> anexos = new ArrayList<>();
            for (JsonNode anexoNode : anexosNode) {
                String id = readText(anexoNode, "id", "anexo_id");
                String tipo = readText(anexoNode, "tipo", "type", "categoria");
                String url = readText(anexoNode, "url", "link", "download_url", "arquivo.url");
                anexos.add(new BaixaAttachmentDTO(id, tipo, url));
            }

            return Optional.of(new BaixaDetailDTO(anexos));
        } catch (ContaAzulHttpException ex) {
            if (!ex.isStatus(404)) {
                throw ex;
            }
            log.warn("Detalhe da baixa {} não encontrado no Conta Azul.", normalizedBaixaId);
            return Optional.empty();
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao parsear payload de detalhe da baixa da Conta Azul.", ex);
        }
    }

    public byte[] downloadFile(String url) {
        if (!StringUtils.hasText(url)) {
            return new byte[0];
        }

        String sanitizedUri = sanitizeContaAzulUri(url.trim());
        URI externalUri = URI.create(sanitizedUri);
        log.debug("MANDANDO PARA O MUNDO EXTERNO: {}", externalUri);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(externalUri)
                .GET()
                .build();

        try {
            log.info("CUIDADO: Enviando para: " + externalUri.toString());
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ContaAzulHttpException(response.statusCode(), toUtf8String(response.body()));
            }
            return response.body() != null ? response.body() : new byte[0];
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Falha ao baixar arquivo da Conta Azul.", ex);
        }
    }

    public byte[] downloadFile(String url, String bearerToken) {
        if (!StringUtils.hasText(url)) {
            return new byte[0];
        }

        String sanitizedUri = sanitizeContaAzulUri(url.trim());
        URI externalUri = URI.create(sanitizedUri);
        log.debug("MANDANDO PARA O MUNDO EXTERNO: {}", externalUri);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(externalUri)
                .GET();

        if (StringUtils.hasText(bearerToken)) {
            builder.header("Authorization", "Bearer " + bearerToken.trim());
        }

        HttpRequest request = builder.build();

        try {
            log.info("CUIDADO: Enviando para: " + externalUri.toString());
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ContaAzulHttpException(response.statusCode(), toUtf8String(response.body()));
            }
            return response.body() != null ? response.body() : new byte[0];
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Falha ao baixar arquivo da Conta Azul.", ex);
        }
    }

    public byte[] downloadPublicFile(String url) {
        if (!StringUtils.hasText(url)) {
            return new byte[0];
        }

        String sanitizedUri = sanitizeContaAzulUri(url.trim());
        URI externalUri = URI.create(sanitizedUri);
        log.debug("MANDANDO PARA O MUNDO EXTERNO: {}", externalUri);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(externalUri)
                .GET()
                .build();

        try {
            log.info("CUIDADO: Enviando para: " + externalUri.toString());
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ContaAzulHttpException(response.statusCode(), toUtf8String(response.body()));
            }
            return response.body() != null ? response.body() : new byte[0];
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Falha ao baixar arquivo público da Conta Azul.", ex);
        }
    }

    // Lógica Sniper removida: busca por número de venda não é mais utilizada para recibo

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
            return executeJsonGetWithRefresh(stableUri);
        }
    }

    private String executeJsonGetWithRefresh(String uri) {
        return executeJsonGetResponseWithRefresh(uri).body();
    }

    private HttpResponse<String> executeJsonGetResponseWithRefresh(String uri) {
        ContaAzulOAuthToken token = contaAzulTokenService.getValidTokenFromDatabase();

        try {
            return executeJsonGetResponse(uri, token);
        } catch (ContaAzulHttpException ex) {
            if (!ex.isStatus(401)) {
                throw ex;
            }
            log.warn("Token expirado ao consultar vendas. Tentando refresh.");
            token = contaAzulTokenService.forceRefreshAndReloadFromDatabase();
            return executeJsonGetResponse(uri, token);
        }
    }

    private HttpResponse<String> executeJsonGetResponse(String uri, ContaAzulOAuthToken token) {
        String url = sanitizeContaAzulUri(uri);
        String authorizationHeader = "Bearer " + token.getAccessToken();
        String sanitizedAuthorizationHeader = sanitizeAuthorizationHeader(authorizationHeader);

        log.info("ContaAzul external request URI (JSON): {}", url);
        log.debug(
                "Enviando requisição para {} com Token iniciado em {}...",
                url,
                sanitizeTokenPrefix(token.getAccessToken()));
        log.trace("Header Authorization sanitizado enviado: {}", sanitizedAuthorizationHeader);
        URI externalUri = URI.create(url);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(externalUri)
                .header("Authorization", authorizationHeader)
                .header("Accept", "application/json")
                .GET()
                .build();

        try {
            log.debug("URL ABSOLUTA SENDO ENVIADA: " + url);
            log.debug("ENVIANDO REQUISIÇÃO PARA HOST EXTERNO: " + externalUri.getHost());
            log.info("CUIDADO: Enviando para: " + url);
            log.debug("MANDANDO PARA O MUNDO EXTERNO: " + externalUri.toString());
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error(
                        "Conta Azul retornou {} ao consultar URI {}. Corpo do erro: {}",
                        response.statusCode(),
                        url,
                        response.body());
                throw new ContaAzulHttpException(response.statusCode(), response.body());
            }
            return response;
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Falha ao consultar endpoint JSON da Conta Azul.", ex);
        }
    }

    private String toUtf8String(byte[] body) {
        if (body == null || body.length == 0) {
            return "";
        }
        return new String(body, StandardCharsets.UTF_8);
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
                    "evento_financeiro.referencia.origem",
                    "referencia.origem",
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
                    "evento_financeiro.referencia.id",
                    "referencia.id",
                        "origem.venda_id",
                        "origem.sale_id",
                        "origem.venda.id",
                        "origem.id");

                String vendaId = readText(
                        node,
                    "evento_financeiro.referencia.id",
                    "referencia.id",
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

                String baixaId = readText(
                    node,
                    "baixas.0.id",
                    "evento_financeiro.baixas.0.id",
                    "evento.baixas.0.id",
                    "id_baixa",
                    "baixa_id");

                String idReciboDigital = readText(
                    node,
                    "baixas.0.id_recibo_digital",
                    "evento_financeiro.baixas.0.id_recibo_digital",
                    "evento.baixas.0.id_recibo_digital",
                    "id_recibo_digital");

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

                        sales.add(new SaleItem(saleId, customerUuid, customerName, parcelaId, origem, venda, origemSaleId, vendaId, descricao, null, true, baixaId, idReciboDigital));
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

        // suporte para chave em português 'itens' no nível raiz
        if (root.has("itens") && root.get("itens").isArray()) {
            return root.get("itens");
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

            // suporte para chave em português 'itens' dentro de content
            if (content.has("itens") && content.get("itens").isArray()) {
                return content.get("itens");
            }
        }

        return objectMapper.createArrayNode();
    }

    private JsonNode resolveArrayNodeFromDtoOrFallback(JsonNode root) {
        try {
            ReceivablesSearchResponseDTO dto = objectMapper.treeToValue(root, ReceivablesSearchResponseDTO.class);
            if (dto != null) {
                if (dto.itens() != null && !dto.itens().isEmpty()) {
                    return resolveArrayNode(root);
                }

                if (dto.content() != null && dto.content().itens() != null && !dto.content().itens().isEmpty()) {
                    return resolveArrayNode(root);
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

    private String normalizeBaixaBaseUrl() {
        if (!StringUtils.hasText(baixaDetailsUrl)) {
            return "https://api-v2.contaazul.com/v1/financeiro/eventos-financeiros/parcelas/baixa/{id}";
        }

        return baixaDetailsUrl.trim();
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

    // normalizeSalePrintTemplate removed — printing via sale endpoint is no longer used

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

    private String sanitizeContaAzulUri(String uri) {
        if (!StringUtils.hasText(uri)) {
            return uri;
        }

        String sanitized = uri.trim();
        // Normalize old /api/v1/ patterns to /v1/ on the v2 host
        sanitized = sanitized.replace("https://api-v2.contaazul.com/api/v1/", "https://api-v2.contaazul.com/v1/");
        sanitized = sanitized.replace("https://api.contaazul.com/api/v1/", "https://api-v2.contaazul.com/v1/");
        // Ensure any reference to the legacy host uses the api-v2 host
        sanitized = sanitized.replace("api.contaazul.com", "api-v2.contaazul.com");
        return sanitized;
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
                    hasAcquittedInstallmentInSaleNode(saleNode),
                    null,
                    null));
            }

            return sales;
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao parsear payload de vendas COMMITTED da Conta Azul.", ex);
        }
    }

    // hasAcquittedInstallmentInSaleByNumberItem removed — Sniper flow not used anymore

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

    private JsonNode readArrayNode(JsonNode node, String... paths) {
        for (String path : paths) {
            JsonNode current = node;
            for (String segment : path.split("\\.")) {
                if (current == null) {
                    break;
                }

                current = current.get(segment);
            }

            if (current != null && current.isArray()) {
                return current;
            }
        }

        return null;
    }

        @JsonIgnoreProperties(ignoreUnknown = true)
        private record ReceivablesSearchResponseDTO(
            @JsonProperty("itens") List<ReceivableItemDTO> itens,
            @JsonProperty("content") ReceivablesContentDTO content) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        private record ReceivablesContentDTO(
            @JsonProperty("itens") List<ReceivableItemDTO> itens) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        private record ReceivableItemDTO(
            @JsonProperty("id") String id,
            @JsonProperty("baixas") List<ReceivableBaixaDTO> baixas) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        private record ReceivableBaixaDTO(
            @JsonProperty("id") String id,
            @JsonProperty("id_recibo_digital") String idReciboDigital) {
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
            boolean hasAcquittedInstallment,
            String baixaId,
            String idReciboDigital) {
    }

            // SaleByNumber DTOs removed — Sniper flow retired

    private record PessoasPage(List<PessoaItem> itens, Long total) {
    }

    public record PessoaItem(
            String id,
            String nome,
            String email) {
    }

        public record ParcelaDetailDTO(
            String vendaId,
            String baixaId) {
        }

        public record BaixaDetailDTO(
            List<BaixaAttachmentDTO> anexos) {
        }

        public record BaixaAttachmentDTO(
            String id,
            String tipo,
            String url) {
        }

        private static final class ContaAzulHttpException extends RuntimeException {
            private final int statusCode;
            private final String responseBody;

            private ContaAzulHttpException(int statusCode, String responseBody) {
                super("Conta Azul retornou erro HTTP " + statusCode);
                this.statusCode = statusCode;
                this.responseBody = responseBody;
            }

            private boolean isStatus(int expectedStatus) {
                return this.statusCode == expectedStatus;
            }

            @SuppressWarnings("unused")
            private String responseBody() {
                return responseBody;
            }
        }
}