package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

/**
 * Exceção que representa falhas relacionadas à autorização/autenticação
 * com a API da Conta Azul (por exemplo, refresh falhado ou token inválido).
 */
public class ContaAzulAuthException extends RuntimeException {

    public ContaAzulAuthException(String message) {
        super(message);
    }

    public ContaAzulAuthException(String message, Throwable cause) {
        super(message, cause);
    }
}
