package br.dev.ctrls.inovareti.modules.finance.domain.model;

/**
 * DTO simples representando uma parcela/resultados de pagamento retornados
 * pela Conta Azul utilizados internamente pela aplicaÃ§Ã£o.
 *
 * - `parcelaId`: identificador da parcela na Conta Azul
 * - `customerId`: identificador do cliente/paciente na Conta Azul
 * - `medicoNome`: nome do profissional associado
 * - `recipientEmail`: e-mail do destinatÃ¡rio para envio de recibos
 * - `saleNumber`: nÃºmero comercial da venda (numero/numero_venda)
 */
public record ContaAzulPaymentParcel(
        String parcelaId,
        String customerId,
        String medicoNome,
        String recipientEmail,
        String saleNumber) {
}
/**
 * ObservaÃ§Ã£o de uso: este record Ã© um DTO simples e NÃƒO altera nomes de
 * colunas do banco â€” apenas agrupa informaÃ§Ãµes extraÃ­das dos payloads da
 * Conta Azul para uso interno nas automaÃ§Ãµes e envio de e-mails.
 */

