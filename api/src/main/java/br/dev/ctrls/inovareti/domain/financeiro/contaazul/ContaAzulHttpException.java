package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

/**
 * Exceção de infraestrutura para erros HTTP retornados pela API da Conta Azul.
 *
 * Carrega o status HTTP e o corpo bruto da resposta para facilitar diagnóstico.
 */
public class ContaAzulHttpException extends RuntimeException {

    private final int statusCode;
    private final String responseBody;

    public ContaAzulHttpException(int statusCode, String responseBody) {
        super("Conta Azul retornou erro HTTP " + statusCode);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
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
}
