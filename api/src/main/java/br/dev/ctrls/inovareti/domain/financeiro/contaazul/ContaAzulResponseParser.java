package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Classe de compatibilidade para parser legado da Conta Azul.
 *
 * Mantida como fachada delegadora para permitir migração incremental
 * para os mappers especializados por contexto.
 */
@Component
@RequiredArgsConstructor
public class ContaAzulResponseParser {

    private final SalesResponseMapper salesResponseMapper;
    private final FinancialResponseMapper financialResponseMapper;
    private final CustomerResponseMapper customerResponseMapper;

    /**
     * Faz parse das parcelas liquidadas retornando itens de venda relevantes.
     */
    public List<ContaAzulClient.SaleItem> parseAcquittedSales(String jsonPayload) {
        return salesResponseMapper.parseAcquittedSales(jsonPayload);
    }

    /**
     * Localiza o customer UUID pelo e-mail informado.
     */
    public Optional<String> parseCustomerIdByEmail(String jsonPayload, String email) {
        return customerResponseMapper.parseCustomerIdByEmail(jsonPayload, email);
    }

    /**
     * Localiza e-mail do cliente a partir do payload de detalhe por ID.
     */
    public Optional<String> parseCustomerEmailById(String jsonPayload) {
        return customerResponseMapper.parseCustomerEmailById(jsonPayload);
    }

    /**
     * Converte payload de pessoas em itens paginados com total opcional.
     */
    public PessoasPage parsePessoasPage(String jsonPayload) {
        CustomerResponseMapper.PessoasPage result = customerResponseMapper.parsePessoasPage(jsonPayload);
        return new PessoasPage(result.itens(), result.total());
    }

    /**
     * Faz parse do endpoint de vendas committed para extrair vendas com parcelas.
     */
    public List<ContaAzulClient.SaleItem> parseCommittedSalesWithAcquittedParcels(String jsonPayload) {
        return salesResponseMapper.parseCommittedSalesWithAcquittedParcels(jsonPayload);
    }

    /**
     * Extrai vendaId/baixaId do payload de detalhe da parcela.
     */
    public Optional<ContaAzulClient.ParcelaDetailDTO> parseParcelaDetail(String jsonPayload) {
        return financialResponseMapper.parseParcelaDetail(jsonPayload);
    }

    /**
     * Extrai detalhes da baixa incluindo anexos e id_recibo_digital.
     */
    public Optional<ContaAzulClient.BaixaDetailDTO> parseBaixaDetail(String jsonPayload) {
        return financialResponseMapper.parseBaixaDetail(jsonPayload);
    }

    /**
     * Extrai o primeiro id de baixa do payload de /parcelas/{id}/baixa.
     */
    public Optional<String> parseBaixaIdByParcelaPayload(String jsonPayload) {
        return financialResponseMapper.parseBaixaIdByParcelaPayload(jsonPayload);
    }

    /**
     * Localiza URL de anexo de recibo no payload de detalhe da baixa.
     */
    public Optional<String> parseReceiptDownloadUrl(String jsonPayload) {
        return financialResponseMapper.parseReceiptDownloadUrl(jsonPayload);
    }

    /**
     * Representa uma página de pessoas retornada pelo parser.
     */
    public record PessoasPage(List<ContaAzulClient.PessoaItem> itens, Long total) {
    }
}
