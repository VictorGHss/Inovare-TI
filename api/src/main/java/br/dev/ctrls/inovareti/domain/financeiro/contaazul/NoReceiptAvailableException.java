package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

/**
 * Exceção lançada quando a Conta Azul não fornece um anexo de recibo
 * para uma baixa financeira consultada — indica que o recibo ainda não
 * foi gerado/associado e que não há bytes a serem baixados.
 */
public class NoReceiptAvailableException extends RuntimeException {
    public NoReceiptAvailableException(String message) {
        super(message);
    }

    public NoReceiptAvailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
