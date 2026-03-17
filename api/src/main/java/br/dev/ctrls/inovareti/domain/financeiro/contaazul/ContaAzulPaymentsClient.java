package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContaAzulPaymentsClient {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final int SEARCH_PAGE_SIZE = 100;
    private static final int SEARCH_MAX_PAGES = 20;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ContaAzulTokenService contaAzulTokenService;

    @Value("${app.contaazul.payments-url}")
    private String paymentsUrl;

    @Value("${app.contaazul.receipt-pdf-url-template}")
    private String receiptPdfUrlTemplate;

    @Value("${app.contaazul.parcela-by-id-url-template:}")
    private String parcelaByIdUrlTemplate;

    public List<ContaAzulPaymentParcel> fetchPaidParcelsSinceLastRun(
            LocalDateTime from,
            LocalDateTime to,
            int pageSize,
            int page) {
        String accessToken = contaAzulTokenService.getValidAccessToken();
        LocalDate hoje = LocalDate.now();
        LocalDate janelaVencimentoDe = hoje.minusDays(90);
        LocalDate janelaVencimentoAte = hoje.plusDays(30);
        String dataAlteracaoDe = from.format(DATETIME_FORMATTER);
        String dataAlteracaoAte = to.format(DATETIME_FORMATTER);
        String dataVencimentoDe = DATE_FORMATTER.format(janelaVencimentoDe);
        String dataVencimentoAte = DATE_FORMATTER.format(janelaVencimentoAte);

        log.debug(
                "Parâmetros ContaAzul: vencimento_de={}, vencimento_ate={}, alteracao_de={}, alteracao_ate={}, status={}",
                dataVencimentoDe,
                dataVencimentoAte,
                dataAlteracaoDe,
                dataAlteracaoAte,
                ContaAzulStatus.RECEBIDO);

        String uri = paymentsUrl
                + "?pagina=" + page
                + "&tamanho_pagina=" + pageSize
                + "&data_vencimento_de=" + dataVencimentoDe
                + "&data_vencimento_ate=" + dataVencimentoAte
                + "&data_alteracao_de=" + dataAlteracaoDe
                + "&data_alteracao_ate=" + dataAlteracaoAte
                + "&status=" + ContaAzulStatus.RECEBIDO;

        log.debug("Chamando ContaAzul: {}", uri);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), String.class);
        log.debug("ContaAzul response body (polling): {}", response.getBody());
        return parseParcels(response.getBody());
    }

    public List<ContaAzulPaymentParcel> fetchPaidParcelsByWindow(
            OffsetDateTime from,
            OffsetDateTime to,
            int pageSize,
            int page) {
        return fetchPaidParcelsSinceLastRun(from.toLocalDateTime(), to.toLocalDateTime(), pageSize, page);
    }

    public List<ContaAzulPaymentParcel> fetchPaidParcelsByWindow(
            LocalDate from,
            LocalDate to,
            int pageSize,
            int page) {
        LocalDateTime dataAlteracaoDe = from.atStartOfDay();
        LocalDateTime dataAlteracaoAte = to.atTime(23, 59, 59);
        return fetchPaidParcelsSinceLastRun(dataAlteracaoDe, dataAlteracaoAte, pageSize, page);
    }

    public byte[] downloadReceiptPdf(String parcelaId) {
        String accessToken = contaAzulTokenService.getValidAccessToken();
        String primaryPdfUrl = receiptPdfUrlTemplate.replace("{parcelaId}", parcelaId);
        String fallbackPdfUrl = resolveReceiptPdfFallbackUrl(parcelaId);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setAccept(List.of(MediaType.APPLICATION_PDF, MediaType.APPLICATION_OCTET_STREAM));

        List<String> candidateUrls = new ArrayList<>();
        candidateUrls.add(primaryPdfUrl);

        if (StringUtils.hasText(fallbackPdfUrl) && !fallbackPdfUrl.equals(primaryPdfUrl)) {
            candidateUrls.add(fallbackPdfUrl);
        }

        HttpClientErrorException.NotFound lastNotFound = null;
        for (String url : candidateUrls) {
            try {
                ResponseEntity<byte[]> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
                return response.getBody() != null ? response.getBody() : new byte[0];
            } catch (HttpClientErrorException.NotFound ex) {
                lastNotFound = ex;
                log.warn("PDF de recibo não encontrado na ContaAzul para parcela {} na URL {}", parcelaId, url);
            }
        }

        if (lastNotFound != null) {
            throw lastNotFound;
        }

        return new byte[0];
    }

    public ContaAzulPaymentParcel fetchParcelById(String parcelaId) {
        String accessToken = contaAzulTokenService.getValidAccessToken();
        String uri = resolveParcelByIdUrl(parcelaId);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), String.class);
        log.debug("ContaAzul response body (parcela_id={}): {}", parcelaId, response.getBody());

        ParcelLookup parcelLookup = parseSingleParcel(response.getBody());
        if (parcelLookup == null || parcelLookup.parcel() == null) {
            throw new IllegalStateException("Parcela não encontrada ou payload inválido na ContaAzul [parcelaId=" + parcelaId + "]");
        }

        if (!isPaidParcelStatus(parcelLookup.status())) {
            throw new IllegalStateException(
                    "Parcela retornada pela ContaAzul ainda não está quitada/recebida [parcelaId=" + parcelaId
                            + ", status=" + parcelLookup.status() + ", eventoId=" + parcelLookup.eventId() + "]");
        }

        ContaAzulPaymentParcel resolvedParcel = parcelLookup.parcel();
        if (isMissingLinkIdentity(resolvedParcel)) {
            ContaAzulPaymentParcel enrichedParcel = searchParcelIdentityInReceivables(parcelaId, accessToken);
            if (enrichedParcel != null) {
                resolvedParcel = enrichedParcel;
            }
        }

        return resolvedParcel;
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

    private ParcelLookup parseSingleParcel(String jsonPayload) {
        if (jsonPayload == null || jsonPayload.isBlank()) {
            return null;
        }

        try {
            JsonNode root = objectMapper.readTree(jsonPayload.getBytes(StandardCharsets.UTF_8));
            JsonNode node = resolveObjectNode(root);
            if (node == null || node.isMissingNode() || node.isNull()) {
                return null;
            }

            String resolvedParcelaId = readText(node, "id", "parcela_id", "parcela.id");
            String eventId = readText(node, "evento.id");
            String status = readText(node, "status");
            String customerId = readText(
                    node,
                    "contaazul_customer_id",
                    "customer.id",
                    "cliente.id",
                    "paciente.id",
                    "contato.id",
                    "pessoa.id",
                    "cliente.contaazul_id",
                    "contato.contaazul_id",
                    "pessoa.contaazul_id",
                    "baixas.0.contaazul_customer_id",
                    "baixas.0.customer.id",
                    "baixas.0.cliente.id",
                    "baixas.0.paciente.id");
            String doctorName = readText(
                    node,
                    "customer.name",
                    "cliente.nome",
                    "paciente.nome",
                    "medico.nome",
                    "contato.nome",
                    "pessoa.nome",
                    "nome");
            String recipientEmail = readText(
                    node,
                    "customer.email",
                    "cliente.email",
                    "paciente.email",
                    "contato.email",
                    "pessoa.email",
                    "email");

            if (!StringUtils.hasText(resolvedParcelaId)) {
                return null;
            }

            if (doctorName == null) {
                doctorName = "Profissional";
            }

            return new ParcelLookup(
                    new ContaAzulPaymentParcel(resolvedParcelaId, customerId, doctorName, recipientEmail),
                    eventId,
                    status);
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao parsear parcela da ContaAzul.", ex);
        }
    }

    private JsonNode resolveObjectNode(JsonNode root) {
        if (root == null || root.isNull()) {
            return null;
        }

        if (root.isObject()) {
            if (root.has("data") && root.get("data").isObject()) {
                return root.get("data");
            }
            if (root.has("item") && root.get("item").isObject()) {
                return root.get("item");
            }
            return root;
        }

        return null;
    }

    private String resolveParcelByIdUrl(String parcelaId) {
        if (StringUtils.hasText(parcelaByIdUrlTemplate)) {
            return parcelaByIdUrlTemplate.replace("{parcelaId}", parcelaId);
        }

        String basePath = "/contas-a-receber/buscar";
        int suffixStart = paymentsUrl.indexOf(basePath);
        if (suffixStart > 0) {
            String prefix = paymentsUrl.substring(0, suffixStart);
            return prefix + "/parcelas/" + parcelaId;
        }

        throw new IllegalStateException(
                "Não foi possível resolver URL de parcela por ID. Defina app.contaazul.parcela-by-id-url-template.");
    }

    private ContaAzulPaymentParcel searchParcelIdentityInReceivables(String parcelaId, String accessToken) {
        LocalDate today = LocalDate.now();
        LocalDate fromDueDate = today.minusMonths(12);
        LocalDate toDueDate = today.plusMonths(1);

        for (int page = 1; page <= SEARCH_MAX_PAGES; page++) {
            String uri = paymentsUrl
                    + "?pagina=" + page
                    + "&tamanho_pagina=" + SEARCH_PAGE_SIZE
                    + "&data_vencimento_de=" + fromDueDate.format(DATE_FORMATTER)
                    + "&data_vencimento_ate=" + toDueDate.format(DATE_FORMATTER)
                    + "&status=" + ContaAzulStatus.RECEBIDO;

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));

            ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            JsonNode entries = readEntries(response.getBody());
            if (entries == null || !entries.isArray() || entries.isEmpty()) {
                return null;
            }

            for (JsonNode node : entries) {
                String currentParcelId = readText(node, "parcela_id", "id", "parcela.id");
                if (!parcelaId.equals(currentParcelId)) {
                    continue;
                }

                String customerId = readText(node, "contaazul_customer_id", "customer.id", "cliente.id", "paciente.id");
                String doctorName = readText(node, "customer.name", "cliente.nome", "paciente.nome", "medico.nome", "nome");
                String recipientEmail = readText(node, "customer.email", "cliente.email", "paciente.email", "email");

                if (!StringUtils.hasText(doctorName)) {
                    doctorName = "Profissional";
                }

                return new ContaAzulPaymentParcel(parcelaId, customerId, doctorName, recipientEmail);
            }

            if (entries.size() < SEARCH_PAGE_SIZE) {
                return null;
            }
        }

        return null;
    }

    private JsonNode readEntries(String jsonPayload) {
        if (jsonPayload == null || jsonPayload.isBlank()) {
            return objectMapper.createArrayNode();
        }

        try {
            JsonNode root = objectMapper.readTree(jsonPayload.getBytes(StandardCharsets.UTF_8));
            return resolveArrayNode(root);
        } catch (IOException ex) {
            return objectMapper.createArrayNode();
        }
    }

    private String resolveReceiptPdfFallbackUrl(String parcelaId) {
        String basePath = "/contas-a-receber/buscar";
        int suffixStart = paymentsUrl.indexOf(basePath);
        if (suffixStart > 0) {
            String prefix = paymentsUrl.substring(0, suffixStart);
            return prefix + "/parcelas/" + parcelaId + "/recibo.pdf";
        }

        return null;
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

            if (current != null && !current.isNull() && !current.asText().isBlank()) {
                return current.asText();
            }
        }

        return null;
    }

    private boolean isPaidParcelStatus(String status) {
        return "QUITADO".equalsIgnoreCase(status) || ContaAzulStatus.RECEBIDO.equalsIgnoreCase(status);
    }

    private boolean isMissingLinkIdentity(ContaAzulPaymentParcel parcel) {
        boolean missingCustomerId = !StringUtils.hasText(parcel.customerId());
        boolean missingDoctorName = !StringUtils.hasText(parcel.medicoNome()) || "Profissional".equalsIgnoreCase(parcel.medicoNome());
        return missingCustomerId && missingDoctorName;
    }

    private record ParcelLookup(
            ContaAzulPaymentParcel parcel,
            String eventId,
            String status) {
    }
}
