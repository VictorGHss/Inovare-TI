package br.dev.ctrls.inovareti.domain.appointment.dto;

public record AppointmentTemplateMappingResponse(
        String templateName,
        Integer placeholderIndex,
        String feegowFieldName) {
}