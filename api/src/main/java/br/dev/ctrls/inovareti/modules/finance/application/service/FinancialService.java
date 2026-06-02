package br.dev.ctrls.inovareti.modules.finance.application.service;

import io.micrometer.observation.annotation.Observed;

import br.dev.ctrls.inovareti.modules.ticket.domain.model.Ticket;


import br.dev.ctrls.inovareti.modules.finance.domain.port.FinancialLinkRepository;
import br.dev.ctrls.inovareti.modules.finance.domain.model.FinancialTransaction;
import br.dev.ctrls.inovareti.modules.finance.domain.port.FinancialTransactionRepository;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * ServiÃ§o responsÃ¡vel por criar lanÃ§amentos financeiros internos a partir de eventos
 * do sistema (ex: saÃ­da de estoque por chamado). A lÃ³gica segue a regra:
 * - Se o solicitante possuir vÃ­nculo financeiro (contaAzulId mapeado em `financial_link`),
 *   registra o dÃ©bito para o mÃ©dico (`DOCTOR`).
 * - Caso contrÃ¡rio, registra o dÃ©bito para o setor do usuÃ¡rio (`SECTOR`).
 */
@Service
@RequiredArgsConstructor
@Observed
public class FinancialService {

    private final FinancialTransactionRepository transactionRepository;
    private final FinancialLinkRepository financialLinkRepository;

    /**
     * Cria um lanÃ§amento financeiro associado a um chamado.
     *
     * @param ticket Chamado que originou a deduÃ§Ã£o
     * @param resourceType Tipo do recurso consumido ("INVENTORY" ou "ASSET")
     * @param amount Valor total apurado na deduÃ§Ã£o (precision 19,2)
     */
    public void recordDebitForTicket(Ticket ticket, String resourceType, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return; // Nada a registrar
        }

        var requester = ticket.getRequester();

        FinancialTransaction.TargetType targetType;
        UUID targetId;

        // Verifica se o usuÃ¡rio possui vÃ­nculo financeiro (ContaAzul)
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



