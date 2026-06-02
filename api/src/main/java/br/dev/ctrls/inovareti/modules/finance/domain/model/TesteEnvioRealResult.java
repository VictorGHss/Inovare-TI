package br.dev.ctrls.inovareti.modules.finance.domain.model;

/**
 * Resultado do teste de envio real de recibo — utilizado para validar o fluxo
 * de geração e envio de PDF em ambiente de teste/integração.
 *
 * - `saleId`: id da venda relacionada
 * - `doctorName`: nome do profissional
 * - `recipientEmail`: e-mail do destinatário
 * - `pdfBytes`: tamanho em bytes do PDF gerado
 */
public record TesteEnvioRealResult(
        String saleId,
        String doctorName,
        String recipientEmail,
        int pdfBytes) {
}

