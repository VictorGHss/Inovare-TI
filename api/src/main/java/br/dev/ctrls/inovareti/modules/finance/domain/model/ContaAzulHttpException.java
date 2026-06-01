package br.dev.ctrls.inovareti.modules.finance.domain.model;

/**
 * ExceÃ§Ã£o de infraestrutura para erros HTTP retornados pela API da Conta Azul.
 *
 * Carrega o status HTTP e o corpo bruto da resposta para facilitar diagnÃ³stico.
 */
public class ContaAzulHttpException extends RuntimeException {

    private final int statusCode;
    private final String responseBody;
    private final String externalUrl;

    public ContaAzulHttpException(int statusCode, String responseBody) {
        this(statusCode, responseBody, null);
    }

    public ContaAzulHttpException(int statusCode, String responseBody, String externalUrl) {
        super(buildMessage(statusCode, externalUrl));
        this.statusCode = statusCode;
        this.responseBody = responseBody;
        this.externalUrl = externalUrl;
    }

    public boolean isStatus(int expectedStatus) {
        return this.statusCode == expectedStatus;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public String getExternalUrl() {
        return externalUrl;
    }

    private static String buildMessage(int statusCode, String externalUrl) {
        if (externalUrl == null || externalUrl.isBlank()) {
            return "Conta Azul retornou erro HTTP " + statusCode;
        }

        return "Conta Azul retornou erro HTTP " + statusCode + " na URL " + externalUrl;
    }
}

