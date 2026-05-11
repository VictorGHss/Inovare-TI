package br.dev.ctrls.inovareti.domain.appointment.utils;

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
}
