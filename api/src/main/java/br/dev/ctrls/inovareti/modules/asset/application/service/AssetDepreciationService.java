package br.dev.ctrls.inovareti.modules.asset.application.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

/**
 * Serviço responsável pelo cálculo contábil de parcelas e depreciação linear de ativos imobilizados.
 * Utiliza BigDecimal e a regra do banqueiro (RoundingMode.HALF_EVEN) para precisão centesimal e
 * prevenção de anomalias de arredondamento e perdas de centavos.
 */
@Service
@lombok.extern.slf4j.Slf4j
public class AssetDepreciationService {

    /**
     * Divide um valor total em parcelas de forma justa, ajustando a última parcela 
     * para garantir que a soma das parcelas bata exatamente com o valor total do ativo.
     * Utiliza RoundingMode.HALF_EVEN para os cálculos de arredondamento.
     *
     * @param totalValue Valor total do ativo.
     * @param installmentCount Quantidade de parcelas.
     * @return Lista com os valores de cada parcela.
     */
    public List<BigDecimal> calculateInstallments(BigDecimal totalValue, int installmentCount) {
        log.info("[CONTABILIDADE] Calculando divisão de parcelas. Valor total: {}, Parcelas: {}", totalValue, installmentCount);

        if (totalValue == null) {
            return List.of();
        }
        if (installmentCount <= 0) {
            throw new IllegalArgumentException("A quantidade de parcelas deve ser maior que zero.");
        }

        List<BigDecimal> amounts = new ArrayList<>();
        BigDecimal count = BigDecimal.valueOf(installmentCount);
        
        // Divide o valor total pelo número de parcelas usando a escala de 2 casas decimais e RoundingMode.HALF_EVEN
        BigDecimal baseAmount = totalValue.divide(count, 2, RoundingMode.HALF_EVEN);
        
        BigDecimal accumulated = BigDecimal.ZERO;
        for (int i = 0; i < installmentCount - 1; i++) {
            amounts.add(baseAmount);
            accumulated = accumulated.add(baseAmount);
        }
        
        // A última parcela absorve a diferença residual de centavos para fechar o valor exato
        BigDecimal finalAmount = totalValue.subtract(accumulated);
        amounts.add(finalAmount);
        
        return amounts;
    }

    /**
     * Calcula a depreciação mensal linear de um ativo imobilizado com base no valor de aquisição
     * e no tempo de vida útil em meses.
     *
     * @param acquisitionValue Valor de aquisição do ativo imobilizado.
     * @param usefulLifeMonths Tempo de vida útil estimado em meses.
     * @return Valor da depreciação mensal arredondado usando a regra do banqueiro.
     */
    public BigDecimal calculateMonthlyDepreciation(BigDecimal acquisitionValue, int usefulLifeMonths) {
        log.info("[CONTABILIDADE] Calculando depreciação mensal. Valor aquisição: {}, Vida útil em meses: {}", acquisitionValue, usefulLifeMonths);

        if (acquisitionValue == null || usefulLifeMonths <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal months = BigDecimal.valueOf(usefulLifeMonths);
        return acquisitionValue.divide(months, 2, RoundingMode.HALF_EVEN);
    }
}
