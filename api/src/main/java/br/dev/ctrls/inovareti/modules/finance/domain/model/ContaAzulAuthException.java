package br.dev.ctrls.inovareti.modules.finance.domain.model;

/**
 * ExceÃ§Ã£o que representa falhas relacionadas Ã  autorizaÃ§Ã£o/autenticaÃ§Ã£o
 * com a API da Conta Azul (por exemplo, refresh falhado ou token invÃ¡lido).
 */
public class ContaAzulAuthException extends RuntimeException {

    public ContaAzulAuthException(String message) {
        super(message);
    }

    public ContaAzulAuthException(String message, Throwable cause) {
        super(message, cause);
    }
}

