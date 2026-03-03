package br.dev.ctrls.inovareti.domain.knowledge;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ArticleRepository extends JpaRepository<Article, UUID> {
    /**
     * Busca artigos cujo título ou tags contém o texto informado (case-insensitive).
     * @param query o texto de busca
     * @return lista de artigos encontrados
     */
    @Query("SELECT a FROM Article a WHERE LOWER(a.title) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(a.tags) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<Article> findByTitleContainingIgnoreCase(@Param("query") String query);
}
