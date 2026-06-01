package br.dev.ctrls.inovareti.modules.finance.domain.model;

/**
 * Resultado do teste de envio real de recibo â€” utilizado para validar o fluxo
 * de geraÃ§Ã£o e envio de PDF em ambiente de teste/integraÃ§Ã£o.
 *
 * - `saleId`: id da venda relacionada
 * - `doctorName`: nome do profissional
 * - `recipientEmail`: e-mail do destinatÃ¡rio
 * - `pdfBytes`: tamanho em bytes do PDF gerado
 */
public record TesteEnvioRealResult(
        String saleId,
        String doctorName,
        String recipientEmail,
        int pdfBytes) {
}

