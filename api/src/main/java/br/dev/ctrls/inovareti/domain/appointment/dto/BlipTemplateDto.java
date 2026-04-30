package br.dev.ctrls.inovareti.domain.appointment.dto;

/**
 * DTO para apresentação de templates aprovados ao frontend
 */
public record BlipTemplateDto(
        String id,
        String name,
        String body) {
}
