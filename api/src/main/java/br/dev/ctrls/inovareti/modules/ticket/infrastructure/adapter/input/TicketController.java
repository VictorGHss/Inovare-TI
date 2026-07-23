package br.dev.ctrls.inovareti.modules.ticket.infrastructure.adapter.input;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import br.dev.ctrls.inovareti.core.shared.domain.model.exception.NotFoundException;
import br.dev.ctrls.inovareti.infrastructure.shared.storage.LocalFileStorageService;
import br.dev.ctrls.inovareti.modules.ticket.application.dto.ResolveTicketDTO;
import br.dev.ctrls.inovareti.modules.ticket.application.dto.TicketAttachmentResponseDTO;
import br.dev.ctrls.inovareti.modules.ticket.application.dto.TicketCommentRequestDTO;
import br.dev.ctrls.inovareti.modules.ticket.application.dto.TicketCommentResponseDTO;
import br.dev.ctrls.inovareti.modules.ticket.application.dto.TicketRequestDTO;
import br.dev.ctrls.inovareti.modules.ticket.application.dto.TicketResponseDTO;
import br.dev.ctrls.inovareti.modules.ticket.application.usecase.LinkTicketUseCase;
import jakarta.validation.Valid;

import br.dev.ctrls.inovareti.modules.ticket.application.dto.UpdateSolutionTextDTO;
import br.dev.ctrls.inovareti.modules.ticket.application.dto.UpdateTicketItemsDTO;
import br.dev.ctrls.inovareti.modules.ticket.application.usecase.AddAdditionalUserUseCase;
import br.dev.ctrls.inovareti.modules.ticket.application.usecase.AddTicketCommentUseCase;
import br.dev.ctrls.inovareti.modules.ticket.application.usecase.ChangeCategoryUseCase;
import br.dev.ctrls.inovareti.modules.ticket.application.usecase.ClaimTicketUseCase;
import br.dev.ctrls.inovareti.modules.ticket.application.usecase.CreateTicketUseCase;
import br.dev.ctrls.inovareti.modules.ticket.application.usecase.FetchTicketsByItemUseCase;
import br.dev.ctrls.inovareti.modules.ticket.application.usecase.FindTicketByIdUseCase;
import br.dev.ctrls.inovareti.modules.ticket.application.usecase.GetTicketCommentsUseCase;
import br.dev.ctrls.inovareti.modules.ticket.application.usecase.ListAllTicketsUseCase;
import br.dev.ctrls.inovareti.modules.ticket.application.usecase.ResolveTicketUseCase;
import br.dev.ctrls.inovareti.modules.ticket.application.usecase.TransferTicketUseCase;
import br.dev.ctrls.inovareti.modules.ticket.application.usecase.UpdateSolutionTextUseCase;
import br.dev.ctrls.inovareti.modules.ticket.application.usecase.UpdateTicketItemsUseCase;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.Ticket;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.TicketAttachment;
import br.dev.ctrls.inovareti.modules.ticket.domain.port.output.TicketAttachmentRepositoryPort;
import br.dev.ctrls.inovareti.modules.ticket.domain.port.output.TicketRepositoryPort;
import br.dev.ctrls.inovareti.modules.user.domain.port.output.UserRepositoryPort;
import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Controller REST para gerenciamento de chamados (tickets).
 * Base path: /api/tickets
 */
@Slf4j
@RestController
@RequestMapping("/tickets")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Observed
@SuppressWarnings("spring-data-string-property-reference")
public class TicketController {

    private final CreateTicketUseCase createTicketUseCase;
    private final ResolveTicketUseCase resolveTicketUseCase;
    private final ClaimTicketUseCase claimTicketUseCase;
    private final TransferTicketUseCase transferTicketUseCase;
    private final AddTicketCommentUseCase addTicketCommentUseCase;
    private final GetTicketCommentsUseCase getTicketCommentsUseCase;
    private final ListAllTicketsUseCase listAllTicketsUseCase;
    private final FindTicketByIdUseCase findTicketByIdUseCase;
    private final LocalFileStorageService fileStorageService;
    private final TicketAttachmentRepositoryPort attachmentRepository;
    private final TicketRepositoryPort ticketRepository;
    private final UserRepositoryPort userRepository;
    private final UpdateSolutionTextUseCase updateSolutionTextUseCase;
    private final ChangeCategoryUseCase changeCategoryUseCase;
    private final AddAdditionalUserUseCase addAdditionalUserUseCase;
    private final FetchTicketsByItemUseCase fetchTicketsByItemUseCase;
    private final UpdateTicketItemsUseCase updateTicketItemsUseCase;
    private final LinkTicketUseCase linkTicketUseCase;

