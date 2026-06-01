package br.dev.ctrls.inovareti.modules.finance.infrastructure.adapter.output;


import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Mapper de respostas relacionadas ao contexto de vendas da Conta Azul.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SalesResponseMapper {

    private final ObjectMapper objectMapper;
    private final JsonSafeReader jsonSafeReader;

    public List<ContaAzulClient.SaleItem> parseAcquittedSales(String jsonPayload) {
        if (!StringUtils.hasText(jsonPayload)) {
            return List.of();
        }

        try {
            JsonNode root = objectMapper.readTree(jsonPayload.getBytes(StandardCharsets.UTF_8));
            JsonNode entries = resolveArrayNodeFromDtoOrFallback(root);
            if (entries == null || !entries.isArray() || entries.isEmpty()) {
                return List.of();
            }

            List<ContaAzulClient.SaleItem> sales = new ArrayList<>();
            for (JsonNode node : entries) {
                String parcelaId = jsonSafeReader.readText(
                        node,
                        "id",
                        "parcela_id",
                        "parcela.id",
                        "titulo.id");

                String origem = jsonSafeReader.readText(
                        node,
                        "evento_financeiro.referencia.origem",
                        "referencia.origem",
                        "origem",
                        "source");

                String vendaNestedId = jsonSafeReader.readText(
                        node,
                        "venda.id",
                        "sale.id");

                ContaAzulClient.VendaRef venda = StringUtils.hasText(vendaNestedId)
                        ? new ContaAzulClient.VendaRef(vendaNestedId)
                        : null;

                String origemSaleId = jsonSafeReader.readText(
                        node,
                        "evento_financeiro.referencia.id",
                        "referencia.id",
                        "origem.venda_id",
                        "origem.sale_id",
                        "origem.venda.id",
                        "origem.id");

                String vendaId = jsonSafeReader.readText(
                        node,
                        "evento_financeiro.referencia.id",
                        "referencia.id",
                        "venda_id",
                        "sale_id",
                        "sale.id",
                        "venda.id");

                String descricao = jsonSafeReader.readText(
                        node,
                        "descricao",
                        "description",
                        "historico",
                        "observacao");

                String saleNumber = jsonSafeReader.readText(
                    node,
                    "numero_venda",
                    "numero",
                    "evento_financeiro.referencia.numero",
                    "referencia.numero",
                    "venda.numero",
                    "sale.number");

                String baixaId = jsonSafeReader.readText(
                        node,
                        "baixas.0.id",
                        "evento_financeiro.baixas.0.id",
                        "evento.baixas.0.id",
                        "id_baixa",
                        "baixa_id");

                String idReciboDigital = jsonSafeReader.readText(
                        node,
                        "baixas.0.id_recibo_digital",
                        "evento_financeiro.baixas.0.id_recibo_digital",
                        "evento.baixas.0.id_recibo_digital",
                        "id_recibo_digital");

                String saleId = venda != null && StringUtils.hasText(venda.id())
                        ? venda.id()
                        : (StringUtils.hasText(origemSaleId)
                                ? origemSaleId
                                : (StringUtils.hasText(vendaId) ? vendaId : null));

                String status = jsonSafeReader.readText(node, "status", "sale.status", "situacao", "estado");
                if (!isReceivableItemPaid(status)) {
                    continue;
                }

                String customerUuid = jsonSafeReader.readText(
                        node,
                        "customer.id",
                        "customer.uuid",
                        "customerId",
                        "customer_uuid",
                        "cliente.id",
                        "cliente.uuid",
                        "contato.id",
                        "pessoa.id",
                        "origem.cliente.id");

                String customerName = jsonSafeReader.readText(
                        node,
                        "customer.name",
                        "customer_name",
                        "cliente.nome",
                        "nome",
                        "contato.nome",
                        "pessoa.nome");

                sales.add(new ContaAzulClient.SaleItem(
                        saleId,
                        customerUuid,
                        customerName,
                        parcelaId,
                        origem,
                        venda,
                        origemSaleId,
                        vendaId,
                        descricao,
                        saleNumber,
                        true,
                        baixaId,
                        idReciboDigital));
            }

            return sales;
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao parsear payload de vendas da Conta Azul.", ex);
        }
    }

    public List<ContaAzulClient.SaleItem> parseCommittedSalesWithAcquittedParcels(String jsonPayload) {
        if (!StringUtils.hasText(jsonPayload)) {
            return List.of();
        }

        try {
            JsonNode root = objectMapper.readTree(jsonPayload.getBytes(StandardCharsets.UTF_8));
            JsonNode entries = jsonSafeReader.resolveArrayNode(root);
            if (entries == null || !entries.isArray() || entries.isEmpty()) {
                return List.of();
            }

            List<ContaAzulClient.SaleItem> sales = new ArrayList<>();
            for (JsonNode saleNode : entries) {
                String saleId = jsonSafeReader.readText(saleNode, "id", "sale_id", "venda.id");
                if (!StringUtils.hasText(saleId)) {
                    continue;
                }

                String customerUuid = jsonSafeReader.readText(
                        saleNode,
                        "customer.id",
                        "customer.uuid",
                        "cliente.id",
                        "cliente.uuid",
                        "person.id",
                        "pessoa.id");

                String customerName = jsonSafeReader.readText(
                        saleNode,
                        "customer.name",
                        "cliente.nome",
                        "person.nome",
                        "person.name");

                String saleNumber = jsonSafeReader.readText(
                        saleNode,
                    "numero_venda",
                        "number",
                        "numero",
                        "sale.number",
                        "venda.numero");

                sales.add(new ContaAzulClient.SaleItem(
                        saleId,
                        customerUuid,
                        customerName,
                        null,
                        "VENDA",
                        new ContaAzulClient.VendaRef(saleId),
                        saleId,
                        saleId,
                        null,
                        saleNumber,
                        hasAcquittedInstallmentInSaleNode(saleNode),
                        null,
                        null));
            }

            return sales;
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao parsear payload de vendas COMMITTED da Conta Azul.", ex);
        }
    }

    private JsonNode resolveArrayNodeFromDtoOrFallback(JsonNode root) {
        try {
            ReceivablesSearchResponseDTO dto = objectMapper.treeToValue(root, ReceivablesSearchResponseDTO.class);
            if (dto != null) {
                if (dto.itens() != null && !dto.itens().isEmpty()) {
                    return jsonSafeReader.resolveArrayNode(root);
                }

                if (dto.content() != null && dto.content().itens() != null && !dto.content().itens().isEmpty()) {
                    return jsonSafeReader.resolveArrayNode(root);
                }
            }
        } catch (IOException | IllegalArgumentException ex) {
            log.debug("Falha ao mapear DTO de parcelas (itens). Aplicando fallback por JsonNode.", ex);
        }

        return jsonSafeReader.resolveArrayNode(root);
    }

    private boolean isReceivableItemPaid(String status) {
        if (!StringUtils.hasText(status)) {
            return true;
        }

        String normalizedStatus = status.trim();
        return "ACQUITTED".equalsIgnoreCase(normalizedStatus)
                || "RECEBIDO".equalsIgnoreCase(normalizedStatus)
                || "PAGO".equalsIgnoreCase(normalizedStatus)
                || "PAID".equalsIgnoreCase(normalizedStatus)
                || "LIQUIDADO".equalsIgnoreCase(normalizedStatus);
    }

    private boolean hasAcquittedInstallmentInSaleNode(JsonNode saleNode) {
        if (saleNode == null || saleNode.isNull()) {
            return false;
        }

        JsonNode installments = saleNode.get("installments");
        if (installments != null && installments.isArray()) {
            for (JsonNode installment : installments) {
                String installmentStatus = jsonSafeReader.readText(installment, "status", "situacao", "estado");
                if ("ACQUITTED".equalsIgnoreCase(installmentStatus)) {
                    return true;
                }
            }
        }

        JsonNode parcelas = saleNode.get("parcelas");
        if (parcelas != null && parcelas.isArray()) {
            for (JsonNode parcela : parcelas) {
                String parcelaStatus = jsonSafeReader.readText(parcela, "status", "situacao", "estado");
                if ("ACQUITTED".equalsIgnoreCase(parcelaStatus)) {
                    return true;
                }
            }
        }

        return false;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ReceivablesSearchResponseDTO(
            @JsonProperty("itens") List<ReceivableItemDTO> itens,
            @JsonProperty("content") ReceivablesContentDTO content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ReceivablesContentDTO(
            @JsonProperty("itens") List<ReceivableItemDTO> itens) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ReceivableItemDTO(
            @JsonProperty("id") String id,
            @JsonProperty("baixas") List<ReceivableBaixaDTO> baixas) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ReceivableBaixaDTO(
            @JsonProperty("id") String id,
            @JsonProperty("id_recibo_digital") String idReciboDigital) {
    }
}

