package br.dev.ctrls.inovareti.modules.appointment.application.dto;

import java.util.List;

public record SaveAppointmentTemplateMappingsRequest(
        String templateName,
        List<TemplateMappingItem> mappings) {

    public record TemplateMappingItem(
            Integer placeholderIndex,
            String feegowFieldName) {
    }
}