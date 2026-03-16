package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

public class ContaAzulAuthException extends RuntimeException {

    public ContaAzulAuthException(String message) {
        super(message);
    }

    public ContaAzulAuthException(String message, Throwable cause) {
        super(message, cause);
    }
}