    private void checkTicketOwnershipOrStaff(UUID ticketId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            throw new org.springframework.security.access.AccessDeniedException("Acesso negado: Usuário não autenticado.");
        }
        
        UUID userId;
        try {
            userId = UUID.fromString(auth.getPrincipal().toString());
        } catch (Exception e) {
            throw new org.springframework.security.access.AccessDeniedException("Acesso negado: Identificador de usuário inválido.");
        }

        var user = userRepository.findById(userId)
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("Acesso negado: Usuário não encontrado."));

        if (user.getRole() == br.dev.ctrls.inovareti.modules.user.domain.model.UserRole.ADMIN 
                || user.getRole() == br.dev.ctrls.inovareti.modules.user.domain.model.UserRole.TECHNICIAN) {
            return;
        }

        var ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new br.dev.ctrls.inovareti.core.shared.domain.model.exception.NotFoundException("Chamado não encontrado."));

        if (!ticket.getRequester().getId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException("Acesso negado: Você não é o proprietário deste chamado.");
        }
    }

    /**
     * Lista todos os chamados com isolamento por role e suporte a pesquisa global.
     * ADMIN/TECHNICIAN: ver todos os chamados
     * USER: ver apenas seus próprios chamados
     * Retorna 200 OK com a lista de chamados.
     */
    @GetMapping
    @SuppressWarnings("spring-data-string-property-reference")
    public ResponseEntity<org.springframework.data.domain.Page<TicketResponseDTO>> listAll(
            @RequestParam(required = false) List<UUID> tagIds,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) br.dev.ctrls.inovareti.modules.ticket.domain.model.TicketStatus status,
            @RequestParam(required = false) br.dev.ctrls.inovareti.modules.ticket.domain.model.TicketPriority priority,
            @RequestParam(required = false) UUID categoryId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UUID userId;
        
        try {
            userId = UUID.fromString(auth.getPrincipal().toString());
        } catch (Exception e) {
            log.warn("Não foi possível obter ID do usuário a partir da autenticação");
            return ResponseEntity.badRequest().build();
        }
        
        var user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        
        String sortProperty = "createdAt";
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(
                page,
                15,
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, sortProperty)
        );
        
        return ResponseEntity.ok(listAllTicketsUseCase.execute(
                userId,
                user.getRole(),
                tagIds,
                search,
                status,
                priority,
                categoryId,
                pageable
        ));
    }

    /**
     * Retorna os dados de um único chamado pelo UUID.
     * Retorna 200 OK ou 404 se não encontrado.
     */
    @GetMapping("/{id}")
    public ResponseEntity<TicketResponseDTO> findById(@PathVariable UUID id) {
        checkTicketOwnershipOrStaff(id);
        return ResponseEntity.ok(findTicketByIdUseCase.execute(id));
    }

    /**
     * Retorna chamados resolvidos/fechados que compartilham tags com o chamado atual,
     * para atuar como base de conhecimento inline.
     */
    @GetMapping("/{id}/similar")
    public ResponseEntity<List<TicketResponseDTO>> findSimilar(@PathVariable UUID id) {
        checkTicketOwnershipOrStaff(id);
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Chamado não encontrado com id: " + id));

        if (ticket.getTags() == null || ticket.getTags().isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        List<TicketResponseDTO> response = ticketRepository.findSimilarResolvedTickets(id, ticket.getTags())
                .stream()
                .map(TicketResponseDTO::from)
                .toList();

        return ResponseEntity.ok(response);
    }

    /**
     * Abre um novo chamado com status OPEN e slaDeadline calculado automaticamente.
     * Retorna 201 Created com os dados do chamado.
     * âœ… Acessível a todos os usuários autenticados (USER, TECHNICIAN e ADMIN).
     */
    @PostMapping
    public ResponseEntity<TicketResponseDTO> create(@Valid @RequestBody TicketRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(createTicketUseCase.execute(request));
    }

    /**
     * Resolve um chamado existente com entrega opcional de equipamento ou item.
     * Se entrega de equipamento ou item for especificada, executa o fulfillment atomicamente.
     * Retorna 200 OK com os dados atualizados do chamado.
     */
    @PatchMapping("/{id}/resolve")
    public ResponseEntity<TicketResponseDTO> resolve(
            @PathVariable UUID id,
            @Valid @RequestBody ResolveTicketDTO request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UUID authenticatedUserId;

        try {
            authenticatedUserId = UUID.fromString(auth.getPrincipal().toString());
        } catch (Exception e) {
            log.warn("Não foi possível obter ID do usuário a partir da autenticação");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(resolveTicketUseCase.execute(id, request, authenticatedUserId));
    }

    /**
     * Assume um chamado para o usuário autenticado e altera o status para EM_PROGRESSO.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIAN')")
    @PatchMapping("/{id}/claim")
    public ResponseEntity<TicketResponseDTO> claim(@PathVariable UUID id) {
        return ResponseEntity.ok(claimTicketUseCase.execute(id));
    }

    /**
     * Transfere um chamado para outro usuário.
     * Se o chamado estiver ABERTO, o status é alterado para EM_PROGRESSO.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIAN')")
    @PatchMapping("/{id}/transfer/{userId}")
    public ResponseEntity<TicketResponseDTO> transfer(@PathVariable UUID id, @PathVariable UUID userId) {
        return ResponseEntity.ok(transferTicketUseCase.execute(id, userId));
    }

    /**
     * Upload de anexo para um chamado existente.
     * Retorna 201 Created com os dados do anexo.
     */
    @PostMapping("/{id}/attachments")
    public ResponseEntity<TicketAttachmentResponseDTO> uploadAttachment(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file) {
        
        checkTicketOwnershipOrStaff(id);
        Ticket ticket = ticketRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Chamado não encontrado"));

        try {
            String storedFilename = fileStorageService.store(file);
            
            TicketAttachment attachment = TicketAttachment.builder()
                    .originalFilename(file.getOriginalFilename())
                    .storedFilename(storedFilename)
                    .fileType(file.getContentType() != null ? file.getContentType() : "application/octet-stream")
                    .ticket(ticket)
                    .build();
            
            attachment = attachmentRepository.save(attachment);
            
                log.info("Anexo enviado para chamado {}: {} (armazenado como: {})",
                    id, file.getOriginalFilename(), storedFilename);
            
            TicketAttachmentResponseDTO response = new TicketAttachmentResponseDTO(
                    attachment.getId(),
                    attachment.getOriginalFilename(),
                    attachment.getStoredFilename(),
                    attachment.getFileType(),
                    attachment.getTicket().getId(),
                    attachment.getUploadedAt()
            );
            
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IOException e) {
            throw new RuntimeException("Falha ao armazenar arquivo", e);
        }
    }

    /**
     * Lista todos os anexos de um chamado específico.
     * Retorna 200 OK com a lista de anexos.
     */
    @GetMapping("/{id}/attachments")
    public ResponseEntity<List<TicketAttachmentResponseDTO>> listAttachments(@PathVariable UUID id) {
        checkTicketOwnershipOrStaff(id);
        List<TicketAttachment> attachments = attachmentRepository.findByTicketId(id);
        
        List<TicketAttachmentResponseDTO> response = attachments.stream()
                .map(a -> new TicketAttachmentResponseDTO(
                        a.getId(),
                        a.getOriginalFilename(),
                        a.getStoredFilename(),
                        a.getFileType(),
                        a.getTicket().getId(),
                        a.getUploadedAt()
                ))
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(response);
    }

    /**
     * Adiciona um novo comentário a um chamado existente.
     * Retorna 201 Created com os dados do comentário.
     */
    @PostMapping("/{id}/comments")
    public ResponseEntity<TicketCommentResponseDTO> addComment(
            @PathVariable UUID id,
            @Valid @RequestBody TicketCommentRequestDTO request) {
        checkTicketOwnershipOrStaff(id);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(addTicketCommentUseCase.execute(id, request));
    }

    /**
     * Lista todos os comentários de um chamado específico.
     * Retorna 200 OK com a lista de comentários ordenados por data.
     */
    @GetMapping("/{id}/comments")
    public ResponseEntity<List<TicketCommentResponseDTO>> listComments(@PathVariable UUID id) {
        checkTicketOwnershipOrStaff(id);
        return ResponseEntity.ok(getTicketCommentsUseCase.execute(id));
    }

    /**
     * Relaciona dois chamados de forma bidirecional.
     * Retorna 200 OK com o chamado principal atualizado.
     */
    @org.springframework.transaction.annotation.Transactional
    @PostMapping("/{id}/relate/{relatedId}")
    public ResponseEntity<TicketResponseDTO> relateTickets(
            @PathVariable UUID id,
            @PathVariable UUID relatedId) {
        checkTicketOwnershipOrStaff(id);
        checkTicketOwnershipOrStaff(relatedId);

        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new br.dev.ctrls.inovareti.core.shared.domain.model.exception.NotFoundException("Chamado principal não encontrado: " + id));

        Ticket relatedTicket = ticketRepository.findById(relatedId)
                .orElseThrow(() -> new br.dev.ctrls.inovareti.core.shared.domain.model.exception.NotFoundException("Chamado relacionado não encontrado: " + relatedId));

        ticket.getRelatedTickets().add(relatedTicket);
        relatedTicket.getRelatedTickets().add(ticket);

        ticketRepository.save(ticket);
        ticketRepository.save(relatedTicket);

        log.info("Relacionamento estabelecido de forma bidirecional entre o chamado {} e {}", id, relatedId);

        return ResponseEntity.ok(TicketResponseDTO.from(ticket));
    }

    /**
     * Atualiza a descrição de solução de um chamado resolvido ou fechado.
     */
    @PatchMapping("/{id}/solution")
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIAN')")
    public ResponseEntity<TicketResponseDTO> updateSolutionText(
            @PathVariable UUID id,
            @RequestBody UpdateSolutionTextDTO request) {
        checkTicketOwnershipOrStaff(id);
        return ResponseEntity.ok(updateSolutionTextUseCase.execute(id, request));
    }

    /**
     * Altera a categoria de um chamado e recalcula o prazo de SLA.
     * O novo {@code slaDeadline} é calculado somando {@code baseSlaHours} da nova categoria
     * í  {@code createdAt} original do chamado.
     * Restrito a ADMIN e TECHNICIAN.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIAN')")
    @PatchMapping("/{id}/category/{categoryId}")
    public ResponseEntity<TicketResponseDTO> changeCategory(
            @PathVariable UUID id,
            @PathVariable UUID categoryId) {
        return ResponseEntity.ok(changeCategoryUseCase.execute(id, categoryId));
    }

    /**
     * Vincula um usuário adicional afetado ao chamado.
     * O usuário é inserido na tabela {@code ticket_additional_users}.
     * Restrito a ADMIN e TECHNICIAN.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIAN')")
    @PostMapping("/{id}/additional-users/{userId}")
    public ResponseEntity<TicketResponseDTO> addAdditionalUser(
            @PathVariable UUID id,
            @PathVariable UUID userId) {
        return ResponseEntity.ok(addAdditionalUserUseCase.execute(id, userId));
    }

    /**
     * Recupera de forma paginada todos os chamados que possuem vínculo com um item de inventário específico.
     * Retorna 200 OK com a página de chamados associados.
     *
     * @param itemId O identificador único do item de inventário (UUID)
     * @param page O número da página a ser retornada (padrão: 0)
     * @return ResponseEntity contendo a página de chamados vinculados ao item
     */
    @GetMapping("/item/{itemId}")
    public ResponseEntity<org.springframework.data.domain.Page<TicketResponseDTO>> getTicketsByItem(
            @PathVariable UUID itemId,
            @RequestParam(defaultValue = "0") int page) {
        String sortProperty = "createdAt";
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(
                page,
                15,
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, sortProperty)
        );
        return ResponseEntity.ok(fetchTicketsByItemUseCase.execute(itemId, pageable));
    }

    /**
     * Atualiza a lista de itens de inventário vinculados a um chamado existente.
     * Apenas o solicitante original ou membros da equipe de suporte (ADMIN/TECHNICIAN) podem realizar essa alteração.
     * Retorna 200 OK com os dados atualizados do chamado.
     *
     * @param id O identificador único do chamado (UUID)
     * @param request O DTO contendo a nova lista de itens e quantidades
     * @return ResponseEntity contendo os dados atualizados do chamado
     */
    @PatchMapping("/{id}/items")
    public ResponseEntity<TicketResponseDTO> updateTicketItems(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTicketItemsDTO request) {
        // Valida se o utilizador atual possui permissão de leitura/escrita no chamado correspondente
        checkTicketOwnershipOrStaff(id);
        return ResponseEntity.ok(updateTicketItemsUseCase.execute(id, request));
    }

    /**
     * Vincula um chamado filho a um chamado pai/mestre.
     *
     * @param id O identificador único do chamado filho (UUID)
     * @param request O DTO contendo o ID do chamado pai
     * @return ResponseEntity indicando sucesso
     */
    @PostMapping("/{id}/link")
    public ResponseEntity<TicketResponseDTO> linkTicket(
            @PathVariable UUID id,
            @Valid @RequestBody LinkTicketRequest request) {
        // Valida se o utilizador atual possui permissão no chamado correspondente
        checkTicketOwnershipOrStaff(id);
        return ResponseEntity.ok(linkTicketUseCase.execute(id, request.parentTicketId()));
    }

    public record LinkTicketRequest(
        @jakarta.validation.constraints.NotNull(message = "O ID do chamado pai é obrigatório")
        UUID parentTicketId
    ) {}
}


