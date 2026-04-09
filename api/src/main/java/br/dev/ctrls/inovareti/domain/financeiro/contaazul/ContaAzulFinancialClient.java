package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Cliente especializado para operações financeiras de baixa/recibo na Conta Azul.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContaAzulFinancialClient {

    private final ContaAzulRequestExecutor requestExecutor;
    private final FinancialResponseMapper financialResponseMapper;
    private final ContaAzulTokenService contaAzulTokenService;

    @Value("${app.contaazul.baixa-details-url}")
    private String baixaDetailsUrl;

    @Value("${app.contaazul.parcela-by-id-url-template:https://api-v2.contaazul.com/v1/financeiro/eventos-financeiros/parcelas/{id}}")
    private String parcelaByIdUrlTemplate;

    @Value("${app.contaazul.customer-by-id-v1-url-template:https://api-v2.contaazul.com/v1/pessoas/{id}}")
    private String customerByIdV1UrlTemplate;

    /**
     * Baixa o PDF do recibo da baixa financeira.
     */
    public byte[] downloadReceiptPdf(String baixaId) {
        if (!StringUtils.hasText(baixaId)) {
            throw new IllegalArgumentException("baixaId não pode ser vazio para download do recibo");
        }

        String normalizedBaixaId = baixaId.trim();

        try {
            JsonNode settlementNode = getSettlementDetails(normalizedBaixaId)
                    .orElseThrow(() -> new NoReceiptAvailableException(
                            "Detalhe da baixa não encontrado para " + normalizedBaixaId));

            String receiptUrl = financialResponseMapper.extractReceiptUrl(settlementNode)
                    .orElseThrow(() -> new NoReceiptAvailableException(
                            "Nenhum anexo de recibo encontrado para baixa " + normalizedBaixaId));

            String accessToken = contaAzulTokenService.getValidAccessToken();
            return requestExecutor.downloadFile(receiptUrl, accessToken);
        } catch (ContaAzulHttpException ex) {
            if (!ex.isStatus(401)) {
                throw ex;
            }

            log.warn("Token expirado ao baixar recibo da baixa {}. Tentando refresh explícito.", normalizedBaixaId);
            ContaAzulOAuthToken refreshed = contaAzulTokenService.forceRefreshAndReloadFromDatabase();
            JsonNode settlementNode = getSettlementDetails(normalizedBaixaId)
                    .orElseThrow(() -> new NoReceiptAvailableException(

                            "Detalhe da baixa não encontrado para " + normalizedBaixaId));
            String receiptUrl = financialResponseMapper.extractReceiptUrl(settlementNode)
                    .orElseThrow(() -> new NoReceiptAvailableException(
                            "Nenhum anexo de recibo encontrado para baixa " + normalizedBaixaId));
            return requestExecutor.downloadFile(receiptUrl, refreshed.getAccessToken());
        }
    }

    /**
     * Obtém os detalhes da baixa (settlement) no endpoint de parcelas baixadas.
     */
    public Optional<JsonNode> getSettlementDetails(String settlementId) {
        if (!StringUtils.hasText(settlementId)) {
            return Optional.empty();
        }

        String normalizedSettlementId = settlementId.trim();
        String uri = buildBaixaDetailsUri(normalizedSettlementId);

        try {
            String payload = requestExecutor.executeJsonGetWithRefresh(uri);
            return financialResponseMapper.parseSettlementNode(payload);
        } catch (ContaAzulHttpException ex) {
            if (!ex.isStatus(404)) {
                throw ex;
            }
            log.warn("Detalhe da baixa {} não encontrado no Conta Azul.", normalizedSettlementId);
            return Optional.empty();
        }
    }

    /**
     * Busca o ID da baixa associada a uma parcela.
     */
    public Optional<String> fetchBaixaIdByParcelaId(String parcelaId) {
        if (!StringUtils.hasText(parcelaId)) {
            return Optional.empty();
        }

        // Primeiro tenta pelo endpoint oficial da parcela para manter o mapeamento alinhado com a API atual.
        Optional<ContaAzulClient.ParcelaDetailDTO> parcelaDetailOpt = fetchParcelaDetail(parcelaId);
        if (parcelaDetailOpt.isPresent() && StringUtils.hasText(parcelaDetailOpt.get().baixaId())) {
            return Optional.of(parcelaDetailOpt.get().baixaId().trim());
        }

        // Fallback de compatibilidade com endpoint de baixa legado ainda aceito em algumas contas.
        String uri = "https://api-v2.contaazul.com/v1/financeiro/eventos-financeiros/parcelas/"
                + parcelaId.trim()
                + "/baixa";

        try {
            String payload = requestExecutor.executeJsonGetWithRefresh(uri);
            return financialResponseMapper.parseBaixaIdByParcelaPayload(payload);
        } catch (ContaAzulHttpException ex) {
            log.warn("Falha ao buscar baixa para parcela {}: erro HTTP ContaAzul.", parcelaId, ex);
            return Optional.empty();
        } catch (RuntimeException ex) {
            log.warn("Falha ao parsear resposta de baixa para parcela {}.", parcelaId, ex);
            return Optional.empty();
        }
    }

    /**
     * Recupera detalhe da parcela com possíveis referências de venda e baixa.
     */
    public Optional<ContaAzulClient.ParcelaDetailDTO> fetchParcelaDetail(String uuidParcela) {
        if (!StringUtils.hasText(uuidParcela)) {
            return Optional.empty();
        }

        String normalizedParcelaUuid = uuidParcela.trim();
        // Endpoint oficial para parcela financeira, usado para obter referência da venda.
        String uri = buildParcelaByIdUri(normalizedParcelaUuid);

        try {
            String payload = requestExecutor.executeJsonGetWithRefresh(uri);
            return financialResponseMapper.parseParcelaDetail(payload);
        } catch (ContaAzulHttpException ex) {
            if (!ex.isStatus(404)) {
                throw ex;
            }
            log.warn("Detalhe da parcela {} não encontrado no Conta Azul.", normalizedParcelaUuid);
            return Optional.empty();
        }
    }

    /**
     * Recupera detalhe de baixa com anexos e id de recibo digital.
     */
    public Optional<ContaAzulClient.BaixaDetailDTO> fetchBaixaDetail(String baixaId) {
        if (!StringUtils.hasText(baixaId)) {
            return Optional.empty();
        }

        String normalizedBaixaId = baixaId.trim();
        String uri = buildBaixaDetailsUri(normalizedBaixaId);

        try {
            String payload = requestExecutor.executeJsonGetWithRefresh(uri);
            return financialResponseMapper.parseBaixaDetail(payload);
        } catch (ContaAzulHttpException ex) {
            if (!ex.isStatus(404)) {
                throw ex;
            }
            log.warn("Detalhe da baixa {} não encontrado no Conta Azul.", normalizedBaixaId);
            return Optional.empty();
        }
    }

    /**
     * Busca CPF/CNPJ (documento) da pessoa no endpoint GET /v1/pessoas/{id}.
     */
    public Optional<String> fetchPersonDocumentById(String personId) {
        if (!StringUtils.hasText(personId)) {
            return Optional.empty();
        }

        String normalizedId = personId.trim();
        String uri = customerByIdV1UrlTemplate
                .replace("{id}", normalizedId)
                .replace("{customerId}", normalizedId);

        try {
            String payload = requestExecutor.executeJsonGetWithRefresh(uri);
            return financialResponseMapper.parsePessoaDocumento(payload);
        } catch (RuntimeException ex) {
            log.warn("Falha ao buscar documento da pessoa {} na Conta Azul: {}", normalizedId, ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Faz download de arquivo com chamada simples.
     */
    public byte[] downloadFile(String url) {
        return requestExecutor.downloadFile(url);
    }

    /**
     * Faz download de arquivo com token Bearer explícito.
     */
    public byte[] downloadFile(String url, String bearerToken) {
        return requestExecutor.downloadFile(url, bearerToken);
    }

    /**
     * Faz download de arquivo público sem autenticação.
     */
    public byte[] downloadPublicFile(String url) {
        return requestExecutor.downloadPublicFile(url);
    }

    private String normalizeBaixaBaseUrl() {
        if (!StringUtils.hasText(baixaDetailsUrl)) {
            return "https://api-v2.contaazul.com/v1/financeiro/eventos-financeiros/parcelas/baixa";
        }

        return baixaDetailsUrl.trim();
    }

    private String buildBaixaDetailsUri(String settlementId) {
        String baseUrl = normalizeBaixaBaseUrl();
        if (baseUrl.contains("{id}")) {
            return baseUrl.replace("{id}", settlementId);
        }

        String normalizedBase = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;

        return normalizedBase + "/" + settlementId;
    }

    private String buildParcelaByIdUri(String parcelaId) {
        String template = StringUtils.hasText(parcelaByIdUrlTemplate)
                ? parcelaByIdUrlTemplate.trim()
                : "https://api-v2.contaazul.com/v1/financeiro/eventos-financeiros/parcelas/{id}";

        return template
                .replace("{id}", parcelaId)
                .replace("{parcelaId}", parcelaId);
    }
}
