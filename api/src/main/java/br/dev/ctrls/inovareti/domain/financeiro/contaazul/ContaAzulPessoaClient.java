package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

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

@Component
@RequiredArgsConstructor
@Slf4j
public class ContaAzulPessoaClient {

    private static final String BASE_URL = "https://api-v2.contaazul.com";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ContaAzulTokenService tokenService;

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

    private HttpEntity<Void> buildRequest() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenService.getValidAccessToken());
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return new HttpEntity<>(headers);
    }

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
