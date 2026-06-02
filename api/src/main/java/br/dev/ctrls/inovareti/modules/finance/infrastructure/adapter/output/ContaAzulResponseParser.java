package br.dev.ctrls.inovareti.modules.finance.infrastructure.adapter.output;

import br.dev.ctrls.inovareti.modules.finance.domain.model.FinancialAccountRef;
import br.dev.ctrls.inovareti.modules.finance.domain.model.ReceivableParcelRef;
import br.dev.ctrls.inovareti.modules.finance.domain.model.ContaAzulStatus;
import br.dev.ctrls.inovareti.modules.finance.domain.model.ReceivablesPageData;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
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
 * Componente utilitário e Fachada encarregada pelo parsing de respostas brutas da API da Conta Azul,
 * contendo engenharia defensiva para interpretação de payloads JSON, busca segura de nós e tratamento de fusos horários.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContaAzulResponseParser {

    private static final ZoneOffset BRASILIA_OFFSET = ZoneOffset.ofHours(-3);

    private final SalesResponseMapper salesResponseMapper;
    private final FinancialResponseMapper financialResponseMapper;
    private final CustomerResponseMapper customerResponseMapper;

    private final ObjectMapper objectMapper;
    private final JsonSafeReader jsonSafeReader;

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
     * Faz o parsing das contas financeiras vindas do JSON bruto.
     */
    public List<FinancialAccountRef> parseFinancialAccounts(String body) {
        if (body == null || body.isBlank()) {
            return List.of();
        }

        try {
            JsonNode root = objectMapper.readTree(body.getBytes(StandardCharsets.UTF_8));
            JsonNode entries = jsonSafeReader.resolveArrayNode(root);

            List<FinancialAccountRef> accounts = new ArrayList<>();
            for (JsonNode accountNode : entries) {
                String accountId = jsonSafeReader.readText(accountNode, "id", "conta_financeira_id", "contaFinanceiraId");
                if (!StringUtils.hasText(accountId)) {
                    continue;
                }

                String accountName = jsonSafeReader.readText(accountNode, "nome", "name", "descricao", "description");
                boolean active = readBooleanFromPaths(accountNode, "ativo", "active", "is_active", "isActive");
                String accountType = normalizeAccountType(jsonSafeReader.readText(accountNode, "tipo", "type", "categoria"));
                accounts.add(new FinancialAccountRef(
                        accountId.trim(),
                        StringUtils.hasText(accountName) ? accountName.trim() : accountId.trim(),
                        accountType,
                        active));
            }

            return accounts;
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Falha ao interpretar lista de contas financeiras da Conta Azul.", ex);
        }
    }

    /**
     * Faz o parsing do saldo atual de uma conta.
     */
    public BigDecimal parseAccountCurrentBalance(String body, String accountId) {
        if (body == null || body.isBlank()) {
            return BigDecimal.ZERO;
        }

        try {
            JsonNode root = objectMapper.readTree(body.getBytes(StandardCharsets.UTF_8));
            BigDecimal saldoAtual = readDecimalFromPaths(
                    root,
                    "saldo_atual",
                    "saldo_atual.valor",
                    "data.saldo_atual",
                    "data.saldo_atual.valor");

            return saldoAtual != null ? saldoAtual : BigDecimal.ZERO;
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Falha ao interpretar saldo da conta financeira " + accountId + ".", ex);
        }
    }

    /**
     * Faz o parsing dos valores líquidos de baixas de uma parcela.
     */
    public List<BigDecimal> parseParcelaBaixasValorLiquido(String body, String parcelaId) {
        if (body == null || body.isBlank()) {
            return List.of();
        }

        try {
            JsonNode root = objectMapper.readTree(body.getBytes(StandardCharsets.UTF_8));
            JsonNode baixasNode = jsonSafeReader.readArrayNode(
                    root,
                    "baixas",
                    "evento.baixas",
                    "evento_financeiro.baixas",
                    "data.baixas",
                    "content.baixas");

            if (baixasNode == null || !baixasNode.isArray() || baixasNode.isEmpty()) {
                return List.of();
            }

            List<BigDecimal> valoresLiquidos = new ArrayList<>();
            for (JsonNode baixaNode : baixasNode) {
                BigDecimal baixaValorLiquido = readDecimalFromPaths(
                        baixaNode,
                        "composicao_valor.valor_liquido",
                        "composicaoValor.valorLiquido",
                        "valor_composicao.valor_liquido",
                        "valorComposicao.valorLiquido",
                        "valor_liquido",
                        "valorLiquido");

                if (baixaValorLiquido != null) {
                    valoresLiquidos.add(baixaValorLiquido);
                }
            }

            return valoresLiquidos;
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Falha ao interpretar detalhe de baixas da parcela " + parcelaId + ".", ex);
        }
    }

    /**
     * Faz o parsing da página de contas a receber.
     */
    public ReceivablesPageData parseReceivablesPage(String jsonPayload) {
        if (!StringUtils.hasText(jsonPayload)) {
            return new ReceivablesPageData(List.of(), null);
        }

        try {
            JsonNode root = objectMapper.readTree(jsonPayload.getBytes(StandardCharsets.UTF_8));
            JsonNode entries = jsonSafeReader.resolveArrayNode(root);

            List<ReceivableParcelRef> parcels = new ArrayList<>();
            OffsetDateTime latestUpdate = parseApiDateToBrasiliaOffsetDateTime(jsonSafeReader.readText(
                    root,
                    "atualizado_em",
                    "updated_at",
                    "ultima_atualizacao",
                    "data_atualizacao"));

            for (JsonNode parcelNode : entries) {
                String parcelaId = jsonSafeReader.readText(parcelNode, "parcela_id", "id", "parcela.id");
                if (!StringUtils.hasText(parcelaId)) {
                    continue;
                }

                String commercialNumber = jsonSafeReader.readText(
                        parcelNode,
                        "numero",
                        "numero_venda",
                        "venda.numero",
                        "evento_financeiro.referencia.numero",
                        "referencia.numero");

                String referenceCode = jsonSafeReader.readText(
                    parcelNode,
                    "codigo_referencia",
                    "codigoReferencia",
                    "referencia.codigo",
                    "evento_financeiro.referencia.codigo");

                String displayIdentifier = firstNonBlank(commercialNumber, referenceCode);
                String descricaoParcela = firstNonBlank(
                    jsonSafeReader.readText(
                        parcelNode,
                        "descricao",
                        "description",
                        "referencia.descricao",
                        "evento_financeiro.referencia.descricao"),
                    displayIdentifier,
                    parcelaId);
                BigDecimal valorBruto = readDecimalFromPaths(
                    parcelNode,
                    "total",
                    "valor",
                    "valor_total",
                    "valorTotal",
                    "valor_bruto",
                    "valorBruto");

                log.info("Parcela encontrada: ID={}, Descrição={}, Valor Bruto={}", parcelaId, descricaoParcela, valorBruto);

                parcels.add(new ReceivableParcelRef(parcelaId.trim(), displayIdentifier));

                OffsetDateTime parcelUpdatedAt = parseApiDateToBrasiliaOffsetDateTime(jsonSafeReader.readText(
                        parcelNode,
                        "data_alteracao",
                        "dataAlteracao",
                        "updated_at",
                        "atualizado_em",
                        "evento.data_alteracao",
                        "evento_financeiro.data_alteracao"));

                if (parcelUpdatedAt != null
                        && (latestUpdate == null || parcelUpdatedAt.toInstant().isAfter(latestUpdate.toInstant()))) {
                    latestUpdate = parcelUpdatedAt;
                }
            }

            return new ReceivablesPageData(parcels, latestUpdate);
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Falha ao interpretar payload de parcelas recebidas da Conta Azul.", ex);
        }
    }

    /**
     * Extrai o decimal total a partir do status e payload JSON bruto.
     */
    public BigDecimal extractTotalDecimal(String jsonPayload, String status) {
        if (jsonPayload == null || jsonPayload.isBlank()) {
            return BigDecimal.ZERO;
        }

        try {
            JsonNode root = objectMapper.readTree(jsonPayload.getBytes(StandardCharsets.UTF_8));

            String totalPath;
            if (null == status) {
                throw new IllegalStateException("Status não suportado para cálculo do resumo: " + status);
            } else switch (status) {
                case ContaAzulStatus.RECEBIDO -> totalPath = "totais.pago.valor";
                case ContaAzulStatus.EM_ABERTO -> totalPath = "totais.aberto.valor";
                default -> throw new IllegalStateException("Status não suportado para cálculo do resumo: " + status);
            }

            return readDecimalFromPaths(root, totalPath);
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Falha ao calcular resumo financeiro da Conta Azul.", ex);
        }
    }

    public OffsetDateTime parseApiDateToBrasiliaOffsetDateTime(String rawDate) {
        if (!StringUtils.hasText(rawDate)) {
            return null;
        }

        String trimmed = rawDate.trim();

        try {
            if (trimmed.endsWith("Z") || trimmed.endsWith("z")) {
                return Instant.parse(trimmed).atOffset(BRASILIA_OFFSET);
            }
        } catch (DateTimeParseException ignored) {
        }

        try {
            return OffsetDateTime.parse(trimmed).withOffsetSameInstant(BRASILIA_OFFSET);
        } catch (DateTimeParseException ignored) {
        }

        try {
            return LocalDateTime.parse(trimmed).atOffset(BRASILIA_OFFSET);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private boolean readBooleanFromPaths(JsonNode node, String... paths) {
        for (String path : paths) {
            JsonNode valueNode = readNode(node, path);
            if (valueNode == null || valueNode.isNull()) {
                continue;
            }

            if (valueNode.isBoolean()) {
                return valueNode.booleanValue();
            }

            if (valueNode.isTextual()) {
                String raw = valueNode.asText().trim();
                if ("true".equalsIgnoreCase(raw)) {
                    return true;
                }
                if ("false".equalsIgnoreCase(raw)) {
                    return false;
                }
            }
        }
        return false;
    }

    private JsonNode readNode(JsonNode node, String path) {
        JsonNode current = node;
        for (String segment : path.split("\\.")) {
            if (current == null) {
                return null;
            }
            current = current.get(segment);
        }
        return current;
    }

    private BigDecimal readDecimalFromPaths(JsonNode node, String... paths) {
        for (String path : paths) {
            JsonNode valueNode = readNode(node, path);
            if (valueNode == null || valueNode.isNull()) {
                continue;
            }

            BigDecimal parsed = parseDecimalValue(valueNode);
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    private BigDecimal parseDecimalValue(JsonNode valueNode) {
        if (valueNode.isNumber()) {
            return valueNode.decimalValue();
        }

        if (!valueNode.isTextual()) {
            return null;
        }

        String raw = valueNode.asText().trim();
        if (raw.isBlank()) {
            return null;
        }

        String normalized = raw.replace("R$", "").replace(" ", "");
        if (normalized.contains(",") && normalized.contains(".")) {
            normalized = normalized.replace(".", "").replace(",", ".");
        } else if (normalized.contains(",")) {
            normalized = normalized.replace(",", ".");
        }

        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String normalizeAccountType(String rawType) {
        if (!StringUtils.hasText(rawType)) {
            return "";
        }

        return rawType.trim().toUpperCase()
                .replace('-', '_')
                .replace(' ', '_');
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    /**
     * Representa uma página de pessoas retornada pelo parser.
     */
    public record PessoasPage(List<ContaAzulClient.PessoaItem> itens, Long total) {
    }
}

