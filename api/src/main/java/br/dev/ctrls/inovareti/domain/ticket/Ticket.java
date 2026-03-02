package br.dev.ctrls.inovareti.domain.ticket;

import br.dev.ctrls.inovareti.domain.inventory.Item;
import br.dev.ctrls.inovareti.domain.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Representa um chamado (ticket) de suporte ou solicitação de item.
 * Se {@code requestedItem} e {@code requestedQuantity} estiverem preenchidos,
 * ao fechar o chamado o estoque do item será decrementado automaticamente.
 */
@Entity
@Table(name = "tickets")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotBlank
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    /** Código AnyDesk do computador do solicitante, para acesso remoto. */
    @Column(name = "anydesk_code", length = 50)
    private String anydeskCode;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TicketStatus status;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 10)
    private TicketPriority priority;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    /** Técnico responsável pelo chamado. Pode ser nulo até a atribuição. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to_id")
    private User assignedTo;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private TicketCategory category;

    /** Item de inventário solicitado neste chamado. Opcional. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_item_id")
    private Item requestedItem;

    /** Quantidade do item solicitado. Obrigatório quando requestedItem não for nulo. */
    @Column(name = "requested_quantity")
    private Integer requestedQuantity;

    @NotNull
    @Column(name = "sla_deadline", nullable = false)
    private LocalDateTime slaDeadline;

    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;
}
