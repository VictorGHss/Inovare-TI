package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

public final class ContaAzulStatus {

    /**
     * Status válidos para GET /v1/financeiro/eventos-financeiros/contas-a-receber/buscar
     * conforme a spec oficial da Conta Azul consultada em 16/03/2026.
     */
    public static final String RECEBIDO = "RECEBIDO";
    public static final String EM_ABERTO = "EM_ABERTO";
    public static final String ATRASADO = "ATRASADO";
    public static final String RENEGOCIADO = "RENEGOCIADO";
    public static final String RECEBIDO_PARCIAL = "RECEBIDO_PARCIAL";
    public static final String PERDIDO = "PERDIDO";

    private ContaAzulStatus() {
    }
}
