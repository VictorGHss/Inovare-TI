package br.dev.ctrls.inovareti.modules.finance.domain.model;

/**
 * DTO simples representando uma parcela/resultados de pagamento retornados
 * pela Conta Azul utilizados internamente pela aplicação.
 *
 * - `parcelaId`: identificador da parcela na Conta Azul
 * - `customerId`: identificador do cliente/paciente na Conta Azul
 * - `medicoNome`: nome do profissional associado
 * - `recipientEmail`: e-mail do destinatário para envio de recibos
 * - `saleNumber`: número comercial da venda (numero/numero_venda)
 */
public record ContaAzulPaymentParcel(
        String parcelaId,
        String customerId,
        String medicoNome,
        String recipientEmail,
        String saleNumber) {
}
/**
 * Observação de uso: este record é um DTO simples e NÃO altera nomes de
 * colunas do banco — apenas agrupa informações extraídas dos payloads da
 * Conta Azul para uso interno nas automações e envio de e-mails.
 */

