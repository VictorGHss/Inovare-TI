package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ContaAzulPaymentsClient {

    private static final DateTimeFormatter WINDOW_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ContaAzulTokenService contaAzulTokenService;

    @Value("${app.contaazul.payments-url}")
    private String paymentsUrl;

    @Value("${app.contaazul.receipt-pdf-url-template}")
    private String receiptPdfUrlTemplate;

    public List<ContaAzulPaymentParcel> fetchPaidParcelsFromLastSixHours() {
        String accessToken = contaAzulTokenService.getValidAccessToken();

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime from = now.minusHours(6);

        String uri = UriComponentsBuilder.fromUriString(paymentsUrl)
                .queryParam("data_pagamento_de", WINDOW_FORMATTER.format(from))
                .queryParam("data_pagamento_ate", WINDOW_FORMATTER.format(now))
                .queryParam("status", "PAGO")
                .build(true)
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), String.class);
        return parseParcels(response.getBody());
    }

    public byte[] downloadReceiptPdf(String parcelaId) {
        String accessToken = contaAzulTokenService.getValidAccessToken();
        String pdfUrl = receiptPdfUrlTemplate.replace("{parcelaId}", parcelaId);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setAccept(List.of(MediaType.APPLICATION_PDF, MediaType.APPLICATION_OCTET_STREAM));

        ResponseEntity<byte[]> response = restTemplate.exchange(pdfUrl, HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
        return response.getBody() != null ? response.getBody() : new byte[0];
    }

    private List<ContaAzulPaymentParcel> parseParcels(String jsonPayload) {
        if (jsonPayload == null || jsonPayload.isBlank()) {
            return List.of();
        }

        try {
            JsonNode root = objectMapper.readTree(jsonPayload.getBytes(StandardCharsets.UTF_8));
            JsonNode entries = resolveArrayNode(root);

            List<ContaAzulPaymentParcel> parcels = new ArrayList<>();
            for (JsonNode node : entries) {
                String parcelaId = readText(node, "parcela_id", "id", "parcela.id");
                String customerId = readText(node, "contaazul_customer_id", "customer.id", "cliente.id", "paciente.id");
                String doctorName = readText(node, "customer.name", "cliente.nome", "paciente.nome", "medico.nome", "nome");
                String recipientEmail = readText(node, "customer.email", "cliente.email", "paciente.email", "email");

                if (parcelaId == null || customerId == null) {
                    continue;
                }

                if (doctorName == null) {
                    doctorName = "Profissional";
                }

                parcels.add(new ContaAzulPaymentParcel(parcelaId, customerId, doctorName, recipientEmail));
            }

            return parcels;
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao parsear pagamentos do ContaAzul.", ex);
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

    private String readText(JsonNode node, String... paths) {
        for (String path : paths) {
            JsonNode current = node;
            String[] segments = path.split("\\.");
            for (String segment : segments) {
                if (current == null) {
                    break;
                }
                current = current.get(segment);
            }

            if (current != null && !current.isNull() && !current.asText().isBlank()) {
                return current.asText();
            }
        }

        return null;
    }
}
