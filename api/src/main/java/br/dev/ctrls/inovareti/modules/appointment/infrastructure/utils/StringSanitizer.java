package br.dev.ctrls.inovareti.modules.appointment.infrastructure.utils;

public class StringSanitizer {

    public static final String UNICODE_LTR_MARK = "\u200E";

    /**
     * Sanitizes a string ensuring it is not null, empty, or the literal word "null".
     * If invalid, it returns the Unicode Left-to-Right Mark to bypass Meta validations.
     *
     * @param input the string to be sanitized
     * @return the sanitized string or \u200E
     */
    public static String sanitize(String input) {
        if (input == null || input.isBlank() || "null".equalsIgnoreCase(input.trim()) || "Informação não disponível".equalsIgnoreCase(input.trim())) {
            return UNICODE_LTR_MARK;
        }
        return input.trim();
    }

    /**
     * Purifica, auto-corrige e valida um número de telefone celular para WhatsApp no Brasil.
     *
     * Regras estritas:
     * 1. Limpeza de caracteres não numéricos.
     * 2. Remoção de DDI 55 duplicado (se tiver 12 ou 13 dígitos e iniciar por 55).
     * 3. Auto-correção do 9º dígito (10 dígitos com DDD -> insere '9' se 3º dígito for 6, 7, 8 ou 9).
     * 4. Validação de DDD (11-99) e tamanho estrito de 11 dígitos (DDD + 9 + 8 dígitos).
     *
     * @param rawPhone Número bruto fornecido
     * @return Número nacional purificado com 11 dígitos (ex: "42988044290") ou NULL se inválido/fixo/sem DDD.
     */
    public static String sanitizeAndValidatePhone(String rawPhone) {
        if (rawPhone == null || rawPhone.isBlank() || "null".equalsIgnoreCase(rawPhone.trim())) {
            return null;
        }

        String phone = rawPhone.trim();
        if (phone.contains("@")) {
            phone = phone.substring(0, phone.indexOf('@')).trim();
        }

        // 1. Limpeza de caracteres não numéricos
        String digits = phone.replaceAll("\\D+", "");
        if (digits.isEmpty()) {
            return null;
        }

        // 2. Remoção de DDI 55 duplicado (se tiver 12 ou 13 dígitos e iniciar por 55)
        if (digits.startsWith("55") && (digits.length() == 12 || digits.length() == 13)) {
            digits = digits.substring(2);
        }

        // 3. Auto-correção do 9º dígito (10 dígitos -> 11 dígitos)
        if (digits.length() == 10) {
            char firstNumDigit = digits.charAt(2);
            // Se o primeiro dígito do número (após o DDD) for 6, 7, 8 ou 9, insere o '9' do celular
            if (firstNumDigit == '6' || firstNumDigit == '7' || firstNumDigit == '8' || firstNumDigit == '9') {
                digits = digits.substring(0, 2) + "9" + digits.substring(2);
            }
        }

        // 4. Validação de DDD e Tamanho Mínimo (11 dígitos começando com DDD legítimo + 9)
        if (digits.length() == 11 && digits.charAt(2) == '9') {
            try {
                int dddVal = Integer.parseInt(digits.substring(0, 2));
                if (dddVal >= 11 && dddVal <= 99) {
                    return digits;
                }
            } catch (NumberFormatException ignored) {}
        }

        return null;
    }

    /**
     * Formata o número nacional purificado no padrão E.164 estrito (+55 + 11 dígitos).
     * Retorna NULL se o número não for um celular válido com 11 dígitos e DDD.
     *
     * @param rawPhone Número bruto fornecido
     * @return String formatada E.164 (ex: "+5542988044290", 14 caracteres) ou NULL.
     */
    public static String formatE164(String rawPhone) {
        String cleanNational = sanitizeAndValidatePhone(rawPhone);
        if (cleanNational != null && cleanNational.length() == 11) {
            return "+55" + cleanNational;
        }
        return null;
    }
}
