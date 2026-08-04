package br.dev.ctrls.inovareti.modules.appointment.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de resposta contendo o resultado do processamento da intenção do usuário.
 * Comentários mantidos em PT-BR pelas Regras de Ouro.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IntentAnalysisResponse {

    @JsonProperty("tipo")
    private String tipo;

    @JsonProperty("termoBuscado")
    private String termoBuscado;

    @JsonProperty("medico")
    private String medico;

    @JsonProperty("especialidade")
    private String especialidade;

    @JsonProperty("fila")
    private String fila;

    @JsonProperty("rota")
    private String rota;

    @JsonProperty("acao")
    private String acao;
}
