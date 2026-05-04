package br.dev.ctrls.inovareti.domain.appointment.dto;

import java.util.regex.Pattern;

/**
 * DTO para apresentação de templates aprovados ao frontend
 */
public record BlipTemplateDto(
        String id,
        String name,
        String body) {

        private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{\\d+\\}\\}");

        public int getVariableCount() {
                if (body == null || body.isBlank()) {
                        return 0;
                }

                int count = 0;
                var matcher = VARIABLE_PATTERN.matcher(body);
                while (matcher.find()) {
                        count++;
                }
                return count;
        }
}
