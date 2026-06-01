package br.dev.ctrls.inovareti.modules.finance.infrastructure.adapter.output;


import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;

/**
 * Fachada da integraÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Â£o Conta Azul.
 *
 * Esta classe mantÃƒÆ’Ã‚Â©m a API pÃƒÆ’Ã‚Âºblica original para reduzir impacto no restante
 * do sistema, delegando responsabilidades para clients especializados.
 */
@Component
@RequiredArgsConstructor
public class ContaAzulClient {

    private final ContaAzulSalesClient salesClient;
    private final ContaAzulFinancialClient financialClient;
    private final ContaAzulCustomerClient customerClient;

    public boolean hasSalesConfiguration() {
        return salesClient.hasSalesConfiguration();
    }

    public List<SaleItem> fetchAcquittedSales() {
        return salesClient.fetchAcquittedSales();
    }

    public List<SaleItem> fetchAcquittedSales(String dataVencimentoDe, String dataVencimentoAte) {
        return salesClient.fetchAcquittedSales(dataVencimentoDe, dataVencimentoAte);
    }

    public byte[] downloadReceiptPdf(String baixaId) {
        return financialClient.downloadReceiptPdf(baixaId);
    }

    public Optional<String> fetchBaixaIdByParcelaId(String parcelaId) {
        return financialClient.fetchBaixaIdByParcelaId(parcelaId);
    }

    public Optional<String> findCustomerIdByEmail(String email) {
        return customerClient.findCustomerIdByEmail(email);
    }

    public Optional<SaleItem> findAcquittedSaleById(String saleId) {
        return salesClient.findAcquittedSaleById(saleId);
    }

    public Optional<String> findCustomerEmailById(String customerId) {
        return customerClient.findCustomerEmailById(customerId);
    }

    public Optional<String> fetchPersonDocumentById(String personId) {
        return financialClient.fetchPersonDocumentById(personId);
    }

    public List<PessoaItem> fetchAllPessoas() {
        return customerClient.fetchAllPessoas();
    }

    public List<SaleItem> fetchCommittedSalesWithAcquittedParcels() {
        return salesClient.fetchCommittedSalesWithAcquittedParcels();
    }

    public Optional<ParcelaDetailDTO> fetchParcelaDetail(String uuidParcela) {
        return financialClient.fetchParcelaDetail(uuidParcela);
    }

    public Optional<BaixaDetailDTO> fetchBaixaDetail(String baixaId) {
        return financialClient.fetchBaixaDetail(baixaId);
    }

    public Optional<JsonNode> getSettlementDetails(String settlementId) {
        return financialClient.getSettlementDetails(settlementId);
    }

    public byte[] downloadFile(String url) {
        return financialClient.downloadFile(url);
    }

    public byte[] downloadFile(String url, String bearerToken) {
        return financialClient.downloadFile(url, bearerToken);
    }

    public byte[] downloadPublicFile(String url) {
        return financialClient.downloadPublicFile(url);
    }

    /** ReferÃƒÆ’Ã‚Âªncia simplificada a uma venda (ID). */
    public record VendaRef(String id) {
    }

    /**
     * DTO leve representando uma parcela/venda relevante para processamento.
     */
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

    /**
     * DTO simplificado de pessoa retornado pela API.
     */
    public record PessoaItem(
            String id,
            String nome,
            String email) {
    }

    /**
     * DTO de detalhe da parcela (conta a receber), com referÃƒÆ’Ã‚Âªncias ÃƒÆ’Ã‚Âºteis para
     * localizar venda e baixa associadas.
     */
    public record ParcelaDetailDTO(
            String saleId,
            String baixaId) {
    }

    /**
     * DTO de detalhe de baixa com lista de anexos e id de recibo digital global.
     */
    public record BaixaDetailDTO(
            List<BaixaAttachmentDTO> anexos,
            String idReciboDigital) {
    }

    /**
     * DTO de anexo da baixa.
     */
    public record BaixaAttachmentDTO(
            String id,
            String tipo,
            String url) {
    }
}

