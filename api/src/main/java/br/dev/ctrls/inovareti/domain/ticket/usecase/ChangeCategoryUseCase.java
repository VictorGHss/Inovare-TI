package br.dev.ctrls.inovareti.domain.ticket.usecase;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.core.exception.NotFoundException;
import br.dev.ctrls.inovareti.domain.ticket.Ticket;
import br.dev.ctrls.inovareti.domain.ticket.TicketCategory;
import br.dev.ctrls.inovareti.domain.ticket.TicketCategoryRepository;
import br.dev.ctrls.inovareti.domain.ticket.TicketRepository;
import br.dev.ctrls.inovareti.domain.ticket.dto.TicketResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Caso de uso: altera a categoria de um chamado e recalcula o SLA.
 *
 * <p>Regra de negócio: o novo {@code slaDeadline} é calculado somando
 * {@code baseSlaHours} da nova categoria à {@code createdAt} original do ticket,
 * garantindo que o prazo reflita sempre a data de abertura, não a data da troca.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChangeCategoryUseCase {

    private final TicketRepository ticketRepository;
    private final TicketCategoryRepository ticketCategoryRepository;

    @Transactional
    public TicketResponseDTO execute(UUID ticketId, UUID categoryId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new NotFoundException("Chamado não encontrado: " + ticketId));

        TicketCategory newCategory = ticketCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new NotFoundException("Categoria não encontrada: " + categoryId));

        ticket.setCategory(newCategory);
        // Recalcula SLA a partir da data de criação original do chamado
        ticket.setSlaDeadline(ticket.getCreatedAt().plusHours(newCategory.getBaseSlaHours()));

        Ticket saved = ticketRepository.save(ticket);

        log.info("[TICKET] Categoria do chamado {} alterada para '{}'. Novo deadline SLA: {}",
                ticketId, newCategory.getName(), saved.getSlaDeadline());

        return TicketResponseDTO.from(saved);
    }
}
