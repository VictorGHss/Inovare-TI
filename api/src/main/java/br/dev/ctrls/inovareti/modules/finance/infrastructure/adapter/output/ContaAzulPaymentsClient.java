package br.dev.ctrls.inovareti.modules.finance.infrastructure.adapter.output;

import br.dev.ctrls.inovareti.modules.finance.application.service.ContaAzulTokenService;
import br.dev.ctrls.inovareti.modules.finance.domain.model.ContaAzulPaymentParcel;
import br.dev.ctrls.inovareti.modules.finance.domain.model.ContaAzulStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import br.dev.ctrls.inovareti.modules.finance.infrastructure.config.ContaAzulProperties;
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
 * ServiÃƒÆ’Ã‚Â§o cliente para operaÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Âµes especÃƒÆ’Ã‚Â­ficas de pagamentos/parcelas na Conta Azul.
 *
 * Responsabilidades:
 * - construir e executar requisiÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Âµes HTTP;
 * - aplicar paginaÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Â£o e fallback de URL;
 * - orquestrar retries/fallbacks de consulta quando necessÃƒÆ’Ã‚Â¡rio.
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
    private final ContaAzulProperties properties;







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

        // Normaliza endpoint para garantir formato BASE_URL + /v1/... sem prefixo legado /api.
        String uri = normalizeContaAzulUrl(properties.getPaymentsUrl())
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
     * VersÃƒÆ’Ã‚Â£o compatÃƒÆ’Ã‚Â­vel com OffsetDateTime para busca de parcelas pagas em uma janela.
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
     * Tenta URL primÃƒÆ’Ã‚Â¡ria e fallback quando aplicÃƒÆ’Ã‚Â¡vel.
     */
    public byte[] downloadReceiptPdf(String parcelaId) {
        String accessToken = contaAzulTokenService.getValidAccessToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setAccept(List.of(MediaType.APPLICATION_PDF, MediaType.APPLICATION_OCTET_STREAM));

        List<String> candidateUrls = new ArrayList<>();
        String primaryPdfUrl = normalizeReceiptPdfUrlTemplate(properties.getReceiptPdfUrlTemplate())
                .replace("{parcelaId}", parcelaId)
                .replace("{id}", parcelaId);
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
                log.warn("PDF de recibo nÃƒÆ’Ã‚Â£o encontrado para parcela {} na URL {}", parcelaId, url);
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
                        "Parcela nÃƒÆ’Ã‚Â£o encontrada ou payload invÃƒÆ’Ã‚Â¡lido na ContaAzul [parcelaId=" + parcelaId + "]"));

        if (!responseMapper.isPaidParcelStatus(parcelLookup.status())) {
            throw new IllegalStateException(
                    "Parcela retornada pela ContaAzul ainda nÃƒÆ’Ã‚Â£o estÃƒÆ’Ã‚Â¡ quitada/recebida [parcelaId=" + parcelaId
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
        if (StringUtils.hasText(properties.getParcelaByIdUrlTemplate())) {
            // Nunca adiciona "/api" manualmente; apenas normaliza e substitui placeholders.
            return normalizeContaAzulUrl(properties.getParcelaByIdUrlTemplate())
                    .replace("{parcelaId}", parcelaId)
                    .replace("{id}", parcelaId);
        }

        String basePath = "/contas-a-receber/buscar";
        String normalizedPaymentsUrl = normalizeContaAzulUrl(properties.getPaymentsUrl());
        int suffixStart = normalizedPaymentsUrl.indexOf(basePath);
        if (suffixStart > 0) {
            String prefix = normalizedPaymentsUrl.substring(0, suffixStart);
            return prefix + "/parcelas/" + parcelaId;
        }

        throw new IllegalStateException(
                "NÃƒÆ’Ã‚Â£o foi possÃƒÆ’Ã‚Â­vel resolver URL de parcela por ID. Defina app.contaazul.parcela-by-id-url-template.");
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
            String uri = normalizeContaAzulUrl(properties.getPaymentsUrl())
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
        String normalizedPaymentsUrl = normalizeContaAzulUrl(properties.getPaymentsUrl());
        int suffixStart = normalizedPaymentsUrl.indexOf(basePath);
        if (suffixStart > 0) {
            String prefix = normalizedPaymentsUrl.substring(0, suffixStart);
            return prefix + "/parcelas/" + parcelaId + "/recibo.pdf";
        }

        return null;
    }

    // Padroniza host e path da Conta Azul para impedir envio indevido de /api/v1.
    private String normalizeContaAzulUrl(String rawUrl) {
        String normalized = StringUtils.hasText(rawUrl)
                ? rawUrl.trim()
                : "https://api-v2.contaazul.com/v1/financeiro/eventos-financeiros/contas-a-receber/buscar";

        normalized = normalized.replace("https://api.contaazul.com", "https://api-v2.contaazul.com");
        // Remove barra final para reduzir risco de path divergente entre ambientes.
        normalized = normalized.replaceAll("/+$", "");
        normalized = normalized.replaceAll("(?i)/api/v1/", "/v1/");
        normalized = normalized.replaceAll("(?i)https://api-v2\\.contaazul\\.com/api/", "https://api-v2.contaazul.com/");

        // Migra automaticamente configuraÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Â£o antiga sem /eventos-financeiros e/ou sem /buscar.
        if (normalized.matches("(?i).*/v1/financeiro/contas-a-receber$")) {
            normalized = normalized.replaceAll(
                    "(?i)/v1/financeiro/contas-a-receber$",
                    "/v1/financeiro/eventos-financeiros/contas-a-receber/buscar");
        } else if (normalized.matches("(?i).*/v1/financeiro/eventos-financeiros/contas-a-receber$")) {
            normalized = normalized + "/buscar";
        }

        return normalized;
    }

    private String normalizeReceiptPdfUrlTemplate(String rawTemplate) {
        if (StringUtils.hasText(rawTemplate)) {
            return normalizeContaAzulUrl(rawTemplate);
        }

        return "https://api-v2.contaazul.com/v1/financeiro/eventos-financeiros/parcelas/{parcelaId}/recibo.pdf";
    }

    private boolean isMissingLinkIdentity(ContaAzulPaymentParcel parcel) {
        boolean missingCustomerId = !StringUtils.hasText(parcel.customerId());
        boolean missingDoctorName = !StringUtils.hasText(parcel.medicoNome())
                || "Profissional".equalsIgnoreCase(parcel.medicoNome());
        return missingCustomerId && missingDoctorName;
    }
}

