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

/**
 * Mapper de respostas do contexto de pagamentos da Conta Azul.
 *
 * Responsabilidades:
 * - parse de payloads de parcelas;
 * - extração segura de dados relevantes;
 * - regra de status de pagamento considerado quitado.
 */
@Component
@RequiredArgsConstructor
public class ContaAzulPaymentsResponseMapper {

    private final ObjectMapper objectMapper;
    private final JsonSafeReader jsonSafeReader;

    /**
     * Converte payload de listagem de parcelas em objetos de domínio.
     */
    public List<ContaAzulPaymentParcel> parseParcels(String jsonPayload) {
        if (!StringUtils.hasText(jsonPayload)) {
            return List.of();
        }

        try {
            JsonNode root = objectMapper.readTree(jsonPayload.getBytes(StandardCharsets.UTF_8));
            JsonNode entries = jsonSafeReader.resolveArrayNode(root);

            List<ContaAzulPaymentParcel> parcels = new ArrayList<>();
            for (JsonNode node : entries) {
                String parcelaId = jsonSafeReader.readText(node, "parcela_id", "id", "parcela.id");
                String customerId = jsonSafeReader.readText(node, "contaazul_customer_id", "customer.id", "cliente.id", "paciente.id");
                String doctorName = jsonSafeReader.readText(node, "customer.name", "cliente.nome", "paciente.nome", "medico.nome", "nome");
                String recipientEmail = jsonSafeReader.readText(node, "customer.email", "cliente.email", "paciente.email", "email");
                String saleNumber = jsonSafeReader.readText(
                    node,
                    "numero_venda",
                    "numero",
                    "venda.numero",
                    "evento_financeiro.referencia.numero",
                    "referencia.numero");

                if (!StringUtils.hasText(parcelaId) || !StringUtils.hasText(customerId)) {
                    continue;
                }

                parcels.add(new ContaAzulPaymentParcel(
                        parcelaId,
                        customerId,
                        StringUtils.hasText(doctorName) ? doctorName : "Profissional",
                    recipientEmail,
                    saleNumber));
            }

            return parcels;
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao parsear pagamentos do Conta Azul.", ex);
        }
    }

    /**
     * Faz parse do payload de detalhe de parcela por ID.
     */
    public Optional<ParcelLookup> parseSingleParcel(String jsonPayload) {
        if (!StringUtils.hasText(jsonPayload)) {
            return Optional.empty();
        }

        try {
            JsonNode root = objectMapper.readTree(jsonPayload.getBytes(StandardCharsets.UTF_8));
            JsonNode node = resolveObjectNode(root);
            if (node == null || node.isMissingNode() || node.isNull()) {
                return Optional.empty();
            }

            String resolvedParcelaId = jsonSafeReader.readText(node, "id", "parcela_id", "parcela.id");
            if (!StringUtils.hasText(resolvedParcelaId)) {
                return Optional.empty();
            }

            String eventId = jsonSafeReader.readText(node, "evento.id");
            String status = jsonSafeReader.readText(node, "status");
            String customerId = jsonSafeReader.readText(
                    node,
                    "contaazul_customer_id",
                    "customer.id",
                    "cliente.id",
                    "paciente.id",
                    "contato.id",
                    "pessoa.id",
                    "cliente.contaazul_id",
                    "contato.contaazul_id",
                    "pessoa.contaazul_id",
                    "baixas.0.contaazul_customer_id",
                    "baixas.0.customer.id",
                    "baixas.0.cliente.id",
                    "baixas.0.paciente.id");
            String doctorName = jsonSafeReader.readText(
                    node,
                    "customer.name",
                    "cliente.nome",
                    "paciente.nome",
                    "medico.nome",
                    "contato.nome",
                    "pessoa.nome",
                    "nome");
            String recipientEmail = jsonSafeReader.readText(
                    node,
                    "customer.email",
                    "cliente.email",
                    "paciente.email",
                    "contato.email",
                    "pessoa.email",
                    "email");

            return Optional.of(new ParcelLookup(
                    new ContaAzulPaymentParcel(
                            resolvedParcelaId,
                            customerId,
                            StringUtils.hasText(doctorName) ? doctorName : "Profissional",
                        recipientEmail,
                        jsonSafeReader.readText(
                            node,
                            "numero_venda",
                            "numero",
                            "venda.numero",
                            "evento_financeiro.referencia.numero",
                            "referencia.numero")),
                    eventId,
                    status));
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao parsear parcela da Conta Azul.", ex);
        }
    }

    /**
     * Procura uma parcela específica dentro de um payload de listagem de contas a receber.
     */
    public Optional<ContaAzulPaymentParcel> findParcelIdentityByParcelaId(String jsonPayload, String parcelaId) {
        if (!StringUtils.hasText(jsonPayload) || !StringUtils.hasText(parcelaId)) {
            return Optional.empty();
        }

        JsonNode entries = readEntries(jsonPayload);
        if (entries == null || !entries.isArray() || entries.isEmpty()) {
            return Optional.empty();
        }

        for (JsonNode node : entries) {
            String currentParcelId = jsonSafeReader.readText(node, "parcela_id", "id", "parcela.id");
            if (!parcelaId.equals(currentParcelId)) {
                continue;
            }

            String customerId = jsonSafeReader.readText(node, "contaazul_customer_id", "customer.id", "cliente.id", "paciente.id");
            String doctorName = jsonSafeReader.readText(node, "customer.name", "cliente.nome", "paciente.nome", "medico.nome", "nome");
            String recipientEmail = jsonSafeReader.readText(node, "customer.email", "cliente.email", "paciente.email", "email");
                String saleNumber = jsonSafeReader.readText(
                    node,
                    "numero_venda",
                    "numero",
                    "venda.numero",
                    "evento_financeiro.referencia.numero",
                    "referencia.numero");

            return Optional.of(new ContaAzulPaymentParcel(
                    parcelaId,
                    customerId,
                    StringUtils.hasText(doctorName) ? doctorName : "Profissional",
                    recipientEmail,
                    saleNumber));
        }

        return Optional.empty();
    }

    /**
     * Determina se o status da parcela representa pagamento/quitacão.
     */
    public boolean isPaidParcelStatus(String status) {
        return "QUITADO".equalsIgnoreCase(status) || ContaAzulStatus.RECEBIDO.equalsIgnoreCase(status);
    }

    private JsonNode readEntries(String jsonPayload) {
        if (!StringUtils.hasText(jsonPayload)) {
            return objectMapper.createArrayNode();
        }

        try {
            JsonNode root = objectMapper.readTree(jsonPayload.getBytes(StandardCharsets.UTF_8));
            return jsonSafeReader.resolveArrayNode(root);
        } catch (IOException ex) {
            return objectMapper.createArrayNode();
        }
    }

    private JsonNode resolveObjectNode(JsonNode root) {
        if (root == null || root.isNull()) {
            return null;
        }

        if (root.isObject()) {
            if (root.has("data") && root.get("data").isObject()) {
                return root.get("data");
            }
            if (root.has("item") && root.get("item").isObject()) {
                return root.get("item");
            }
            return root;
        }

        return null;
    }

    /**
     * Estrutura auxiliar para retorno de dados relevantes da parcela por ID.
     */
    public record ParcelLookup(ContaAzulPaymentParcel parcel, String eventId, String status) {
    }
}
