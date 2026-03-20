package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

public record TesteEnvioRealResult(
        String saleId,
        String doctorName,
        String recipientEmail,
        int pdfBytes) {
}
