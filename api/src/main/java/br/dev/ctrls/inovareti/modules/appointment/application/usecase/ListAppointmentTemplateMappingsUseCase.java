package br.dev.ctrls.inovareti.modules.appointment.application.usecase;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentTemplateMappingRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.application.dto.AppointmentTemplateMappingResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ListAppointmentTemplateMappingsUseCase {

    private final AppointmentTemplateMappingRepositoryPort appointmentTemplateMappingRepository;

    public List<AppointmentTemplateMappingResponse> execute(String templateName) {
        String normalizedTemplateName = normalizeTemplateName(templateName);
        if (!StringUtils.hasText(normalizedTemplateName)) {
            throw new IllegalArgumentException("templateName é obrigatório");
        }

        return appointmentTemplateMappingRepository
                .findByTemplateNameIgnoreCaseOrderByPlaceholderIndexAsc(normalizedTemplateName)
                .stream()
                .map(mapping -> new AppointmentTemplateMappingResponse(
                        mapping.getTemplateName(),
                        mapping.getPlaceholderIndex(),
                        mapping.getFeegowFieldName()))
                .toList();
    }

    private String normalizeTemplateName(String templateName) {
        if (!StringUtils.hasText(templateName)) {
            return "";
        }
        return templateName.trim();
    }
}