package br.dev.ctrls.inovareti.domain.ticket.usecase;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.core.exception.NotFoundException;
import br.dev.ctrls.inovareti.domain.inventory.Item;
import br.dev.ctrls.inovareti.domain.inventory.ItemRepository;
import br.dev.ctrls.inovareti.domain.ticket.Ticket;
import br.dev.ctrls.inovareti.domain.ticket.TicketCategory;
import br.dev.ctrls.inovareti.domain.ticket.TicketCategoryRepository;
import br.dev.ctrls.inovareti.domain.ticket.TicketRepository;
import br.dev.ctrls.inovareti.domain.ticket.TicketStatus;
import br.dev.ctrls.inovareti.domain.ticket.dto.TicketRequestDTO;
import br.dev.ctrls.inovareti.domain.ticket.dto.TicketResponseDTO;
import br.dev.ctrls.inovareti.domain.user.User;
import br.dev.ctrls.inovareti.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;

/**
 * Caso de uso: abre um novo chamado.
 * Responsabilidades:
 *   1. Validar existência do solicitante, categoria e item (se informado).
 *   2. Calcular o slaDeadline somando baseSlaHours ao momento atual.
 *   3. Definir status inicial como OPEN.
 */
@Component
@RequiredArgsConstructor
public class CreateTicketUseCase {

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final ItemRepository itemRepository;

    /**
     * Abre um chamado com as informações fornecidas.
     *
     * @param request DTO com os dados do chamado
     * @return DTO com os dados do chamado criado
     */
    @Transactional
    public TicketResponseDTO execute(TicketRequestDTO request) {
        User requester = userRepository.findById(request.requesterId())
                .orElseThrow(() -> new NotFoundException(
                        "Solicitante não encontrado com o id: " + request.requesterId()));

        User assignedTo = null;
        if (request.assignedToId() != null) {
            assignedTo = userRepository.findById(request.assignedToId())
                    .orElseThrow(() -> new NotFoundException(
                            "Técnico não encontrado com o id: " + request.assignedToId()));
        }

        TicketCategory category = ticketCategoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new NotFoundException(
                        "Categoria não encontrada com o id: " + request.categoryId()));

        Item requestedItem = null;
        if (request.requestedItemId() != null) {
            requestedItem = itemRepository.findById(request.requestedItemId())
                    .orElseThrow(() -> new NotFoundException(
                            "Item não encontrado com o id: " + request.requestedItemId()));
        }

        LocalDateTime now = LocalDateTime.now();

        Ticket ticket = Ticket.builder()
                .title(request.title())
                .description(request.description())
                .anydeskCode(request.anydeskCode())
                .status(TicketStatus.OPEN)
                .priority(request.priority())
                .requester(requester)
                .assignedTo(assignedTo)
                .category(category)
                .requestedItem(requestedItem)
                .requestedQuantity(request.requestedQuantity())
                .slaDeadline(now.plusHours(category.getBaseSlaHours()))
                .createdAt(now)
                .build();

        return TicketResponseDTO.from(ticketRepository.save(ticket));
    }
}
