package br.dev.ctrls.inovareti.domain.ticket.usecase;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.core.exception.NotFoundException;
import br.dev.ctrls.inovareti.domain.ticket.Ticket;
import br.dev.ctrls.inovareti.domain.ticket.TicketAttachmentRepository;
import br.dev.ctrls.inovareti.domain.ticket.TicketRepository;
import br.dev.ctrls.inovareti.domain.ticket.dto.AttachmentResponseDTO;
import br.dev.ctrls.inovareti.domain.ticket.dto.TicketResponseDTO;
import lombok.RequiredArgsConstructor;

/**
 * Caso de uso: busca um chamado pelo seu UUID.
 * Lança {@link NotFoundException} se o chamado não existir.
 */
@Component
@RequiredArgsConstructor
public class FindTicketByIdUseCase {

    private final TicketRepository ticketRepository;
    private final TicketAttachmentRepository attachmentRepository;

    @Transactional(readOnly = true)
    public TicketResponseDTO execute(UUID id) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Ticket not found with id: " + id));

        // Busca os anexos do chamado
        List<AttachmentResponseDTO> attachments = attachmentRepository.findByTicketId(id)
                .stream()
                .map(attachment -> new AttachmentResponseDTO(
                        attachment.getId(),
                        attachment.getOriginalFilename(),
                        "/api/attachments/" + attachment.getStoredFilename(),
                        attachment.getFileType()
                ))
                .collect(Collectors.toList());

        // Monta a resposta com os anexos incluídos
        return new TicketResponseDTO(
                ticket.getId(),
                ticket.getTitle(),
                ticket.getDescription(),
                ticket.getAnydeskCode(),
                ticket.getStatus(),
                ticket.getPriority(),
                ticket.getRequester().getId(),
                ticket.getRequester().getName(),
                ticket.getAssignedTo() != null ? ticket.getAssignedTo().getId() : null,
                ticket.getAssignedTo() != null ? ticket.getAssignedTo().getName() : null,
                ticket.getCategory().getId(),
                ticket.getCategory().getName(),
                ticket.getRequestedItem() != null ? ticket.getRequestedItem().getId() : null,
                ticket.getRequestedItem() != null ? ticket.getRequestedItem().getName() : null,
                ticket.getRequestedQuantity(),
                ticket.getSlaDeadline(),
                ticket.getCreatedAt(),
                ticket.getClosedAt(),
                attachments
        );
    }
}
