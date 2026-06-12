package br.dev.ctrls.inovareti.modules.knowledge.infrastructure.adapter.output;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import br.dev.ctrls.inovareti.modules.knowledge.domain.model.Article;
import br.dev.ctrls.inovareti.modules.knowledge.domain.model.ArticleStatus;
import br.dev.ctrls.inovareti.modules.knowledge.domain.port.output.ArticleRepositoryPort;
import br.dev.ctrls.inovareti.modules.knowledge.infrastructure.adapter.output.jpa.repository.ArticleJpaRepository;

/**
 * Adaptador de saída que implementa a porta ArticleRepositoryPort delegando as chamadas
 * para o repositório Spring Data JPA (ArticleJpaRepository).
 */
@Component
@RequiredArgsConstructor
public class ArticleRepositoryAdapter implements ArticleRepositoryPort {

    private final ArticleJpaRepository repository;

    @Override
    public List<Article> findAllByStatusOrderByCreatedAtDesc(ArticleStatus status) {
        return repository.findAllByStatusOrderByCreatedAtDesc(status);
    }

    @Override
    public List<Article> findAllByOrderByCreatedAtDesc() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    @Override
    public Optional<Article> findById(UUID id) {
        return repository.findById(id);
    }

    @Override
    public Article save(Article article) {
        return repository.save(article);
    }

    @Override
    public List<Article> findByTitleContainingIgnoreCase(String query) {
        return repository.findByTitleContainingIgnoreCase(query);
    }

    @Override
    public List<Article> findPublishedByTitleContainingIgnoreCase(String query) {
        return repository.findPublishedByTitleContainingIgnoreCase(query);
    }

    @Override
    public List<Article> findPublishedByTitleOrContentContainingIgnoreCase(String query) {
        return repository.findPublishedByTitleOrContentContainingIgnoreCase(query);
    }
}
