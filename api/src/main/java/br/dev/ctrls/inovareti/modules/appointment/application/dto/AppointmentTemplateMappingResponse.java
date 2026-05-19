package br.dev.ctrls.inovareti.modules.appointment.application.dto;

public record AppointmentTemplateMappingResponse(
        String templateName,
        Integer placeholderIndex,
        String feegowFieldName) {
}