package br.dev.ctrls.inovareti.domain.appointment;

import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

@Component
public class VariableResolver {

    private static final DateTimeFormatter BRAZILIAN_DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final Map<String, VariableDefinition> glossary;

    public VariableResolver() {
        Map<String, VariableDefinition> initial = new LinkedHashMap<>();
        initial.put("PACIENTE_NOME", new VariableDefinition("PACIENTE_NOME", "$.paciente.nome", "Nome completo do paciente"));
        initial.put("PACIENTE_TELEFONE", new VariableDefinition("PACIENTE_TELEFONE", "$.paciente.telefone", "Telefone principal do paciente"));
        initial.put("PACIENTE_ID", new VariableDefinition("PACIENTE_ID", "$.paciente.id", "Identificador do paciente no Feegow"));
        initial.put("AGENDAMENTO_ID", new VariableDefinition("AGENDAMENTO_ID", "$.agendamento.id", "Identificador da consulta no Feegow"));
        initial.put("AGENDAMENTO_DATA", new VariableDefinition("AGENDAMENTO_DATA", "$.agendamento.data", "Data e hora da consulta"));
        initial.put("MEDICO_NOME", new VariableDefinition("MEDICO_NOME", "$.agendamento.medico.nome", "Nome do profissional da consulta"));
        initial.put("UNIDADE_NOME", new VariableDefinition("UNIDADE_NOME", "$.agendamento.unidade", "Unidade de atendimento"));
        this.glossary = Map.copyOf(initial);
    }

    public Collection<VariableDefinition> glossary() {
        return glossary.values();
    }

    public String resolve(String key, Map<String, Object> context) {
        VariableDefinition variable = glossary.get(key);
        if (variable == null) {
            return "";
        }

        Object value = extractByPath(context, variable.path());
        if (value == null) {
            return "";
        }

        if (value instanceof java.time.LocalDateTime dateTime) {
            return dateTime.format(BRAZILIAN_DATE_TIME);
        }

        if (value instanceof java.time.LocalDate date) {
            return date.atStartOfDay().format(BRAZILIAN_DATE_TIME);
        }

        return String.valueOf(value);
    }

    private Object extractByPath(Map<String, Object> context, String path) {
        if (context == null || !path.startsWith("$.")) {
            return null;
        }

        Object current = context;
        String[] nodes = path.substring(2).split("\\.");
        for (String node : nodes) {
            if (!(current instanceof Map<?, ?> currentMap)) {
                return null;
            }
            current = currentMap.get(node);
            if (current == null) {
                return null;
            }
        }

        return current;
    }

    public record VariableDefinition(String key, String path, String description) {
    }

    public Map<String, Object> buildContext(FeegowClient.FeegowAppointment appointment, FeegowClient.FeegowPatient patient) {
        Map<String, Object> context = new LinkedHashMap<>();
        Map<String, Object> agendamento = new LinkedHashMap<>();
        Map<String, Object> medico = new LinkedHashMap<>();
        Map<String, Object> paciente = new LinkedHashMap<>();

        agendamento.put("id", appointment.id());
        agendamento.put("data", appointment.startAt());
        agendamento.put("unidade", appointment.unitName());
        medico.put("nome", appointment.doctorName());
        agendamento.put("medico", medico);

        paciente.put("id", patient.id());
        paciente.put("nome", Optional.ofNullable(patient.name()).orElse(""));
        paciente.put("telefone", Optional.ofNullable(patient.phone()).orElse(""));

        context.put("agendamento", agendamento);
        context.put("paciente", paciente);
        return context;
    }
}
