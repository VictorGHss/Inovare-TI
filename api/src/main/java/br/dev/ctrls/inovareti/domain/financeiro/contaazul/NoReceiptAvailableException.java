package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

public class NoReceiptAvailableException extends RuntimeException {
    public NoReceiptAvailableException(String message) {
        super(message);
    }

    public NoReceiptAvailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
