package br.dev.ctrls.inovareti.modules.knowledge.domain.port.output;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import br.dev.ctrls.inovareti.modules.knowledge.domain.model.Article;
import br.dev.ctrls.inovareti.modules.knowledge.domain.model.ArticleStatus;

/**
 * Porta de saída para operações de persistência e consulta da entidade Article.
 */
public interface ArticleRepositoryPort {
    List<Article> findAllByStatusOrderByCreatedAtDesc(ArticleStatus status);
    List<Article> findAllByOrderByCreatedAtDesc();
    Optional<Article> findById(UUID id);
    Article save(Article article);
    List<Article> findByTitleContainingIgnoreCase(String query);
    List<Article> findPublishedByTitleContainingIgnoreCase(String query);
    List<Article> findPublishedByTitleOrContentContainingIgnoreCase(String query);
}
