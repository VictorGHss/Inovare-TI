package br.dev.ctrls.inovareti.domain.appointment.usecase;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Component;

import br.dev.ctrls.inovareti.domain.appointment.dto.AppointmentTemplateData;

@Component
public class ListFeegowFieldsUseCase {

    public List<String> execute() {
        RecordComponent[] components = AppointmentTemplateData.class.getRecordComponents();
        if (components == null) {
            return List.of();
        }

        return Arrays.stream(components)
                .map(RecordComponent::getName)
                .sorted()
                .toList();
    }
}