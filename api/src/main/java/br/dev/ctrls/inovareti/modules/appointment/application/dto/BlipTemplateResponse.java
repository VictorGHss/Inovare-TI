package br.dev.ctrls.inovareti.modules.appointment.application.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * DTO para resposta da API de templates do Blip
 * Representa a estrutura JSON-RPC retornada pelo endpoint /message-templates
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BlipTemplateResponse(
        String id,
        String method,
        BlipTemplateResource resource,
        String status) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BlipTemplateResource(
            Integer total,
            List<BlipTemplate> documents) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BlipTemplate(
            String id,
            String name,
            String status,
            String content,
            String body) {
    }
}
