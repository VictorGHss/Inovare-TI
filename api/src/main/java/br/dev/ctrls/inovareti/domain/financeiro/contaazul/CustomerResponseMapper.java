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
 * Mapper de respostas relacionadas ao contexto de clientes/pessoas da Conta Azul.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerResponseMapper {

    private final ObjectMapper objectMapper;
    private final JsonSafeReader jsonSafeReader;

    public Optional<String> parseCustomerIdByEmail(String jsonPayload, String email) {
        if (!StringUtils.hasText(jsonPayload)) {
            return Optional.empty();
        }

        try {
            JsonNode root = objectMapper.readTree(jsonPayload.getBytes(StandardCharsets.UTF_8));
            String normalizedEmail = email.trim();

            if (root.isObject()) {
                String directEmail = jsonSafeReader.readText(root, "email", "data.email", "emails.0.address", "emails.0.email");
                String directId = jsonSafeReader.readText(root, "id", "uuid", "data.id", "data.uuid");
                if (StringUtils.hasText(directEmail)
                        && StringUtils.hasText(directId)
                        && normalizedEmail.equalsIgnoreCase(directEmail.trim())) {
                    return Optional.of(directId);
                }
            }

            JsonNode entries = jsonSafeReader.resolveArrayNode(root);
            if (entries == null || !entries.isArray() || entries.isEmpty()) {
                return Optional.empty();
            }

            for (JsonNode node : entries) {
                String nodeEmail = jsonSafeReader.readText(node, "email", "emails.0.address", "emails.0.email");
                if (StringUtils.hasText(nodeEmail) && normalizedEmail.equalsIgnoreCase(nodeEmail.trim())) {
                    String customerId = jsonSafeReader.readText(node, "id", "uuid", "customer.id", "customer.uuid");
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

    public Optional<String> parseCustomerEmailById(String jsonPayload) {
        if (!StringUtils.hasText(jsonPayload)) {
            return Optional.empty();
        }

        try {
            JsonNode root = objectMapper.readTree(jsonPayload.getBytes(StandardCharsets.UTF_8));
            String email = jsonSafeReader.readText(
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

    public PessoasPage parsePessoasPage(String jsonPayload) {
        if (!StringUtils.hasText(jsonPayload)) {
            return new PessoasPage(List.of(), null);
        }

        try {
            JsonNode root = objectMapper.readTree(jsonPayload.getBytes(StandardCharsets.UTF_8));
            Long total = jsonSafeReader.readLong(root, "total", "content.total", "paginacao.total", "meta.total");

            JsonNode entries = jsonSafeReader.resolveArrayNode(root);
            if (entries == null || !entries.isArray() || entries.isEmpty()) {
                return new PessoasPage(List.of(), total);
            }

            List<ContaAzulClient.PessoaItem> itens = new ArrayList<>();
            for (JsonNode node : entries) {
                String id = jsonSafeReader.readText(node, "id", "uuid", "pessoa.id");
                String nome = jsonSafeReader.readText(node, "nome", "name", "razao_social", "fantasia");
                String email = jsonSafeReader.readText(node, "email", "emails.0.address", "emails.0.email");

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

    public record PessoasPage(List<ContaAzulClient.PessoaItem> itens, Long total) {
    }
}
