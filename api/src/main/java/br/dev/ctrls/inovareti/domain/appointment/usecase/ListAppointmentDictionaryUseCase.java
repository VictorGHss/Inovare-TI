package br.dev.ctrls.inovareti.domain.appointment.usecase;

import java.util.List;

import org.springframework.stereotype.Component;

import br.dev.ctrls.inovareti.domain.appointment.VariableResolver;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ListAppointmentDictionaryUseCase {

    private final VariableResolver variableResolver;

    public List<DictionaryItem> execute() {
        return variableResolver.glossary().stream()
                .map(item -> new DictionaryItem(item.key(), item.path(), item.description()))
                .toList();
    }

    public record DictionaryItem(String key, String path, String description) {
    }
}
