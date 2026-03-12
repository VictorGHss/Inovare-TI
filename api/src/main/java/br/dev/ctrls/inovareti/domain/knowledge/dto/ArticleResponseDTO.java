package br.dev.ctrls.inovareti.domain.knowledge.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import br.dev.ctrls.inovareti.domain.knowledge.Article;
import br.dev.ctrls.inovareti.domain.knowledge.ArticleStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO para resposta de artigos.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleResponseDTO {

    private UUID id;
    private String title;
    private String content;
    private UUID authorId;
    private String authorName;
    private String tags;
    private ArticleStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Converte um Article para ArticleResponseDTO.
     */
    public static ArticleResponseDTO from(Article article) {
        return ArticleResponseDTO.builder()
                .id(article.getId())
                .title(article.getTitle())
                .content(article.getContent())
                .authorId(article.getAuthorId())
                .authorName(article.getAuthorName())
                .tags(article.getTags())
                .status(article.getStatus())
                .createdAt(article.getCreatedAt())
                .updatedAt(article.getUpdatedAt())
                .build();
    }
}
