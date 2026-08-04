package br.dev.ctrls.inovareti.modules.appointment.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de requisição para cadastro de paciente via webhook Blip.
 * Comentários mantidos em PT-BR pelas Regras de Ouro.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientWebhookRegistrationRequest {

    @NotBlank
    @JsonProperty("cpf")
    private String cpf;

    @NotBlank
    @JsonProperty("nome")
    private String nome;

    @NotBlank
    @JsonProperty("nascimento")
    private String nascimento;
}
