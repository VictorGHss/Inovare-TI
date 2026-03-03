package br.dev.ctrls.inovareti.domain.knowledge;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ArticleRepository extends JpaRepository<Article, UUID> {
    /**
     * Busca artigos cujo título contém o texto informado (case-insensitive).
     * @param query o texto de busca
     * @return lista de artigos encontrados
     */
    List<Article> findByTitleContainingIgnoreCase(String query);
}
