package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

public final class ContaAzulStatus {

    /**
     * Status válidos conforme documentação da API ContaAzul v2.
     * Para o endpoint de contas-a-receber, use os filtros:
     * - data_vencimento_de / data_vencimento_ate: período de vencimento
     * - data_alteracao_de / data_alteracao_ate: período de última alteração (formato: yyyy-MM-dd'T'HH:mm:ss)
     * 
     * Nota: Os parâmetros data_pagamento_de/ate existem apenas no endpoint de contas-a-pagar.
     */
    public static final String QUITADO = "QUITADO";
    public static final String PENDENTE = "PENDENTE";
    public static final String ATRASADO = "ATRASADO";
    public static final String CANCELADO = "CANCELADO";
    public static final String RENEGOCIADO = "RENEGOCIADO";
    public static final String RECEBIDO_PARCIAL = "RECEBIDO_PARCIAL";
    public static final String PERDIDO = "PERDIDO";

    private ContaAzulStatus() {
    }
}
