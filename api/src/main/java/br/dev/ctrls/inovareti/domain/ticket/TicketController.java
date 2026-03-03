package br.dev.ctrls.inovareti.domain.ticket;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import br.dev.ctrls.inovareti.domain.ticket.dto.TicketAttachmentResponseDTO;
import br.dev.ctrls.inovareti.domain.ticket.dto.TicketRequestDTO;
import br.dev.ctrls.inovareti.domain.ticket.dto.TicketResponseDTO;
import br.dev.ctrls.inovareti.domain.ticket.usecase.ClaimTicketUseCase;
import br.dev.ctrls.inovareti.domain.ticket.usecase.CloseTicketUseCase;
import br.dev.ctrls.inovareti.domain.ticket.usecase.CreateTicketUseCase;
import br.dev.ctrls.inovareti.domain.ticket.usecase.FindTicketByIdUseCase;
import br.dev.ctrls.inovareti.domain.ticket.usecase.ListAllTicketsUseCase;
import br.dev.ctrls.inovareti.domain.ticket.usecase.TransferTicketUseCase;
import br.dev.ctrls.inovareti.infra.storage.LocalFileStorageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Controller REST para gerenciamento de chamados (tickets).
 * Base path: /api/tickets
 */
@Slf4j
@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final CreateTicketUseCase createTicketUseCase;
    private final CloseTicketUseCase closeTicketUseCase;
    private final ClaimTicketUseCase claimTicketUseCase;
    private final TransferTicketUseCase transferTicketUseCase;
    private final ListAllTicketsUseCase listAllTicketsUseCase;
    private final FindTicketByIdUseCase findTicketByIdUseCase;
    private final LocalFileStorageService fileStorageService;
    private final TicketAttachmentRepository attachmentRepository;
    private final TicketRepository ticketRepository;

    /**
     * Lista todos os chamados com suas relações carregadas.
     * Retorna 200 OK com a lista de chamados.
     */
    @GetMapping
    public ResponseEntity<List<TicketResponseDTO>> listAll() {
        return ResponseEntity.ok(listAllTicketsUseCase.execute());
    }

    /**
     * Retorna os dados de um único chamado pelo UUID.
     * Retorna 200 OK ou 404 se não encontrado.
     */
    @GetMapping("/{id}")
    public ResponseEntity<TicketResponseDTO> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(findTicketByIdUseCase.execute(id));
    }

    /**
     * Abre um novo chamado com status OPEN e slaDeadline calculado automaticamente.
     * Retorna 201 Created com os dados do chamado.
     */
    @PostMapping
    public ResponseEntity<TicketResponseDTO> create(@Valid @RequestBody TicketRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(createTicketUseCase.execute(request));
    }

    /**
     * Fecha um chamado existente.
     * Se o chamado tiver item e quantidade solicitados, debita o estoque atomicamente.
     * Retorna 200 OK com o chamado atualizado.
     */
    @PatchMapping("/{id}/close")
    public ResponseEntity<TicketResponseDTO> close(@PathVariable UUID id) {
        return ResponseEntity.ok(closeTicketUseCase.execute(id));
    }

    /**
     * Assumes a ticket for the authenticated user and sets it to IN_PROGRESS.
     */
    @PatchMapping("/{id}/claim")
    public ResponseEntity<TicketResponseDTO> claim(@PathVariable UUID id) {
        return ResponseEntity.ok(claimTicketUseCase.execute(id));
    }

    /**
     * Transfers a ticket to another user.
     * If ticket is OPEN, status is changed to IN_PROGRESS.
     */
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
        
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));

        try {
            String storedFilename = fileStorageService.store(file);
            
            TicketAttachment attachment = TicketAttachment.builder()
                    .originalFilename(file.getOriginalFilename())
                    .storedFilename(storedFilename)
                    .fileType(file.getContentType() != null ? file.getContentType() : "application/octet-stream")
                    .ticket(ticket)
                    .build();
            
            attachment = attachmentRepository.save(attachment);
            
            log.info("File attachment uploaded for ticket {}: {} (stored as: {})",
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
            throw new RuntimeException("Failed to store file", e);
        }
    }

    /**
     * Lista todos os anexos de um chamado específico.
     * Retorna 200 OK com a lista de anexos.
     */
    @GetMapping("/{id}/attachments")
    public ResponseEntity<List<TicketAttachmentResponseDTO>> listAttachments(@PathVariable UUID id) {
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
}
