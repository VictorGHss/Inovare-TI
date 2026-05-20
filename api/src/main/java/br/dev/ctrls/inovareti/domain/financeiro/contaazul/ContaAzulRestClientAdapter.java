package br.dev.ctrls.inovareti.domain.financeiro.contaazul;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Adaptador de saída (Outbound Adapter) responsável pela coordenação da comunicação com a API da Conta Azul.
 * Aplica o throttling de 300ms, delega a busca física ao ContaAzulHttpClient e o parsing de JSON ao ContaAzulResponseParser.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContaAzulRestClientAdapter {

    private final ContaAzulHttpClient httpClient;
    private final ContaAzulResponseParser responseParser;

    /**
     * Recupera as contas financeiras registradas na Conta Azul.
     */
    public List<FinancialAccountRef> fetchFinancialAccounts(String accessToken) {
        applyThrottlingDelay();
        String rawJson = httpClient.fetchFinancialAccountsRaw(accessToken);
        return responseParser.parseFinancialAccounts(rawJson);
    }

    /**
     * Recupera o saldo atual bruto (BigDecimal) de uma determinada conta.
     */
    public BigDecimal fetchAccountCurrentBalance(String accessToken, String accountId) {
        applyThrottlingDelay();
        String rawJson = httpClient.fetchAccountCurrentBalanceRaw(accessToken, accountId);
        return responseParser.parseAccountCurrentBalance(rawJson, accountId);
    }

    /**
     * Recupera a lista de valores líquidos (BigDecimal) das baixas de uma parcela.
     */
    public List<BigDecimal> fetchParcelaBaixasValorLiquido(String accessToken, String parcelaId) {
        applyThrottlingDelay();
        String rawJson = httpClient.fetchParcelaBaixasValorLiquidoRaw(accessToken, parcelaId);
        return responseParser.parseParcelaBaixasValorLiquido(rawJson, parcelaId);
    }

    /**
     * Busca uma página de parcelas a receber filtrando por data de vencimento.
     */
    public ReceivablesPageData fetchReceivablesPageByDueDate(
            String accessToken,
            String status,
            int page,
            LocalDate dataVencimentoDe,
            LocalDate dataVencimentoAte) {
        applyThrottlingDelay();
        String rawJson = httpClient.executePaymentsRequestRaw(status, accessToken, page, dataVencimentoDe, dataVencimentoAte);
        return responseParser.parseReceivablesPage(rawJson);
    }

    /**
     * Busca uma página de parcelas a receber filtrando por data de pagamento (caixa).
     */
    public ReceivablesPageData fetchReceivablesPageByPaymentDate(
            String accessToken,
            String status,
            int page,
            LocalDate dataPagamentoDe,
            LocalDate dataPagamentoAte) {
        applyThrottlingDelay();
        String rawJson = httpClient.executePaymentsRequestByPaymentDateRaw(status, accessToken, page, dataPagamentoDe, dataPagamentoAte);
        return responseParser.parseReceivablesPage(rawJson);
    }

    /**
     * Recupera o total agregado de pagamentos direto pelo status informado para a página 1 (totais gerais do mês).
     */
    public BigDecimal fetchTotalAmountByStatus(String accessToken, String status) {
        applyThrottlingDelay();
        try {
            String rawJson = httpClient.executePaymentsRequestByStatusRaw(status, accessToken, 1);
            return responseParser.extractTotalDecimal(rawJson, status);
        } catch (HttpClientErrorException.Unauthorized ex) {
            String errorBody = ex.getResponseBodyAsString();
            log.warn("Token expirado ou inválido ao buscar pagamentos com status='{}'. Tentando refresh automático. Resposta: {}", status, errorBody);

            try {
                String newToken = httpClient.forceTokenRefresh();
                String rawJsonRetry = httpClient.executePaymentsRequestByStatusRaw(status, newToken, 1);
                return responseParser.extractTotalDecimal(rawJsonRetry, status);
            } catch (Exception refreshEx) {
                log.error("Refresh também falhou. Re-autorização manual necessária.", refreshEx);
                throw new ContaAzulAuthException("Token inválido e refresh falhou. Refaça o login na Conta Azul.", refreshEx);
            }
        } catch (HttpClientErrorException ex) {
            String errorBody = ex.getResponseBodyAsString();

            if (ex.getStatusCode().value() == 403) {
                log.warn("ContaAzul API retornou 403 FORBIDDEN ao buscar pagamentos com status='{}'. Resposta: {}", status, errorBody);
                return null; // Retorna nulo para indicar 403 / indisponível de forma segura
            }

            if (ex.getStatusCode().value() == 401) {
                log.error("ContaAzul API retornou 401 (Unauthorized) ao buscar pagamentos com status='{}'. Resposta da API: {}", status, errorBody, ex);
            } else {
                log.error("ContaAzul API retornou erro {} ao buscar pagamentos com status='{}'. Resposta: {}", ex.getStatusCode(), status, errorBody, ex);
            }

            throw new IllegalStateException("Falha ao recuperar resumo financeiro da Conta Azul [status=" + status + ", http=" + ex.getStatusCode() + "].", ex);
        }
    }

    /**
     * Aplica o delay de throttling anti-429 compatível com as Virtual Threads.
     */
    public void applyThrottlingDelay() {
        try {
            Thread.sleep(300L);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
