package br.dev.ctrls.inovareti.modules.notification.application.dto;

import java.time.LocalDateTime;
import br.dev.ctrls.inovareti.modules.notification.domain.model.FaqTi;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO para resposta de dados do FAQ da TI.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FaqTiResponseDTO {

    private Integer id;
    private String palavraChave;
    private String pergunta;
    private String resposta;
    private LocalDateTime createdAt;

    /**
     * Cria um DTO de resposta a partir da entidade FaqTi.
     */
    public static FaqTiResponseDTO from(FaqTi faqTi) {
        return FaqTiResponseDTO.builder()
                .id(faqTi.getId())
                .palavraChave(faqTi.getPalavraChave())
                .pergunta(faqTi.getPergunta())
                .resposta(faqTi.getResposta())
                .createdAt(faqTi.getCreatedAt())
                .build();
    }
}
