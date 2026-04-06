package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

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

    /**
     * Baixa o PDF do recibo da baixa financeira.
     */
    public byte[] downloadReceiptPdf(String baixaId) {
        if (!StringUtils.hasText(baixaId)) {
            throw new IllegalArgumentException("baixaId não pode ser vazio para download do recibo");
        }

        String normalizedBaixaId = baixaId.trim();
        String uri = "https://api-v2.contaazul.com/v1/financeiro/eventos-financeiros/parcelas/baixa/" + normalizedBaixaId;

        try {
            String payload = requestExecutor.executeJsonGetWithRefresh(uri);
            String receiptUrl = financialResponseMapper.parseReceiptDownloadUrl(payload)
                    .orElseThrow(() -> new NoReceiptAvailableException(
                            "Nenhum anexo de recibo encontrado para baixa " + normalizedBaixaId));
            return requestExecutor.downloadFile(receiptUrl);
        } catch (ContaAzulHttpException ex) {
            if (!ex.isStatus(401)) {
                throw ex;
            }

            log.warn("Token expirado ao baixar recibo da baixa {}. Tentando refresh explícito.", normalizedBaixaId);
            ContaAzulOAuthToken refreshed = contaAzulTokenService.forceRefreshAndReloadFromDatabase();
            String payload = requestExecutor.executeJsonGetResponse(uri, refreshed).body();
                String receiptUrl = financialResponseMapper.parseReceiptDownloadUrl(payload)
                    .orElseThrow(() -> new NoReceiptAvailableException(
                            "Nenhum anexo de recibo encontrado para baixa " + normalizedBaixaId));
            return requestExecutor.downloadFile(receiptUrl, refreshed.getAccessToken());
        }
    }

    /**
     * Busca o ID da baixa associada a uma parcela.
     */
    public Optional<String> fetchBaixaIdByParcelaId(String parcelaId) {
        if (!StringUtils.hasText(parcelaId)) {
            return Optional.empty();
        }

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
        String uri = "https://api-v2.contaazul.com/v1/financeiro/contas-a-receber/" + normalizedParcelaUuid;

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
        String uri = normalizeBaixaBaseUrl().replace("{id}", normalizedBaixaId);

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
            return "https://api-v2.contaazul.com/v1/financeiro/eventos-financeiros/parcelas/baixa/{id}";
        }

        return baixaDetailsUrl.trim();
    }
}
