package br.dev.ctrls.inovareti.modules.appointment.application.util;

/**
 * Utilitária especialista na extração do hash/código final de URLs do Google Review.
 */
public class GoogleReviewUrlUtils {

    public static final String DEFAULT_FALLBACK_URL = "https://share.google/OBtREC0KLjzx1YNOP";
    public static final String DEFAULT_REVIEW_HASH = "OBtREC0KLjzx1YNOP";

    private GoogleReviewUrlUtils() {}

    /**
     * Extrai o código/hash final de uma URL do Google Review.
     * Exemplo: "https://share.google/jrskH337hFK5Mn3WP" -> "jrskH337hFK5Mn3WP"
     * Se a string já for apenas o código "jrskH337hFK5Mn3WP", retorne-a limpa.
     * Se nula ou em branco, retorna o código fallback padrão "jrskH337hFK5Mn3WP".
     *
     * @param urlOrHash URL completa do Google Review ou o próprio código hash.
     * @return O código hash purificado.
     */
    public static String extractHash(String urlOrHash) {
        if (urlOrHash == null || urlOrHash.isBlank()) {
            return DEFAULT_REVIEW_HASH;
        }

        String trimmed = urlOrHash.trim();

        if (trimmed.contains("?")) {
            trimmed = trimmed.substring(0, trimmed.indexOf('?')).trim();
        }
        if (trimmed.contains("#")) {
            trimmed = trimmed.substring(0, trimmed.indexOf('#')).trim();
        }

        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }

        if (trimmed.contains("/")) {
            int lastSlash = trimmed.lastIndexOf('/');
            String lastSegment = trimmed.substring(lastSlash + 1).trim();
            if (!lastSegment.isBlank()) {
                return lastSegment;
            }
        }

        return trimmed.isBlank() ? DEFAULT_REVIEW_HASH : trimmed;
    }
}
