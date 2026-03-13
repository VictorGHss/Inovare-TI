package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

public record ContaAzulPaymentParcel(
        String parcelaId,
        String customerId,
        String medicoNome,
        String recipientEmail) {
}
