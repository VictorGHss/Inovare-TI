package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

/**
 * Constantes representando os status de pagamentos/receitas usados nas
 * consultas à API da Conta Azul. Centralizar esses valores evita
 * strings espalhadas pelo código e facilita atualizações futuras.
 */
public final class ContaAzulStatus {

    /** Status que indica que o item foi recebido/quitado. */
    public static final String RECEBIDO = "RECEBIDO";

    /** Status que indica que o item está em aberto (pendente). */
    public static final String EM_ABERTO = "EM_ABERTO";

    /** Status que indica que o item está em atraso. */
    public static final String ATRASADO = "ATRASADO";

    /** Status que indica que o item foi renegociado. */
    public static final String RENEGOCIADO = "RENEGOCIADO";

    /** Status que indica pagamento parcial. */
    public static final String RECEBIDO_PARCIAL = "RECEBIDO_PARCIAL";

    /** Algumas contas usam QUITADO como status de pagamento confirmado. */
    public static final String QUITADO = "QUITADO";

    /** Status que indica que o item foi perdido/cancelado. */
    public static final String PERDIDO = "PERDIDO";

    private ContaAzulStatus() {
    }
}
