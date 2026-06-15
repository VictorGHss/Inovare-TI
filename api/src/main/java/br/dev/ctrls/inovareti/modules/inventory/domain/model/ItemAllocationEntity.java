package br.dev.ctrls.inovareti.modules.inventory.domain.model;

import java.time.LocalDateTime;
import java.util.UUID;

import br.dev.ctrls.inovareti.modules.ticket.domain.model.Ticket;
import br.dev.ctrls.inovareti.modules.user.domain.model.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entidade JPA que espelha a tabela de alocações do inventário (item_allocations).
 * Registra a associação de periféricos ou consumíveis a ativos principais,
 * garantindo rastreabilidade do consumo e a integridade referencial dos dados.
 */
@Entity
@Table(name = "item_allocations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemAllocationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * Ativo principal ao qual o periférico ou consumível está sendo associado (ex.: Impressora).
     * Mapeado com carregamento tardio (LAZY) para melhor eficiência em queries.
     * Configurado com ON DELETE CASCADE no banco (se o pai for excluído, remove-se a alocação).
     */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parent_item_id", nullable = false)
    private Item parentItem;

    /**
     * Periférico ou consumível que está sendo alocado (ex.: Toner).
     * Mapeado com carregamento tardio (LAZY).
     * Configurado com ON DELETE RESTRICT no banco para impedir que o item de consumo
     * seja excluído caso exista um histórico de alocação vinculado.
     */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "child_item_id", nullable = false)
    private Item childItem;

    /**
     * Quantidade do consumível alocada.
     */
    @NotNull
    @Positive
    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    /**
     * Data e hora em que a alocação foi realizada.
     */
    @NotNull
    @Column(name = "allocated_at", nullable = false)
    private LocalDateTime allocatedAt;

    /**
     * Usuário/Técnico que realizou o registro da alocação.
     * Mapeado com carregamento tardio (LAZY).
     */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "allocated_by_id", nullable = false)
    private User allocatedBy;

    /**
     * Chamado de suporte associado a esta alocação de item (se houver).
     * Mapeado com carregamento tardio (LAZY).
     * Configurado com ON DELETE SET NULL no banco (a exclusão do chamado mantém o histórico).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id")
    private Ticket ticket;
}
