package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Parser especializado para payloads da Conta Azul.
 *
 * Responsável por toda manipulação de JsonNode, tratamento de nulos e
 * heurísticas de leitura de caminhos alternativos no JSON.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContaAzulResponseParser {

    private final ObjectMapper objectMapper;

    /**
     * Faz parse das parcelas liquidadas retornando itens de venda relevantes.
     */
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
                String parcelaId = readText(
                        node,
                        "id",
                        "parcela_id",
                        "parcela.id",
                        "titulo.id");

                String origem = readText(
                        node,
                        "evento_financeiro.referencia.origem",
                        "referencia.origem",
                        "origem",
                        "source");

                String vendaNestedId = readText(
                        node,
                        "venda.id",
                        "sale.id");

                ContaAzulClient.VendaRef venda = StringUtils.hasText(vendaNestedId)
                        ? new ContaAzulClient.VendaRef(vendaNestedId)
                        : null;

                String origemSaleId = readText(
                        node,
                        "evento_financeiro.referencia.id",
                        "referencia.id",
                        "origem.venda_id",
                        "origem.sale_id",
                        "origem.venda.id",
                        "origem.id");

                String vendaId = readText(
                        node,
                        "evento_financeiro.referencia.id",
                        "referencia.id",
                        "venda_id",
                        "sale_id",
                        "sale.id",
                        "venda.id");

                String descricao = readText(
                        node,
                        "descricao",
                        "description",
                        "historico",
                        "observacao");

                String baixaId = readText(
                        node,
                        "baixas.0.id",
                        "evento_financeiro.baixas.0.id",
                        "evento.baixas.0.id",
                        "id_baixa",
                        "baixa_id");

                String idReciboDigital = readText(
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

                String status = readText(node, "status", "sale.status", "situacao", "estado");

                if (!isReceivableItemPaid(status)) {
                    continue;
                }

                String customerUuid = readText(
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

                String customerName = readText(
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
                        null,
                        true,
                        baixaId,
                        idReciboDigital));
            }

            return sales;
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao parsear payload de vendas da Conta Azul.", ex);
        }
    }

    /**
     * Localiza o customer UUID pelo e-mail informado.
     */
    public Optional<String> parseCustomerIdByEmail(String jsonPayload, String email) {
        if (!StringUtils.hasText(jsonPayload)) {
            return Optional.empty();
        }

        try {
            JsonNode root = objectMapper.readTree(jsonPayload.getBytes(StandardCharsets.UTF_8));
            String normalizedEmail = email.trim();

            if (root.isObject()) {
                String directEmail = readText(root, "email", "data.email", "emails.0.address", "emails.0.email");
                String directId = readText(root, "id", "uuid", "data.id", "data.uuid");
                if (StringUtils.hasText(directEmail)
                        && StringUtils.hasText(directId)
                        && normalizedEmail.equalsIgnoreCase(directEmail.trim())) {
                    return Optional.of(directId);
                }
            }

            JsonNode entries = resolveArrayNode(root);
            if (entries == null || !entries.isArray()) {
                return Optional.empty();
            }

            if (entries.isEmpty()) {
                return Optional.empty();
            }

            for (JsonNode node : entries) {
                String nodeEmail = readText(node, "email", "emails.0.address", "emails.0.email");
                if (StringUtils.hasText(nodeEmail) && normalizedEmail.equalsIgnoreCase(nodeEmail.trim())) {
                    String customerId = readText(node, "id", "uuid", "customer.id", "customer.uuid");
                    if (StringUtils.hasText(customerId)) {
                        return Optional.of(customerId);
                    }
                }
            }
        } catch (IOException ex) {
            log.warn("Falha ao parsear retorno de cliente Conta Azul por e-mail {}.", email, ex);
        }

        return Optional.empty();
    }

    /**
     * Localiza e-mail do cliente a partir do payload de detalhe por ID.
     */
    public Optional<String> parseCustomerEmailById(String jsonPayload) {
        if (!StringUtils.hasText(jsonPayload)) {
            return Optional.empty();
        }

        try {
            JsonNode root = objectMapper.readTree(jsonPayload.getBytes(StandardCharsets.UTF_8));
            String email = readText(
                    root,
                    "email",
                    "data.email",
                    "customer.email",
                    "person.email",
                    "emails.0.address",
                    "emails.0.email",
                    "contacts.0.email");

            return StringUtils.hasText(email) ? Optional.of(email) : Optional.empty();
        } catch (IOException ex) {
            log.warn("Falha ao parsear retorno de e-mail do cliente na Conta Azul.", ex);
            return Optional.empty();
        }
    }

    /**
     * Converte payload de pessoas em itens paginados com total opcional.
     */
    public PessoasPage parsePessoasPage(String jsonPayload) {
        if (!StringUtils.hasText(jsonPayload)) {
            return new PessoasPage(List.of(), null);
        }

        try {
            JsonNode root = objectMapper.readTree(jsonPayload.getBytes(StandardCharsets.UTF_8));
            Long total = readLong(root, "total", "content.total", "paginacao.total", "meta.total");

            JsonNode entries = resolveArrayNode(root);
            if (entries == null || !entries.isArray() || entries.isEmpty()) {
                return new PessoasPage(List.of(), total);
            }

            List<ContaAzulClient.PessoaItem> itens = new ArrayList<>();
            for (JsonNode node : entries) {
                String id = readText(node, "id", "uuid", "pessoa.id");
                String nome = readText(node, "nome", "name", "razao_social", "fantasia");
                String email = readText(node, "email", "emails.0.address", "emails.0.email");

                if (!StringUtils.hasText(id)) {
                    continue;
                }

                itens.add(new ContaAzulClient.PessoaItem(id, nome, email));
            }

            return new PessoasPage(itens, total);
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao parsear payload de pessoas da Conta Azul.", ex);
        }
    }

    /**
     * Faz parse do endpoint de vendas committed para extrair vendas com parcelas.
     */
    public List<ContaAzulClient.SaleItem> parseCommittedSalesWithAcquittedParcels(String jsonPayload) {
        if (!StringUtils.hasText(jsonPayload)) {
            return List.of();
        }

        try {
            JsonNode root = objectMapper.readTree(jsonPayload.getBytes(StandardCharsets.UTF_8));
            JsonNode entries = resolveArrayNode(root);
            if (entries == null || !entries.isArray() || entries.isEmpty()) {
                return List.of();
            }

            List<ContaAzulClient.SaleItem> sales = new ArrayList<>();
            for (JsonNode saleNode : entries) {
                String saleId = readText(saleNode, "id", "sale_id", "venda.id");
                if (!StringUtils.hasText(saleId)) {
                    continue;
                }

                String customerUuid = readText(
                        saleNode,
                        "customer.id",
                        "customer.uuid",
                        "cliente.id",
                        "cliente.uuid",
                        "person.id",
                        "pessoa.id");

                String customerName = readText(
                        saleNode,
                        "customer.name",
                        "cliente.nome",
                        "person.nome",
                        "person.name");

                String saleNumber = readText(
                        saleNode,
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

    /**
     * Extrai vendaId/baixaId do payload de detalhe da parcela.
     */
    public Optional<ContaAzulClient.ParcelaDetailDTO> parseParcelaDetail(String jsonPayload) {
        if (!StringUtils.hasText(jsonPayload)) {
            return Optional.empty();
        }

        try {
            JsonNode root = objectMapper.readTree(jsonPayload.getBytes(StandardCharsets.UTF_8));

            String origem = readText(
                    root,
                    "evento.referencia.origem",
                    "evento_financeiro.referencia.origem",
                    "referencia.origem");

            String saleId = null;
            if ("VENDA".equalsIgnoreCase(origem)) {
                saleId = readText(
                        root,
                        "evento.referencia.id",
                        "evento_financeiro.referencia.id",
                        "referencia.id");
            }

            if (!StringUtils.hasText(saleId)) {
                saleId = readText(
                        root,
                        "venda_id",
                        "sale_id",
                        "venda.id",
                        "sale.id",
                        "origem.venda_id",
                        "origem.sale_id",
                        "origem.venda.id");
            }

            String baixaId = readText(
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

    /**
     * Extrai detalhes da baixa incluindo anexos e id_recibo_digital.
     */
    public Optional<ContaAzulClient.BaixaDetailDTO> parseBaixaDetail(String jsonPayload) {
        if (!StringUtils.hasText(jsonPayload)) {
            return Optional.empty();
        }

        try {
            JsonNode root = objectMapper.readTree(jsonPayload.getBytes(StandardCharsets.UTF_8));
            String idReciboDigitalGlobal = readText(
                    root,
                    "id_recibo_digital",
                    "evento.id_recibo_digital",
                    "idReciboDigital",
                    "recibo.id_recibo_digital",
                    "recibo.id");

            JsonNode anexosNode = readArrayNode(root, "anexos", "evento.anexos", "data.anexos", "content.anexos");

            if (anexosNode == null || !anexosNode.isArray() || anexosNode.isEmpty()) {
                return Optional.of(new ContaAzulClient.BaixaDetailDTO(List.of(), idReciboDigitalGlobal));
            }

            List<ContaAzulClient.BaixaAttachmentDTO> anexos = new ArrayList<>();
            for (JsonNode anexoNode : anexosNode) {
                String id = readText(anexoNode, "id", "anexo_id");
                String tipo = readText(anexoNode, "tipo", "type", "categoria");
                String url = readText(anexoNode, "url", "link", "download_url", "arquivo.url");
                anexos.add(new ContaAzulClient.BaixaAttachmentDTO(id, tipo, url));
            }

            return Optional.of(new ContaAzulClient.BaixaDetailDTO(anexos, idReciboDigitalGlobal));
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao parsear payload de detalhe da baixa da Conta Azul.", ex);
        }
    }

    /**
     * Extrai o primeiro id de baixa do payload de /parcelas/{id}/baixa.
     */
    public Optional<String> parseBaixaIdByParcelaPayload(String jsonPayload) {
        if (!StringUtils.hasText(jsonPayload)) {
            return Optional.empty();
        }

        try {
            JsonNode root = objectMapper.readTree(jsonPayload.getBytes(StandardCharsets.UTF_8));
            if (root.isArray() && root.size() > 0) {
                String baixaId = readText(root.get(0), "id", "baixa_id");
                return StringUtils.hasText(baixaId) ? Optional.of(baixaId) : Optional.empty();
            }
            if (root.isObject()) {
                String baixaId = readText(root, "id", "baixa_id");
                return StringUtils.hasText(baixaId) ? Optional.of(baixaId) : Optional.empty();
            }
            return Optional.empty();
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao parsear payload de baixa por parcela da Conta Azul.", ex);
        }
    }

    /**
     * Localiza URL de anexo de recibo no payload de detalhe da baixa.
     */
    public Optional<String> parseReceiptDownloadUrl(String jsonPayload) {
        if (!StringUtils.hasText(jsonPayload)) {
            return Optional.empty();
        }

        try {
            JsonNode root = objectMapper.readTree(jsonPayload.getBytes(StandardCharsets.UTF_8));
            JsonNode anexosNode = readArrayNode(root, "anexos", "evento.anexos", "data.anexos", "content.anexos");
            if (anexosNode == null || !anexosNode.isArray()) {
                return Optional.empty();
            }

            for (JsonNode anexoNode : anexosNode) {
                String tipo = readText(anexoNode, "tipo", "type", "categoria");
                String url = readText(anexoNode, "url", "link", "download_url", "arquivo.url");
                if (StringUtils.hasText(tipo)
                        && ("RECIBO_DIGITAL".equalsIgnoreCase(tipo) || "RECIBO".equalsIgnoreCase(tipo))
                        && StringUtils.hasText(url)) {
                    return Optional.of(url);
                }
            }
            return Optional.empty();
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao parsear payload de anexos da baixa da Conta Azul.", ex);
        }
    }

    private JsonNode resolveArrayNode(JsonNode root) {
        if (root == null || root.isNull()) {
            return objectMapper.createArrayNode();
        }

        if (root.isArray()) {
            return root;
        }

        if (root.has("data") && root.get("data").isArray()) {
            return root.get("data");
        }

        if (root.has("itens") && root.get("itens").isArray()) {
            return root.get("itens");
        }

        if (root.has("items") && root.get("items").isArray()) {
            return root.get("items");
        }

        if (root.has("content") && root.get("content").isArray()) {
            return root.get("content");
        }

        if (root.has("content") && root.get("content").isObject()) {
            JsonNode content = root.get("content");

            if (content.has("items") && content.get("items").isArray()) {
                return content.get("items");
            }

            if (content.has("data") && content.get("data").isArray()) {
                return content.get("data");
            }

            if (content.has("itens") && content.get("itens").isArray()) {
                return content.get("itens");
            }
        }

        return objectMapper.createArrayNode();
    }

    private JsonNode resolveArrayNodeFromDtoOrFallback(JsonNode root) {
        try {
            ReceivablesSearchResponseDTO dto = objectMapper.treeToValue(root, ReceivablesSearchResponseDTO.class);
            if (dto != null) {
                if (dto.itens() != null && !dto.itens().isEmpty()) {
                    return resolveArrayNode(root);
                }

                if (dto.content() != null && dto.content().itens() != null && !dto.content().itens().isEmpty()) {
                    return resolveArrayNode(root);
                }
            }
        } catch (IOException | IllegalArgumentException ex) {
            log.debug("Falha ao mapear DTO de parcelas (itens). Aplicando fallback por JsonNode.", ex);
        }

        return resolveArrayNode(root);
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
                String installmentStatus = readText(installment, "status", "situacao", "estado");
                if ("ACQUITTED".equalsIgnoreCase(installmentStatus)) {
                    return true;
                }
            }
        }

        JsonNode parcelas = saleNode.get("parcelas");
        if (parcelas != null && parcelas.isArray()) {
            for (JsonNode parcela : parcelas) {
                String parcelaStatus = readText(parcela, "status", "situacao", "estado");
                if ("ACQUITTED".equalsIgnoreCase(parcelaStatus)) {
                    return true;
                }
            }
        }

        return false;
    }

    private String readText(JsonNode node, String... paths) {
        for (String path : paths) {
            JsonNode current = node;
            for (String segment : path.split("\\.")) {
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

            if (current != null && !current.isNull()) {
                String value = current.asText();
                if (StringUtils.hasText(value)) {
                    return value;
                }
            }
        }

        return null;
    }

    private Long readLong(JsonNode node, String... paths) {
        String value = readText(node, paths);
        if (!StringUtils.hasText(value)) {
            return null;
        }

        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private JsonNode readArrayNode(JsonNode node, String... paths) {
        for (String path : paths) {
            JsonNode current = node;
            for (String segment : path.split("\\.")) {
                if (current == null) {
                    break;
                }

                current = current.get(segment);
            }

            if (current != null && current.isArray()) {
                return current;
            }
        }

        return null;
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

    /**
     * Representa uma página de pessoas retornada pelo parser.
     */
    public record PessoasPage(List<ContaAzulClient.PessoaItem> itens, Long total) {
    }
}
