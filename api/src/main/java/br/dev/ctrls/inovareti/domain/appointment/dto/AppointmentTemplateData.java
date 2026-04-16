package br.dev.ctrls.inovareti.domain.appointment.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

public record AppointmentTemplateData(
        String appointmentId,
        String patientId,
        String patientName,
        String patientPhone,
        String doctorId,
        String doctorName,
        String specialty,
        String unitName,
        String appointmentDate,
        String appointmentTime,
        String appointmentDateTime) {

        private static final DateTimeFormatter DATE_ONLY_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        private static final List<DateTimeFormatter> DATE_TIME_INPUT_FORMATTERS = List.of(
                        DateTimeFormatter.ISO_DATE_TIME,
                        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"),
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        private static final List<DateTimeFormatter> DATE_INPUT_FORMATTERS = List.of(
                        DateTimeFormatter.ISO_DATE,
                        DateTimeFormatter.ofPattern("dd/MM/yyyy"));

        public AppointmentTemplateData {
                appointmentDateTime = normalizeToDateOnly(appointmentDateTime);
        }

        private static String normalizeToDateOnly(String rawValue) {
                if (rawValue == null || rawValue.isBlank()) {
                        return "";
                }

                String value = rawValue.trim();

                for (DateTimeFormatter formatter : DATE_TIME_INPUT_FORMATTERS) {
                        try {
                                LocalDateTime parsed = LocalDateTime.parse(value, formatter);
                                return parsed.toLocalDate().format(DATE_ONLY_FORMATTER);
                        } catch (DateTimeParseException ignored) {
                                // Tenta o próximo formato.
                        }
                }

                for (DateTimeFormatter formatter : DATE_INPUT_FORMATTERS) {
                        try {
                                LocalDate parsed = LocalDate.parse(value, formatter);
                                return parsed.format(DATE_ONLY_FORMATTER);
                        } catch (DateTimeParseException ignored) {
                                // Tenta o próximo formato.
                        }
                }

                return value;
        }
}