package br.dev.ctrls.inovareti.modules.finance.infrastructure.adapter.output;

import br.dev.ctrls.inovareti.modules.finance.application.service.ContaAzulTokenService;
import br.dev.ctrls.inovareti.modules.finance.application.dto.ContaAzulPessoaDTO;

import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Cliente para operações relacionadas a pessoas (médicos/pacientes) na Conta Azul.
 *
 * Oferece buscas por UUID ou ID legado e mapeia o payload para `ContaAzulPessoaDTO`.
 * Utiliza `ContaAzulTokenService` para autenticação nas chamadas REST.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ContaAzulPessoaClient {

    private static final String BASE_URL = "https://api-v2.contaazul.com";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ContaAzulTokenService tokenService;

    /**
     * Busca uma pessoa na Conta Azul por UUID.
     *
     * @param pessoaUuid UUID da pessoa na Conta Azul
     * @return DTO com informações da pessoa, se encontrada
     */
    public Optional<ContaAzulPessoaDTO> findById(String pessoaUuid) {
        try {
            String url = BASE_URL + "/v1/pessoas/" + pessoaUuid;
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, buildRequest(), String.class);
            return parsePessoa(response.getBody());
        } catch (RestClientException e) {
            log.warn("Pessoa não encontrada (uuid={}): {}", pessoaUuid, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Busca uma pessoa na Conta Azul por ID legado (rota de compatibilidade).
     *
     * @param idLegado identificador legado da pessoa
     * @return DTO com informações da pessoa, se encontrada
     */
    public Optional<ContaAzulPessoaDTO> findByLegacyId(String idLegado) {
        try {
            String url = BASE_URL + "/v1/pessoas/legado/" + idLegado;
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, buildRequest(), String.class);
            return parsePessoa(response.getBody());
        } catch (RestClientException e) {
            log.warn("Pessoa não encontrada (legacyId={}): {}", idLegado, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Constrói `HttpEntity` com cabeçalhos de autorização e `Accept` JSON
     * a partir do token provido por `ContaAzulTokenService`.
     */
    private HttpEntity<Void> buildRequest() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenService.getValidAccessToken());
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return new HttpEntity<>(headers);
    }

    /**
     * Faz o parse do corpo de resposta JSON e converte em `ContaAzulPessoaDTO`.
     * Retorna `Optional.empty()` quando o corpo é vazio ou o parse falha.
     */
    private Optional<ContaAzulPessoaDTO> parsePessoa(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return Optional.empty();
        }

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode payload = unwrapPayload(root);
            if (payload == null || payload.isNull() || payload.isMissingNode()) {
                return Optional.empty();
            }

            ContaAzulPessoaDTO pessoa = objectMapper.treeToValue(payload, ContaAzulPessoaDTO.class);
            return Optional.ofNullable(pessoa);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            log.warn("Falha ao parsear payload de pessoa da Conta Azul: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Desembrulha camadas de envelope JSON comuns da Conta Azul (por exemplo
     * objetos `data` ou `item`) e retorna o `JsonNode` que representa o payload
     * efetivo usado para mapeamento.
     */
    private JsonNode unwrapPayload(JsonNode root) {
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
}

