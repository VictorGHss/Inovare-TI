package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
/**
 * Representação parcial de uma Pessoa na Conta Azul.
 *
 * Esta DTO mapeia apenas os campos necessários pela aplicação (id, nome, e-mail
 * e contatos secundários). Inclui utilitário `resolveEmail()` que retorna o e-mail
 * principal quando disponível ou procura em `outrosContatos` como fallback.
 */
public record ContaAzulPessoaDTO(
        String id,
        String nome,
        String email,
        @JsonProperty("tipo_pessoa") String tipoPessoa,
        Boolean ativo,
        @JsonProperty("outros_contatos") List<OutroContatoDTO> outrosContatos) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    /**
     * Contato alternativo listado na resposta da Conta Azul (nome + e-mail).
     */
    public record OutroContatoDTO(String nome, String email) {
    }

    public Optional<String> resolveEmail() {
        if (email != null && !email.isBlank()) {
            return Optional.of(email);
        }

        if (outrosContatos == null) {
            return Optional.empty();
        }

        return outrosContatos.stream()
                .map(OutroContatoDTO::email)
                .filter(candidate -> candidate != null && !candidate.isBlank())
                .findFirst();
    }
}
