package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContaAzulPaymentsClient {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ContaAzulTokenService contaAzulTokenService;

    @Value("${app.contaazul.payments-url}")
    private String paymentsUrl;

    @Value("${app.contaazul.receipt-pdf-url-template}")
    private String receiptPdfUrlTemplate;

    public List<ContaAzulPaymentParcel> fetchPaidParcelsFromLastSixHours() {
        LocalDate hoje = LocalDate.now();
        return fetchPaidParcelsByWindow(hoje.minusDays(30), hoje, 50, 1);
    }

    public List<ContaAzulPaymentParcel> fetchPaidParcelsByWindow(
            OffsetDateTime from,
            OffsetDateTime to,
            int pageSize,
            int page) {
        return fetchPaidParcelsByWindow(from.toLocalDate(), to.toLocalDate(), pageSize, page);
    }

    public List<ContaAzulPaymentParcel> fetchPaidParcelsByWindow(
            LocalDate from,
            LocalDate to,
            int pageSize,
            int page) {
        String accessToken = contaAzulTokenService.getValidAccessToken();
        LocalDate hoje = LocalDate.now();
        LocalDate janelaVencimentoDe = hoje.minusDays(90);
        LocalDate janelaVencimentoAte = hoje.plusDays(30);

        log.debug(
                "Parâmetros ContaAzul: vencimento_de={}, vencimento_ate={}, pagamento_de={}, pagamento_ate={}, status={}",
                janelaVencimentoDe,
                janelaVencimentoAte,
                from,
                to,
                "PAGO");

        String uri = UriComponentsBuilder.fromUriString(paymentsUrl)
            .queryParam("data_vencimento_de", DATE_FORMATTER.format(janelaVencimentoDe))
            .queryParam("data_vencimento_ate", DATE_FORMATTER.format(janelaVencimentoAte))
                .queryParam("data_pagamento_de", DATE_FORMATTER.format(from))
                .queryParam("data_pagamento_ate", DATE_FORMATTER.format(to))
                .queryParam("status", "PAGO")
                .queryParam("tamanho_pagina", pageSize)
                .queryParam("pagina", page)
                .build()
                .encode()
                .toUriString();

        log.debug("Chamando ContaAzul: {}", uri);

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
