package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ContaAzulPessoaDTO(
        String id,
        String nome,
        String email,
        @JsonProperty("tipo_pessoa") String tipoPessoa,
        Boolean ativo,
        @JsonProperty("outros_contatos") List<OutroContatoDTO> outrosContatos) {

    @JsonIgnoreProperties(ignoreUnknown = true)
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
