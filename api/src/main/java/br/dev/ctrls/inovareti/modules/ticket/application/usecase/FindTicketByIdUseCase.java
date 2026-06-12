package br.dev.ctrls.inovareti.modules.ticket.application.usecase;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.core.shared.domain.model.exception.NotFoundException;
import br.dev.ctrls.inovareti.modules.ticket.domain.model.Ticket;
import br.dev.ctrls.inovareti.modules.ticket.domain.port.output.TicketAttachmentRepositoryPort;
import br.dev.ctrls.inovareti.modules.ticket.domain.port.output.TicketRepositoryPort;
import br.dev.ctrls.inovareti.modules.ticket.application.dto.AttachmentResponseDTO;
import br.dev.ctrls.inovareti.modules.ticket.application.dto.TicketResponseDTO;
import lombok.RequiredArgsConstructor;

/**
 * Caso de uso: busca um chamado pelo seu UUID.
 * Lança {@link NotFoundException} se o chamado não existir.
 */
@Component
@RequiredArgsConstructor
public class FindTicketByIdUseCase {

    private final TicketRepositoryPort ticketRepository;
    private final TicketAttachmentRepositoryPort attachmentRepository;

    @Transactional(readOnly = true)
    public TicketResponseDTO execute(UUID id) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Chamado não encontrado com id: " + id));

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

        // Monta a resposta completa com anexos reais carregados do repositório
        return TicketResponseDTO.from(ticket, attachments);
    }
}
