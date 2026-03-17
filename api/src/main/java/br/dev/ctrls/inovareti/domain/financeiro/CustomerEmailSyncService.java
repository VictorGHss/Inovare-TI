package br.dev.ctrls.inovareti.domain.financeiro;

import java.time.Duration;
import java.time.OffsetDateTime;

import org.springframework.stereotype.Service;

import br.dev.ctrls.inovareti.domain.financeiro.contaazul.ContaAzulPessoaClient;
import br.dev.ctrls.inovareti.domain.financeiro.contaazul.ContaAzulPessoaDTO;
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

        log.info("Cache expirado — buscando e-mail na API Conta Azul. customerId={}",
                link.getContaAzulCustomerId());

        ContaAzulPessoaDTO pessoa = link.getContaAzulPessoaUuid() != null
                ? pessoaClient.findById(link.getContaAzulPessoaUuid())
                        .orElseThrow(() -> new IllegalStateException(
                                "UUID inválido: " + link.getContaAzulPessoaUuid()))
                : pessoaClient.findByLegacyId(link.getContaAzulCustomerId())
                        .orElseThrow(() -> new IllegalStateException(
                                "Cliente não encontrado no Conta Azul: " + link.getContaAzulCustomerId()));

        String email = pessoa.resolveEmail()
                .orElseThrow(() -> new IllegalStateException(
                        "Médico sem e-mail no Conta Azul. customerId=" + link.getContaAzulCustomerId()));

        link.setEmail(email);
        link.setNomeCliente(pessoa.nome());
        link.setContaAzulPessoaUuid(pessoa.id());
        link.setEmailSyncedAt(OffsetDateTime.now());
        financialLinkRepository.save(link);

        return email;
    }
}
