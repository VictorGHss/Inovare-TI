package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço cliente para operações específicas de pagamentos/parcelas na Conta Azul.
 *
 * Responsabilidades:
 * - construir e executar requisições HTTP;
 * - aplicar paginação e fallback de URL;
 * - orquestrar retries/fallbacks de consulta quando necessário.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContaAzulPaymentsClient {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final int SEARCH_PAGE_SIZE = 100;
    private static final int SEARCH_MAX_PAGES = 20;

    private final RestTemplate restTemplate;
    private final ContaAzulTokenService contaAzulTokenService;
    private final ContaAzulPaymentsResponseMapper responseMapper;

    @Value("${app.contaazul.payments-url}")
    private String paymentsUrl;

    @Value("${app.contaazul.receipt-pdf-url-template}")
    private String receiptPdfUrlTemplate;

    @Value("${app.contaazul.parcela-by-id-url-template:}")
    private String parcelaByIdUrlTemplate;

    /**
     * Busca parcelas marcadas como pagas entre os instantes informados.
     * Utilizado pelo processo de polling para identificar novas baixas.
     */
    public List<ContaAzulPaymentParcel> fetchPaidParcelsSinceLastRun(
            LocalDateTime from,
            LocalDateTime to,
            int pageSize,
            int page) {
        String accessToken = contaAzulTokenService.getValidAccessToken();
        LocalDate hoje = LocalDate.now();
        LocalDate janelaVencimentoDe = hoje.minusDays(90);
        LocalDate janelaVencimentoAte = hoje.plusDays(30);

        String uri = paymentsUrl
                + "?pagina=" + page
                + "&tamanho_pagina=" + pageSize
                + "&data_vencimento_de=" + janelaVencimentoDe.format(DATE_FORMATTER)
                + "&data_vencimento_ate=" + janelaVencimentoAte.format(DATE_FORMATTER)
                + "&data_alteracao_de=" + from.format(DATETIME_FORMATTER)
                + "&data_alteracao_ate=" + to.format(DATETIME_FORMATTER)
                + "&status=" + ContaAzulStatus.RECEBIDO;

        log.debug("Chamando ContaAzul (parcelas pagas): {}", uri);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), String.class);
        return responseMapper.parseParcels(response.getBody());
    }

    /**
     * Versão compatível com OffsetDateTime para busca de parcelas pagas em uma janela.
     */
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
        return fetchPaidParcelsSinceLastRun(from.atStartOfDay(), to.atTime(23, 59, 59), pageSize, page);
    }

    /**
     * Faz o download do PDF de recibo para a parcela informada.
     * Tenta URL primária e fallback quando aplicável.
     */
    public byte[] downloadReceiptPdf(String parcelaId) {
        String accessToken = contaAzulTokenService.getValidAccessToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setAccept(List.of(MediaType.APPLICATION_PDF, MediaType.APPLICATION_OCTET_STREAM));

        List<String> candidateUrls = new ArrayList<>();
        String primaryPdfUrl = receiptPdfUrlTemplate.replace("{parcelaId}", parcelaId);
        candidateUrls.add(primaryPdfUrl);

        String fallbackPdfUrl = resolveReceiptPdfFallbackUrl(parcelaId);
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
                log.warn("PDF de recibo não encontrado para parcela {} na URL {}", parcelaId, url);
            }
        }

        if (lastNotFound != null) {
            throw lastNotFound;
        }

        return new byte[0];
    }

    /**
     * Busca os dados da parcela por ID usando o endpoint adequado.
     */
    public ContaAzulPaymentParcel fetchParcelById(String parcelaId) {
        String accessToken = contaAzulTokenService.getValidAccessToken();
        String uri = resolveParcelByIdUrl(parcelaId);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), String.class);
        log.debug("ContaAzul response body (parcela_id={}): {}", parcelaId, response.getBody());

        ContaAzulPaymentsResponseMapper.ParcelLookup parcelLookup = responseMapper.parseSingleParcel(response.getBody())
                .orElseThrow(() -> new IllegalStateException(
                        "Parcela não encontrada ou payload inválido na ContaAzul [parcelaId=" + parcelaId + "]"));

        if (!responseMapper.isPaidParcelStatus(parcelLookup.status())) {
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

    /**
     * Resolve a URL para consulta de parcela por ID.
     */
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

    /**
     * Pesquisa identidade da parcela no endpoint de contas a receber quando o
     * retorno da consulta por ID estiver incompleto.
     */
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
            Optional<ContaAzulPaymentParcel> parcelIdentity = responseMapper.findParcelIdentityByParcelaId(
                    response.getBody(),
                    parcelaId);

            if (parcelIdentity.isPresent()) {
                return parcelIdentity.get();
            }
        }

        return null;
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

    private boolean isMissingLinkIdentity(ContaAzulPaymentParcel parcel) {
        boolean missingCustomerId = !StringUtils.hasText(parcel.customerId());
        boolean missingDoctorName = !StringUtils.hasText(parcel.medicoNome())
                || "Profissional".equalsIgnoreCase(parcel.medicoNome());
        return missingCustomerId && missingDoctorName;
    }
}
