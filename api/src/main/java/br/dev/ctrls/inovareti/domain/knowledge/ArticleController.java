package br.dev.ctrls.inovareti.domain.knowledge;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.dev.ctrls.inovareti.domain.knowledge.dto.ArticleRequestDTO;
import br.dev.ctrls.inovareti.domain.knowledge.dto.ArticleResponseDTO;
import br.dev.ctrls.inovareti.domain.knowledge.dto.ArticleSearchResultDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Controller REST para gerenciamento de artigos da Base de Conhecimento.
 * Base path: /api/articles
 */
@Slf4j
@RestController
@RequestMapping("/api/articles")
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleRepository articleRepository;

    /**
     * Lista todos os artigos (público para usuários logados).
     * Retorna 200 OK com a lista de artigos.
     */
    @GetMapping
    public ResponseEntity<List<ArticleResponseDTO>> listAll() {
        return ResponseEntity.ok(
            articleRepository.findAll()
                .stream()
                .map(ArticleResponseDTO::from)
                .collect(Collectors.toList())
        );
    }

    /**
     * Retorna um artigo específico pelo ID (público para usuários logados).
     * Retorna 200 OK ou 404 se não encontrado.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ArticleResponseDTO> getById(@PathVariable UUID id) {
        return articleRepository.findById(id)
            .map(article -> ResponseEntity.ok(ArticleResponseDTO.from(article)))
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Busca artigos por título (text search).
     * Usado para sugestões de "Ticket Deflection" ao criar novo chamado.
     * Retorna lista resumida (ID e Title) de artigos encontrados.
     */
    @GetMapping("/search")
    public ResponseEntity<List<ArticleSearchResultDTO>> search(@RequestParam String query) {
        if (query == null || query.trim().isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        return ResponseEntity.ok(
            articleRepository.findByTitleContainingIgnoreCase(query.trim())
                .stream()
                .map(ArticleSearchResultDTO::from)
                .collect(Collectors.toList())
        );
    }

    /**
     * Cria um novo artigo (apenas ADMIN e TECHNICIAN).
     * Retorna 201 Created com os dados do artigo criado.
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIAN')")
    public ResponseEntity<ArticleResponseDTO> create(@Valid @RequestBody ArticleRequestDTO request) {
        // Obter informações do usuário autenticado
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String userName = auth.getName();
        
        // Extrair o UUID do usuário a partir do authentication
        // Nota: Aqui assumimos que o authentication possui um principal com toString() = UUID
        String principalString = auth.getPrincipal() != null ? auth.getPrincipal().toString() : null;
        UUID userId;
        
        try {
            userId = UUID.fromString(principalString);
        } catch (Exception e) {
            // Fallback: gerar UUID aleatório (em produção, isso deveria vir do user service)
            userId = UUID.randomUUID();
            log.warn("Could not parse user ID from authentication, using random UUID");
        }

        Article article = Article.builder()
            .title(request.getTitle())
            .content(request.getContent())
            .tags(request.getTags())
            .authorId(userId)
            .authorName(userName)
            .createdAt(LocalDateTime.now())
            .build();

        Article saved = articleRepository.save(article);
        log.info("Article created with ID: {}", saved.getId());

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ArticleResponseDTO.from(saved));
    }
}
