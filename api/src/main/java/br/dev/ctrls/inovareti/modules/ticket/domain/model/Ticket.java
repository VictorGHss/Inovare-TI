package br.dev.ctrls.inovareti.modules.ticket.domain.model;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.JoinTable;

import br.dev.ctrls.inovareti.modules.inventory.domain.model.Item;
import br.dev.ctrls.inovareti.infrastructure.shared.security.CryptoConverter;
import br.dev.ctrls.inovareti.modules.user.domain.model.User;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
    @Convert(converter = CryptoConverter.class)
    @Column(name = "anydesk_code", length = 500)
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

    /** Texto da solução/resolução dada ao chamado. */
    @Column(name = "solution_text", columnDefinition = "text")
    private String solutionText;

    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "ticket_relations",
        joinColumns = @JoinColumn(name = "ticket_id"),
        inverseJoinColumns = @JoinColumn(name = "related_ticket_id")
    )
    private Set<Ticket> relatedTickets = new HashSet<>();

    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "ticket_tag_relations",
        joinColumns = @JoinColumn(name = "ticket_id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private Set<TicketTag> tags = new HashSet<>();

    /**
     * Usuários adicionais afetados pelo chamado.
     * Vinculados via tabela de junção {@code ticket_additional_users}.
     * Ao fechar o chamado, cada um desses usuários recebe uma DM no Discord
     * informando a resolução do problema.
     */
    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "ticket_additional_users",
        joinColumns = @JoinColumn(name = "ticket_id"),
        inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private Set<br.dev.ctrls.inovareti.modules.user.domain.model.User> additionalUsers = new HashSet<>();

    /** Ativo (CMDB) associado a este chamado, se aplicável. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id")
    private br.dev.ctrls.inovareti.modules.asset.domain.model.Asset asset;
}
