package br.dev.ctrls.inovareti.modules.appointment.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de resposta para o cadastro de paciente via webhook Blip.
 * Comentários mantidos em PT-BR pelas Regras de Ouro.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PatientWebhookRegistrationResponse {

    @JsonProperty("status")
    private String status;

    @JsonProperty("paciente_id")
    private String pacienteId;

    @JsonProperty("mensagem")
    private String mensagem;
}
