package br.dev.ctrls.inovareti.modules.finance.application.service;

import br.dev.ctrls.inovareti.modules.finance.domain.port.FinancialLinkRepository;
import br.dev.ctrls.inovareti.modules.finance.domain.model.FinancialTransaction;
import br.dev.ctrls.inovareti.modules.finance.domain.port.FinancialTransactionRepository;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Service;

import br.dev.ctrls.inovareti.domain.ticket.Ticket;
import lombok.RequiredArgsConstructor;

/**
 * ServiÃƒÂ§o responsÃƒÂ¡vel por criar lanÃƒÂ§amentos financeiros internos a partir de eventos
 * do sistema (ex: saÃƒÂ­da de estoque por chamado). A lÃƒÂ³gica segue a regra:
 * - Se o solicitante possuir vÃƒÂ­nculo financeiro (contaAzulId mapeado em `financial_link`),
 *   registra o dÃƒÂ©bito para o mÃƒÂ©dico (`DOCTOR`).
 * - Caso contrÃƒÂ¡rio, registra o dÃƒÂ©bito para o setor do usuÃƒÂ¡rio (`SECTOR`).
 */
@Service
@RequiredArgsConstructor
public class FinancialService {

    private final FinancialTransactionRepository transactionRepository;
    private final FinancialLinkRepository financialLinkRepository;

    /**
     * Cria um lanÃƒÂ§amento financeiro associado a um chamado.
     *
     * @param ticket Chamado que originou a deduÃƒÂ§ÃƒÂ£o
     * @param resourceType Tipo do recurso consumido ("INVENTORY" ou "ASSET")
     * @param amount Valor total apurado na deduÃƒÂ§ÃƒÂ£o (precision 19,2)
     */
    public void recordDebitForTicket(Ticket ticket, String resourceType, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return; // Nada a registrar
        }

        var requester = ticket.getRequester();

        FinancialTransaction.TargetType targetType;
        UUID targetId;

        // Verifica se o usuÃƒÂ¡rio possui vÃƒÂ­nculo financeiro (ContaAzul)
        if (requester.getContaAzulId() != null
                && financialLinkRepository.findByContaAzulCustomerId(requester.getContaAzulId()).isPresent()) {
            targetType = FinancialTransaction.TargetType.DOCTOR;
            targetId = requester.getId();
        } else {
            targetType = FinancialTransaction.TargetType.SECTOR;
            targetId = requester.getSector().getId();
        }

        var tx = FinancialTransaction.builder()
                .targetType(targetType)
                .targetId(targetId)
                .resourceType(FinancialTransaction.ResourceType.valueOf(resourceType))
                .amount(amount)
                .ticketId(ticket.getId())
                .build();

        transactionRepository.save(tx);
    }
}

