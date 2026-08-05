package br.dev.ctrls.inovareti.modules.appointment.application.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de requisição para a análise de intenção do bot Blip.
 * Suporta mensagem bruta e campos contextuais para seleções por índice numérico.
 * Comentários mantidos em PT-BR pelas Regras de Ouro.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntentAnalysisRequest {

    @JsonAlias({"message", "text", "query", "input"})
    @JsonProperty("mensagem")
    private String mensagem;

    @JsonProperty("termoAnterior")
    private String termoAnterior;

    @JsonProperty("especialidade")
    private String especialidade;
}
