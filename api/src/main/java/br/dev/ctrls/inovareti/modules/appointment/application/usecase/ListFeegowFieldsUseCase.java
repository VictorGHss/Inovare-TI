package br.dev.ctrls.inovareti.modules.appointment.application.usecase;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Component;

import br.dev.ctrls.inovareti.modules.appointment.application.dto.AppointmentTemplateData;

@Component
public class ListFeegowFieldsUseCase {

    public List<String> execute() {
        RecordComponent[] components = AppointmentTemplateData.class.getRecordComponents();
        if (components == null) {
            return List.of();
        }

        return Arrays.stream(components)
                .map(component -> component.getName())
                .sorted()
                .toList();
    }
}