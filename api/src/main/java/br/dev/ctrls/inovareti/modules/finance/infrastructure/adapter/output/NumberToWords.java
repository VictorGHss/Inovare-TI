package br.dev.ctrls.inovareti.modules.finance.infrastructure.adapter.output;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Conversor simples de valores monetarios para extenso em portugues (pt-BR).
 */
public final class NumberToWords {

    private static final java.util.Locale PT_BR = java.util.Locale.forLanguageTag("pt-BR");

    private static final String[] UNITS = {
            "zero", "um", "dois", "tres", "quatro", "cinco", "seis", "sete", "oito", "nove"
    };

    private static final String[] TEENS = {
            "dez", "onze", "doze", "treze", "quatorze", "quinze", "dezesseis", "dezessete", "dezoito", "dezenove"
    };

    private static final String[] TENS = {
            "", "", "vinte", "trinta", "quarenta", "cinquenta", "sessenta", "setenta", "oitenta", "noventa"
    };

    private static final String[] HUNDREDS = {
            "", "cento", "duzentos", "trezentos", "quatrocentos", "quinhentos", "seiscentos", "setecentos", "oitocentos", "novecentos"
    };

    private NumberToWords() {
    }

    public static String toBrazilianCurrencyWords(BigDecimal value) {
        BigDecimal normalized = value == null
                ? BigDecimal.ZERO
                : value.setScale(2, RoundingMode.HALF_UP).abs();

        long reais = normalized.longValue();
        int centavos = normalized
                .subtract(BigDecimal.valueOf(reais))
                .movePointRight(2)
                .intValue();

        StringBuilder result = new StringBuilder();

        if (reais > 0) {
            result.append(numberToWords(reais));
            result.append(reais == 1 ? " real" : " reais");
        }

        if (centavos > 0) {
            if (result.length() > 0) {
                result.append(" e ");
            }
            result.append(numberToWords(centavos));
            result.append(centavos == 1 ? " centavo" : " centavos");
        }

        if (result.length() == 0) {
            result.append("zero real");
        }

        return capitalize(result.toString());
    }

    private static String numberToWords(long value) {
        if (value == 0) {
            return "zero";
        }

        if (value < 1000) {
            return convertHundreds((int) value);
        }

        if (value < 1_000_000) {
            long thousands = value / 1000;
            long remainder = value % 1000;

            String thousandPart = thousands == 1 ? "mil" : numberToWords(thousands) + " mil";
            return appendRemainder(thousandPart, remainder);
        }

        if (value < 1_000_000_000) {
            long millions = value / 1_000_000;
            long remainder = value % 1_000_000;

            String millionPart = millions == 1
                    ? "um milhao"
                    : numberToWords(millions) + " milhoes";

            return appendRemainder(millionPart, remainder);
        }

        long billions = value / 1_000_000_000;
        long remainder = value % 1_000_000_000;

        String billionPart = billions == 1
                ? "um bilhao"
                : numberToWords(billions) + " bilhoes";

        return appendRemainder(billionPart, remainder);
    }

    private static String appendRemainder(String prefix, long remainder) {
        if (remainder == 0) {
            return prefix;
        }

        if (remainder < 100) {
            return prefix + " e " + numberToWords(remainder);
        }

        return prefix + ", " + numberToWords(remainder);
    }

    private static String convertHundreds(int value) {
        if (value == 100) {
            return "cem";
        }

        int hundreds = value / 100;
        int remainder = value % 100;

        StringBuilder result = new StringBuilder();
        if (hundreds > 0) {
            result.append(HUNDREDS[hundreds]);
        }

        if (remainder > 0) {
            if (result.length() > 0) {
                result.append(" e ");
            }
            result.append(convertTens(remainder));
        }

        return result.toString();
    }

    private static String convertTens(int value) {
        if (value < 10) {
            return UNITS[value];
        }

        if (value < 20) {
            return TEENS[value - 10];
        }

        int tens = value / 10;
        int units = value % 10;

        if (units == 0) {
            return TENS[tens];
        }

        return TENS[tens] + " e " + UNITS[units];
    }

    private static String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }

        String normalized = value.trim();
        return normalized.substring(0, 1).toUpperCase(PT_BR) + normalized.substring(1);
    }
}

