package br.dev.ctrls.inovareti.modules.notification.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO para solicitação de criação/atualização de FAQ da TI.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FaqTiRequestDTO {

    @NotBlank(message = "A palavra-chave é obrigatória")
    @Size(max = 80, message = "A palavra-chave deve ter no máximo 80 caracteres")
    private String palavraChave;

    @NotBlank(message = "A pergunta é obrigatória")
    private String pergunta;

    @NotBlank(message = "A resposta é obrigatória")
    private String resposta;
}
