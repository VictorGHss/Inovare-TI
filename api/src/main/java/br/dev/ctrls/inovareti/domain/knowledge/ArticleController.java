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

import br.dev.ctrls.inovareti.core.exception.NotFoundException;
import br.dev.ctrls.inovareti.domain.knowledge.dto.ArticleRequestDTO;
import br.dev.ctrls.inovareti.domain.knowledge.dto.ArticleResponseDTO;
import br.dev.ctrls.inovareti.domain.knowledge.dto.ArticleSearchResultDTO;
import br.dev.ctrls.inovareti.domain.user.User;
import br.dev.ctrls.inovareti.domain.user.UserRepository;
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
    private final UserRepository userRepository;

    /**
     * Lista todos os artigos (público para usuários logados).
     * Retorna 200 OK com a lista de artigos.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIAN', 'USER')")
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
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIAN', 'USER')")
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
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIAN', 'USER')")
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
        // Extract user ID from authentication principal as UUID
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String userIdStr = auth.getPrincipal().toString();
        UUID userId = UUID.fromString(userIdStr);
        
        // Fetch user details to get the author name
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Authenticated user not found with id: " + userId));

        Article article = Article.builder()
            .title(request.getTitle())
            .content(request.getContent())
            .tags(request.getTags())
            .authorId(userId)
            .authorName(author.getName())
            .createdAt(LocalDateTime.now())
            .build();

        Article saved = articleRepository.save(article);
        log.info("Article created with ID: {}", saved.getId());

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ArticleResponseDTO.from(saved));
    }
}
