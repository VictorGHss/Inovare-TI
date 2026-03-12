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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.dev.ctrls.inovareti.core.exception.NotFoundException;
import br.dev.ctrls.inovareti.domain.audit.AuditAction;
import br.dev.ctrls.inovareti.domain.audit.AuditEvent;
import br.dev.ctrls.inovareti.domain.audit.AuditLogService;
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
    private final AuditLogService auditLogService;

    /**
     * Lista todos os artigos (público para usuários logados).
     * Retorna 200 OK com a lista de artigos.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIAN', 'USER')")
    @GetMapping
    public ResponseEntity<List<ArticleResponseDTO>> listAll() {
        User user = getAuthenticatedUser();
        List<Article> articles = user.getRole() == br.dev.ctrls.inovareti.domain.user.UserRole.USER
                ? articleRepository.findAllByStatusOrderByCreatedAtDesc(ArticleStatus.PUBLISHED)
                : articleRepository.findAllByOrderByCreatedAtDesc();

        return ResponseEntity.ok(
            articles
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
        User user = getAuthenticatedUser();

        return articleRepository.findById(id)
            .map(article -> {
                boolean canReadDraft = article.getStatus() != ArticleStatus.DRAFT
                        || article.getAuthorId().equals(user.getId())
                        || user.getRole() != br.dev.ctrls.inovareti.domain.user.UserRole.USER;

                if (!canReadDraft) {
                    return ResponseEntity.notFound().build();
                }
                return ResponseEntity.ok(ArticleResponseDTO.from(article));
            })
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
            articleRepository.findPublishedByTitleContainingIgnoreCase(query.trim())
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
        User author = getAuthenticatedUser();
        ArticleStatus status = request.getStatus() != null ? request.getStatus() : ArticleStatus.PUBLISHED;

        Article article = Article.builder()
            .title(request.getTitle())
            .content(request.getContent())
            .tags(request.getTags())
            .status(status)
            .authorId(author.getId())
            .authorName(author.getName())
            .createdAt(LocalDateTime.now())
            .build();

        Article saved = articleRepository.save(article);
        AuditAction action = status == ArticleStatus.DRAFT
            ? AuditAction.KB_ARTICLE_DRAFT_CREATE
            : AuditAction.KB_ARTICLE_PUBLISH;
        auditLogService.publish(AuditEvent.of(action)
            .userId(author.getId())
            .resourceType("Article")
            .resourceId(saved.getId())
            .details("{\"title\": \"" + saved.getTitle() + "\", \"status\": \"" + saved.getStatus().name() + "\"}")
            .build());

        log.info("Article created with ID: {}", saved.getId());

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ArticleResponseDTO.from(saved));
    }

        @PutMapping("/{id}")
        @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIAN')")
        public ResponseEntity<ArticleResponseDTO> update(
            @PathVariable UUID id,
            @Valid @RequestBody ArticleRequestDTO request) {
        User editor = getAuthenticatedUser();

        Article article = articleRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Article not found with id: " + id));

        article.setTitle(request.getTitle());
        article.setContent(request.getContent());
        article.setTags(request.getTags());
        if (request.getStatus() != null) {
            article.setStatus(request.getStatus());
        }
        article.setUpdatedAt(LocalDateTime.now());

        Article saved = articleRepository.save(article);
        auditLogService.publish(AuditEvent.of(AuditAction.KB_ARTICLE_EDIT)
            .userId(editor.getId())
            .resourceType("Article")
            .resourceId(saved.getId())
            .details("{\"status\": \"" + saved.getStatus().name() + "\"}")
            .build());

        return ResponseEntity.ok(ArticleResponseDTO.from(saved));
        }

        private User getAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String userIdStr = auth.getPrincipal().toString();
        UUID userId = UUID.fromString(userIdStr);
        return userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("Authenticated user not found with id: " + userId));
        }
}
