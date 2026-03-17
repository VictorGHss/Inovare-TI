package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

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

    @Value("${app.contaazul.sales-pdf-v1-url-template:}")
    private String salePdfV1UrlTemplate;

    public boolean hasSalesConfiguration() {
        return StringUtils.hasText(salesV2Url) && StringUtils.hasText(salePdfV1UrlTemplate);
    }

    public List<SaleItem> fetchAcquittedSales() {
        List<SaleItem> sales = new ArrayList<>();

        for (int page = 1; page <= MAX_PAGES; page++) {
            String uri = salesV2Url
                    + "?page=" + page
                    + "&size=" + PAGE_SIZE
                    + "&status=ACQUITTED";

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

    private String executeJsonGetWithRefresh(String uri) {
        String token = contaAzulTokenService.getValidAccessToken();

        try {
            return executeJsonGet(uri, token);
        } catch (HttpClientErrorException.Unauthorized ex) {
            log.warn("Token expirado ao consultar vendas. Tentando refresh.");
            String refreshedToken = contaAzulTokenService.forceRefresh();
            return executeJsonGet(uri, refreshedToken);
        }
    }

    private String executeJsonGet(String uri, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        ResponseEntity<String> response = restTemplate.exchange(
                uri,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);

        return response.getBody();
    }

    private byte[] executePdfGet(String uri, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setAccept(List.of(MediaType.APPLICATION_PDF, MediaType.APPLICATION_OCTET_STREAM));

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
                String saleId = readText(node, "id", "sale_id", "venda_id", "sale.id");
                String status = readText(node, "status", "sale.status");

                if (!StringUtils.hasText(saleId) || !"ACQUITTED".equalsIgnoreCase(status)) {
                    continue;
                }

                String customerUuid = readText(
                        node,
                        "customer.id",
                        "customer.uuid",
                        "customerId",
                        "customer_uuid",
                        "cliente.id",
                        "cliente.uuid");
                String customerName = readText(node, "customer.name", "customer_name", "cliente.nome", "nome");

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

        return objectMapper.createArrayNode();
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