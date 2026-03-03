package br.dev.ctrls.inovareti.domain.knowledge.dto;

import java.util.UUID;

import br.dev.ctrls.inovareti.domain.knowledge.Article;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO resumido para resultados de busca de artigos.
 * Contém apenas ID e Título para sugestões rápidas.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleSearchResultDTO {

    private UUID id;
    private String title;

    /**
     * Converte um Article para ArticleSearchResultDTO.
     */
    public static ArticleSearchResultDTO from(Article article) {
        return ArticleSearchResultDTO.builder()
                .id(article.getId())
                .title(article.getTitle())
                .build();
    }
}
