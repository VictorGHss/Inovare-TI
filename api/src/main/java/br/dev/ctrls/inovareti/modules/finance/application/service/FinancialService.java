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
 * Serviço responsável por criar lançamentos financeiros internos a partir de eventos
 * do sistema (ex: saída de estoque por chamado). A lógica segue a regra:
 * - Se o solicitante possuir vínculo financeiro (contaAzulId mapeado em `financial_link`),
 *   registra o débito para o médico (`DOCTOR`).
 * - Caso contrário, registra o débito para o setor do usuário (`SECTOR`).
 */
@Service
@RequiredArgsConstructor
@Observed
public class FinancialService {

    private final FinancialTransactionRepository transactionRepository;
    private final FinancialLinkRepository financialLinkRepository;

    /**
     * Cria um lançamento financeiro associado a um chamado.
     *
     * @param ticket Chamado que originou a dedução
     * @param resourceType Tipo do recurso consumido ("INVENTORY" ou "ASSET")
     * @param amount Valor total apurado na dedução (precision 19,2)
     */
    public void recordDebitForTicket(Ticket ticket, String resourceType, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return; // Nada a registrar
        }

        var requester = ticket.getRequester();

        FinancialTransaction.TargetType targetType;
        UUID targetId;

        // Verifica se o usuário possui vínculo financeiro (ContaAzul)
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



