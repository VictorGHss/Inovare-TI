package br.dev.ctrls.inovareti.modules.appointment.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * DTO de resposta contendo o resultado do processamento da intenção do usuário.
 * Suporta respostas de tipo "RESULTADO_UNICO", "MULTIPLOS_RESULTADOS", "TRIGGER_ITSM" e "NENHUM_RESULTADO".
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

    @JsonProperty("linkWa")
    private String linkWa;

    @JsonProperty("acao")
    private String acao;

    @JsonProperty("opcoes")
    private List<DoctorOption> opcoes;

    @JsonProperty("opcoesFormatadas")
    private String opcoesFormatadas;

    /**
     * Opção individual de candidato utilizada para desambiguação em "MULTIPLOS_RESULTADOS".
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DoctorOption {

        @JsonProperty("medico")
        private String medico;

        @JsonProperty("especialidade")
        private String especialidade;

        @JsonProperty("fila")
        private String fila;

        @JsonProperty("rota")
        private String rota;

        @JsonProperty("linkWa")
        private String linkWa;
    }
}
