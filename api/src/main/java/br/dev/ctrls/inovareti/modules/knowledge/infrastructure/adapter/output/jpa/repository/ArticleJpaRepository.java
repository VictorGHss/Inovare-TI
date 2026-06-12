package br.dev.ctrls.inovareti.modules.knowledge.infrastructure.adapter.output.jpa.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.dev.ctrls.inovareti.modules.knowledge.domain.model.Article;
import br.dev.ctrls.inovareti.modules.knowledge.domain.model.ArticleStatus;

public interface ArticleJpaRepository extends JpaRepository<Article, UUID> {
    List<Article> findAllByStatusOrderByCreatedAtDesc(ArticleStatus status);

    List<Article> findAllByOrderByCreatedAtDesc();

    /**
     * Busca artigos cujo título ou tags contém o texto informado (case-insensitive).
     * @param query o texto de busca
     * @return lista de artigos encontrados
     */
    @Query("SELECT a FROM Article a WHERE LOWER(a.title) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(a.tags) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<Article> findByTitleContainingIgnoreCase(@Param("query") String query);

    @Query("SELECT a FROM Article a WHERE a.status = br.dev.ctrls.inovareti.modules.knowledge.domain.model.ArticleStatus.PUBLISHED AND (LOWER(a.title) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(a.tags) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<Article> findPublishedByTitleContainingIgnoreCase(@Param("query") String query);

    @Query("SELECT a FROM Article a WHERE a.status = br.dev.ctrls.inovareti.modules.knowledge.domain.model.ArticleStatus.PUBLISHED AND (LOWER(a.title) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(a.content) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(a.tags) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<Article> findPublishedByTitleOrContentContainingIgnoreCase(@Param("query") String query);
}
