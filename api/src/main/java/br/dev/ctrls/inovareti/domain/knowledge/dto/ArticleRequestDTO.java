package br.dev.ctrls.inovareti.domain.knowledge.dto;

import br.dev.ctrls.inovareti.domain.knowledge.ArticleStatus;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO para criação/atualização de artigos.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleRequestDTO {

    @NotBlank
    private String title;

    @NotBlank
    private String content;

    private String tags;

    private ArticleStatus status;
}
