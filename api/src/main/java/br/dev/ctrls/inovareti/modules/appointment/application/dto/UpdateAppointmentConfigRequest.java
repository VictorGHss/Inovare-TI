package br.dev.ctrls.inovareti.modules.appointment.application.dto;

import org.springframework.util.StringUtils;

import com.fasterxml.jackson.annotation.JsonAlias;

/**
 * DTO para requisição de atualização de configuração de template
 */
public record UpdateAppointmentConfigRequest(
                @JsonAlias("templateId")
                String templateName,
                @JsonAlias("templateName")
                String templateId) {

        public String resolvedTemplateName() {
                if (StringUtils.hasText(templateName)) {
                        return templateName.trim();
                }

                if (StringUtils.hasText(templateId)) {
                        return templateId.trim();
                }

                return "";
        }
}
