package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Mapper de respostas relacionadas ao contexto financeiro da Conta Azul.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FinancialResponseMapper {

    private final ObjectMapper objectMapper;
    private final JsonSafeReader jsonSafeReader;

    public Optional<ContaAzulClient.ParcelaDetailDTO> parseParcelaDetail(String jsonPayload) {
        if (!StringUtils.hasText(jsonPayload)) {
            return Optional.empty();
        }

        try {
            JsonNode root = objectMapper.readTree(jsonPayload.getBytes(StandardCharsets.UTF_8));

            String origem = jsonSafeReader.readText(
                    root,
                    "evento.referencia.origem",
                    "evento_financeiro.referencia.origem",
                    "referencia.origem");

            String saleId = null;
            if ("VENDA".equalsIgnoreCase(origem)) {
                saleId = jsonSafeReader.readText(
                        root,
                        "evento.referencia.id",
                        "evento_financeiro.referencia.id",
                        "referencia.id");
            }

            if (!StringUtils.hasText(saleId)) {
                saleId = jsonSafeReader.readText(
                        root,
                        "venda_id",
                        "sale_id",
                        "venda.id",
                        "sale.id",
                        "origem.venda_id",
                        "origem.sale_id",
                        "origem.venda.id");
            }

            String baixaId = jsonSafeReader.readText(
                    root,
                    "evento.baixa.id",
                    "evento.baixa_id",
                    "evento.id_baixa",
                    "baixa.id",
                    "baixa_id",
                    "id_baixa",
                    "baixas.0.id",
                    "evento.baixas.0.id",
                    "itens.0.id_baixa",
                    "itens.0.baixa.id",
                    "itens.0.baixado_em");

            String normalizedSaleId = Optional.ofNullable(saleId)
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .orElse(null);

            String normalizedBaixaId = Optional.ofNullable(baixaId)
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .orElse(null);

            if (!StringUtils.hasText(normalizedSaleId) && !StringUtils.hasText(normalizedBaixaId)) {
                return Optional.empty();
            }

            return Optional.of(new ContaAzulClient.ParcelaDetailDTO(normalizedSaleId, normalizedBaixaId));
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao parsear payload de detalhe da parcela da Conta Azul.", ex);
        }
    }

    public Optional<ContaAzulClient.BaixaDetailDTO> parseBaixaDetail(String jsonPayload) {
        if (!StringUtils.hasText(jsonPayload)) {
            return Optional.empty();
        }

        try {
            JsonNode root = objectMapper.readTree(jsonPayload.getBytes(StandardCharsets.UTF_8));
            String idReciboDigitalGlobal = jsonSafeReader.readText(
                    root,
                    "id_recibo_digital",
                    "evento.id_recibo_digital",
                    "idReciboDigital",
                    "recibo.id_recibo_digital",
                    "recibo.id");

            JsonNode anexosNode = jsonSafeReader.readArrayNode(root, "anexos", "evento.anexos", "data.anexos", "content.anexos");

            if (anexosNode == null || !anexosNode.isArray() || anexosNode.isEmpty()) {
                return Optional.of(new ContaAzulClient.BaixaDetailDTO(List.of(), idReciboDigitalGlobal));
            }

            List<ContaAzulClient.BaixaAttachmentDTO> anexos = new ArrayList<>();
            for (JsonNode anexoNode : anexosNode) {
                String id = jsonSafeReader.readText(anexoNode, "id", "anexo_id");
                String tipo = jsonSafeReader.readText(anexoNode, "tipo", "type", "categoria");
                String url = jsonSafeReader.readText(anexoNode, "url", "link", "download_url", "arquivo.url");
                anexos.add(new ContaAzulClient.BaixaAttachmentDTO(id, tipo, url));
            }

            return Optional.of(new ContaAzulClient.BaixaDetailDTO(anexos, idReciboDigitalGlobal));
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao parsear payload de detalhe da baixa da Conta Azul.", ex);
        }
    }

    public Optional<String> parseBaixaIdByParcelaPayload(String jsonPayload) {
        if (!StringUtils.hasText(jsonPayload)) {
            return Optional.empty();
        }

        try {
            JsonNode root = objectMapper.readTree(jsonPayload.getBytes(StandardCharsets.UTF_8));
            if (root.isArray() && root.size() > 0) {
                String baixaId = jsonSafeReader.readText(root.get(0), "id", "baixa_id");
                return StringUtils.hasText(baixaId) ? Optional.of(baixaId) : Optional.empty();
            }
            if (root.isObject()) {
                String baixaId = jsonSafeReader.readText(root, "id", "baixa_id");
                return StringUtils.hasText(baixaId) ? Optional.of(baixaId) : Optional.empty();
            }
            return Optional.empty();
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao parsear payload de baixa por parcela da Conta Azul.", ex);
        }
    }

    public Optional<String> parseReceiptDownloadUrl(String jsonPayload) {
        if (!StringUtils.hasText(jsonPayload)) {
            return Optional.empty();
        }

        try {
            JsonNode root = objectMapper.readTree(jsonPayload.getBytes(StandardCharsets.UTF_8));
            return extractReceiptUrl(root);
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao parsear payload de anexos da baixa da Conta Azul.", ex);
        }
    }

    public Optional<String> parsePessoaDocumento(String jsonPayload) {
        if (!StringUtils.hasText(jsonPayload)) {
            return Optional.empty();
        }

        try {
            JsonNode root = objectMapper.readTree(jsonPayload.getBytes(StandardCharsets.UTF_8));
            String documento = jsonSafeReader.readText(
                    root,
                    "documento",
                    "cpf_cnpj",
                    "cpfCnpj",
                    "numero_documento",
                    "documento.numero",
                    "pessoa.documento",
                    "pessoa.cpf_cnpj",
                    "pessoa.cpfCnpj",
                    "cliente.documento",
                    "cliente.cpf_cnpj",
                    "data.cpf_cnpj",
                    "data.cpfCnpj",
                    "document",
                    "person.documento",
                    "data.documento");

            return StringUtils.hasText(documento) ? Optional.of(documento.trim()) : Optional.empty();
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao parsear documento da pessoa da Conta Azul.", ex);
        }
    }

    /**
     * Faz parse genérico do payload de detalhe da baixa e retorna o JsonNode raiz.
     */
    public Optional<JsonNode> parseSettlementNode(String jsonPayload) {
        if (!StringUtils.hasText(jsonPayload)) {
            return Optional.empty();
        }

        try {
            return Optional.of(objectMapper.readTree(jsonPayload.getBytes(StandardCharsets.UTF_8)));
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao parsear payload de detalhe de baixa da Conta Azul.", ex);
        }
    }

    /**
     * Extrai a URL do recibo digital a partir dos anexos da baixa.
     *
     * Regra de negócio: considerar somente anexos com tipo RECIBO ou RECIBO_DIGITAL.
     */
    public Optional<String> extractReceiptUrl(JsonNode settlementNode) {
        JsonNode anexosNode = resolveAnexosNode(settlementNode);
        if (anexosNode == null || !anexosNode.isArray() || anexosNode.isEmpty()) {
            if (log.isDebugEnabled()) {
                log.debug("Settlement sem anexos disponíveis para recibo. JSON completo: {}",
                        settlementNode != null ? settlementNode.toString() : "null");
            }
            return Optional.empty();
        }

        for (JsonNode anexoNode : anexosNode) {
            String tipo = jsonSafeReader.readText(anexoNode, "tipo", "type", "categoria");
            String url = jsonSafeReader.readText(anexoNode, "url", "link", "download_url", "arquivo.url");
            if (StringUtils.hasText(tipo)
                    && ("RECIBO_DIGITAL".equalsIgnoreCase(tipo) || "RECIBO".equalsIgnoreCase(tipo))
                    && StringUtils.hasText(url)) {
                return Optional.of(url.trim());
            }
        }

        return Optional.empty();
    }

    /**
     * Indica se o array de anexos está ausente ou vazio no detalhe da baixa.
     */
    public boolean isSettlementAttachmentsEmpty(JsonNode settlementNode) {
        JsonNode anexosNode = resolveAnexosNode(settlementNode);
        return anexosNode == null || !anexosNode.isArray() || anexosNode.isEmpty();
    }

    private JsonNode resolveAnexosNode(JsonNode settlementNode) {
        if (settlementNode == null || settlementNode.isNull()) {
            return null;
        }

        return jsonSafeReader.readArrayNode(
                settlementNode,
                "anexos",
                "evento.anexos",
                "data.anexos",
                "content.anexos");
    }
}
