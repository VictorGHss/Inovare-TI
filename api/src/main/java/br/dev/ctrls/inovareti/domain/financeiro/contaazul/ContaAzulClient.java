package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

import java.io.IOException;
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

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ContaAzulTokenService contaAzulTokenService;

    @Value("${app.contaazul.sales-v2-url:}")
    private String salesV2Url;

    @Value("${app.contaazul.payments-url:}")
    private String receivableEventsSearchUrl;

    @Value("${app.contaazul.sales-pdf-v1-url-template:}")
    private String salePdfV1UrlTemplate;

    @Value("${app.contaazul.customers-v1-url:https://api-v2.contaazul.com/v1/pessoas}")
    private String customersV1Url;

    @Value("${app.contaazul.customer-by-id-v1-url-template:https://api-v2.contaazul.com/v1/customers/{id}}")
    private String customerByIdV1UrlTemplate;

    public boolean hasSalesConfiguration() {
        return StringUtils.hasText(receivableEventsSearchUrl) && StringUtils.hasText(salePdfV1UrlTemplate);
    }

    /**
     * Consulta eventos financeiros (contas a receber) e extrai o sale_id (venda_id) para download de PDF.
     */
    public List<SaleItem> fetchAcquittedSales() {
        List<SaleItem> sales = new ArrayList<>();

        for (int page = 1; page <= MAX_PAGES; page++) {
            String uri = receivableEventsSearchUrl
                    + "?pagina=" + page
                    + "&tamanho_pagina=" + PAGE_SIZE
                    + "&status=RECEBIDO";

            log.info("Solicitando lista de vendas ao Conta Azul: {}", uri);
            log.debug("Consultando vendas na Conta Azul. pagina={}, uri={}", page, uri);

            String payload = executeJsonGetWithRefresh(uri);
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
        String uri = salePdfV1UrlTemplate.replace("{id}", saleId).replace("{saleId}", saleId);
        String token = contaAzulTokenService.getValidAccessToken();

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

        for (int page = 1; page <= MAX_PAGES; page++) {
            String uri = receivableEventsSearchUrl
                + "?pagina=" + page
                + "&tamanho_pagina=" + PAGE_SIZE
                + "&status=RECEBIDO";

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
     * Busca o e-mail do cliente pelo ID na Conta Azul usando /v1/customers/{id}.
     */
    public Optional<String> findCustomerEmailById(String customerId) {
        if (!StringUtils.hasText(customerId)) {
            return Optional.empty();
        }

        String uri = customerByIdV1UrlTemplate
                .replace("{id}", customerId.trim())
                .replace("{customerId}", customerId.trim());

        try {
            String payload = executeJsonGetWithRefresh(uri);
            return parseCustomerEmailById(payload);
        } catch (RuntimeException ex) {
            log.warn("Falha ao consultar e-mail do cliente Conta Azul (id={}): {}", customerId, ex.getMessage());
            return Optional.empty();
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
            JsonNode entries = resolveArrayNode(root);
            if (entries == null || !entries.isArray() || entries.isEmpty()) {
                return List.of();
            }

            List<SaleItem> sales = new ArrayList<>();
            for (JsonNode node : entries) {
                String saleId = readText(
                        node,
                        "venda_id",
                        "sale_id",
                        "sale.id",
                        "venda.id",
                        "origem.venda_id",
                        "origem.sale_id",
                        "origem.venda.id",
                        "origem.id");
                String status = readText(node, "status", "sale.status", "situacao", "estado");

                if (!StringUtils.hasText(saleId) || !isReceivableItemPaid(status)) {
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

                sales.add(new SaleItem(saleId, customerUuid, customerName));
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

    public record SaleItem(
            String saleId,
            String customerUuid,
            String customerName) {
    }
}