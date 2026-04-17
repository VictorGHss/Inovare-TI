package br.dev.ctrls.inovareti.domain.appointment.usecase;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import br.dev.ctrls.inovareti.domain.appointment.AppointmentTemplateMapping;
import br.dev.ctrls.inovareti.domain.appointment.AppointmentTemplateMappingRepository;
import br.dev.ctrls.inovareti.domain.appointment.dto.SaveAppointmentTemplateMappingsRequest;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SaveAppointmentTemplateMappingsUseCase {

    private final AppointmentTemplateMappingRepository appointmentTemplateMappingRepository;

    @Transactional
    public int execute(SaveAppointmentTemplateMappingsRequest request) {
        String templateName = normalizeTemplateName(request.templateName());
        if (!StringUtils.hasText(templateName)) {
            throw new IllegalArgumentException("templateName é obrigatório");
        }

        List<SaveAppointmentTemplateMappingsRequest.TemplateMappingItem> mappings = request.mappings();
        if (mappings == null || mappings.isEmpty()) {
            throw new IllegalArgumentException("mappings é obrigatório");
        }

        Map<Integer, SaveAppointmentTemplateMappingsRequest.TemplateMappingItem> normalizedMappings = mappings.stream()
                .filter(item -> item != null && item.placeholderIndex() != null && StringUtils.hasText(item.feegowFieldName()))
                .collect(Collectors.toMap(
                        SaveAppointmentTemplateMappingsRequest.TemplateMappingItem::placeholderIndex,
                        Function.identity(),
                        (previous, current) -> current,
                        LinkedHashMap::new));

        List<AppointmentTemplateMapping> existingMappings = appointmentTemplateMappingRepository
                .findByTemplateNameOrderByPlaceholderIndexAsc(templateName);

        Map<Integer, AppointmentTemplateMapping> existingByPlaceholderIndex = existingMappings.stream()
                .collect(Collectors.toMap(
                        AppointmentTemplateMapping::getPlaceholderIndex,
                        Function.identity(),
                        (previous, current) -> previous,
                        LinkedHashMap::new));

        List<AppointmentTemplateMapping> entitiesToPersist = new ArrayList<>();
        normalizedMappings.values().stream()
                .sorted(Comparator.comparing(SaveAppointmentTemplateMappingsRequest.TemplateMappingItem::placeholderIndex))
                .forEach(item -> {
                    AppointmentTemplateMapping existing = existingByPlaceholderIndex.remove(item.placeholderIndex());
                    if (existing != null) {
                        existing.setTemplateName(templateName);
                        existing.setFeegowFieldName(item.feegowFieldName().trim());
                        entitiesToPersist.add(existing);
                        return;
                    }

                    entitiesToPersist.add(AppointmentTemplateMapping.builder()
                            .templateName(templateName)
                            .placeholderIndex(item.placeholderIndex())
                            .feegowFieldName(item.feegowFieldName().trim())
                            .build());
                });

        if (!existingByPlaceholderIndex.isEmpty()) {
            appointmentTemplateMappingRepository.deleteAll(existingByPlaceholderIndex.values());
            appointmentTemplateMappingRepository.flush();
        }

        appointmentTemplateMappingRepository.saveAllAndFlush(entitiesToPersist);
        return entitiesToPersist.size();
    }

    private String normalizeTemplateName(String templateName) {
        if (!StringUtils.hasText(templateName)) {
            return "";
        }
        return templateName.trim();
    }
}