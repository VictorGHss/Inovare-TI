package br.dev.ctrls.inovareti.modules.notification.infrastructure.adapter.output.discord;

import org.springframework.util.StringUtils;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Utilitário de sanitização para conformidade com a LGPD.
 * Remove ou mascara dados pessoais, financeiros e de saúde de pacientes antes de serem enviados ao Discord.
 */
public class DiscordLgpdSanitizer {

    // Regex para CPF (com ou sem formatação)
    private static final Pattern CPF_PATTERN = Pattern.compile("\\b\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}\\b|\\b\\d{11}\\b");

    // Regex para Cartão de Crédito
    private static final Pattern CREDIT_CARD_PATTERN = Pattern.compile("\\b\\d{4}[-\\s]?\\d{4}[-\\s]?\\d{4}[-\\s]?\\d{4}\\b");

    // Regex para Telefone Comercial/Celular
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?:\\+?55\\s?)?(?:\\(?\\d{2}\\)?\\s?)?(?:9\\d{4}|\\d{4})[-\\s]?\\d{4}");

    // Regex para Valores Financeiros (ex: R$ 1.500,00 ou R$ 150)
    private static final Pattern MONEY_PATTERN = Pattern.compile("(?i)R\\$\\s?\\d+(?:\\.\\d{3})*(?:,\\d{2})?");

    // Regex para identificar menção estrita a nome de paciente por rótulo explícito (ex: "paciente: João da Silva" ou "nome do paciente: Maria")
    private static final Pattern PATIENT_NAME_PATTERN = Pattern.compile(
            "(?i)(?:paciente|prontuário|nome\\s+do\\s+paciente)\\s*:\\s*([A-ZÀ-ÿ][a-zÀ-ÿ]+(?:\\s+(?:de|do|da|dos|das|e)\\s+)?(?:[A-ZÀ-ÿ][a-zÀ-ÿ]+){1,3})"
    );

    // Lista de palavras estritamente sensíveis de diagnósticos clínicos e doenças graves
    private static final String[] HEALTH_WORDS = {
            "câncer", "cisto", "cardiopatia", "hipertensão", "infarto", "diabetes", "hiv", "depressão",
            "síndrome", "oncologia", "psiquiatria", "tumor", "patologia"
    };

    /**
     * Sanitiza o texto de entrada, substituindo dados sensíveis por marcadores.
     *
     * @param text O texto a ser higienizado.
     * @return O texto higienizado, ou a própria string se vazia/nula.
     */
    public static String sanitize(String text) {
        if (!StringUtils.hasText(text)) {
            return text;
        }

        String sanitized = text;

        // 1. Mascarar Nomes de Pacientes detectados por contexto
        Matcher patientMatcher = PATIENT_NAME_PATTERN.matcher(sanitized);
        StringBuilder sb = new StringBuilder();
        while (patientMatcher.find()) {
            String name = patientMatcher.group(1);
            // Ignora se for falso positivo com termos comuns da TI/sistema
            if (name.trim().equalsIgnoreCase("TI") || name.trim().equalsIgnoreCase("Insumo") || name.trim().equalsIgnoreCase("Chamado")) {
                continue;
            }
            patientMatcher.appendReplacement(sb, patientMatcher.group().replace(name, "[PACIENTE ANÔNIMO]"));
        }
        patientMatcher.appendTail(sb);
        sanitized = sb.toString();

        // 2. Mascarar CPFs
        sanitized = CPF_PATTERN.matcher(sanitized).replaceAll("[CPF MASCARADO]");

        // 3. Mascarar Cartões de Crédito
        sanitized = CREDIT_CARD_PATTERN.matcher(sanitized).replaceAll("[CARTÃO MASCARADO]");

        // 4. Mascarar Telefones
        sanitized = PHONE_PATTERN.matcher(sanitized).replaceAll("[TELEFONE MASCARADO]");

        // 5. Mascarar Valores Monetários
        sanitized = MONEY_PATTERN.matcher(sanitized).replaceAll("[VALOR MASCARADO]");

        // 6. Mascarar Termos Clínicos / Diagnósticos de Saúde
        for (String word : HEALTH_WORDS) {
            sanitized = sanitized.replaceAll("(?i)\\b" + word + "\\b", "[DADO CLÍNICO MASCARADO]");
        }

        return sanitized;
    }
}
