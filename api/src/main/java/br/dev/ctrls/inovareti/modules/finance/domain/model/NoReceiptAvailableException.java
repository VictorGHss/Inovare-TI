package br.dev.ctrls.inovareti.modules.finance.domain.model;

/**
 * ExceÃ§Ã£o lanÃ§ada quando a Conta Azul nÃ£o fornece um anexo de recibo
 * para uma baixa financeira consultada â€” indica que o recibo ainda nÃ£o
 * foi gerado/associado e que nÃ£o hÃ¡ bytes a serem baixados.
 */
public class NoReceiptAvailableException extends RuntimeException {
    public NoReceiptAvailableException(String message) {
        super(message);
    }

    public NoReceiptAvailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

