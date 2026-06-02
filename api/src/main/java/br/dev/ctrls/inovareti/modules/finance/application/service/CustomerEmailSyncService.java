package br.dev.ctrls.inovareti.modules.finance.application.service;

import br.dev.ctrls.inovareti.modules.finance.domain.port.FinancialLinkRepository;
import br.dev.ctrls.inovareti.modules.finance.domain.model.FinancialLink;

import java.time.Duration;
import java.time.OffsetDateTime;

import org.springframework.stereotype.Service;

import br.dev.ctrls.inovareti.modules.finance.infrastructure.adapter.output.ContaAzulPessoaClient;
import br.dev.ctrls.inovareti.modules.finance.application.dto.ContaAzulPessoaDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerEmailSyncService {

    private static final Duration EMAIL_CACHE_TTL = Duration.ofDays(7);

    private final ContaAzulPessoaClient pessoaClient;
    private final FinancialLinkRepository financialLinkRepository;

    public String resolveEmail(FinancialLink link) {
        if (!link.isEmailStale(EMAIL_CACHE_TTL) && link.getEmail() != null) {
            return link.getEmail();
        }

        log.info("Cache expirado Ã¢â‚¬â€ buscando e-mail na API Conta Azul. customerId={}",
                link.getContaAzulCustomerId());

        ContaAzulPessoaDTO pessoa = link.getContaAzulPessoaUuid() != null
                ? pessoaClient.findById(link.getContaAzulPessoaUuid())
                        .orElseThrow(() -> new IllegalStateException(
                                "UUID invÃƒÂ¡lido: " + link.getContaAzulPessoaUuid()))
                : pessoaClient.findByLegacyId(link.getContaAzulCustomerId())
                        .orElseThrow(() -> new IllegalStateException(
                                "Cliente nÃƒÂ£o encontrado no Conta Azul: " + link.getContaAzulCustomerId()));

        String email = pessoa.resolveEmail()
                .orElseThrow(() -> new IllegalStateException(
                        "MÃƒÂ©dico sem e-mail no Conta Azul. customerId=" + link.getContaAzulCustomerId()));

        link.setEmail(email);
        link.setNomeCliente(pessoa.nome());
        link.setContaAzulPessoaUuid(pessoa.id());
        link.setEmailSyncedAt(OffsetDateTime.now());
        financialLinkRepository.save(link);

        return email;
    }
}


