package br.dev.ctrls.inovareti.domain.appointment.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FeegowSearchResponseDto(
        @JsonProperty("content") List<FeegowSearchAppointmentDto> content,
        @JsonProperty("data") List<FeegowSearchAppointmentDto> data) {

    public List<FeegowSearchAppointmentDto> appointments() {
        if (content != null && !content.isEmpty()) {
            return content;
        }

        if (data != null && !data.isEmpty()) {
            return data;
        }

        if (content != null) {
            return content;
        }

        if (data != null) {
            return data;
        }

        return List.of();
    }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record FeegowSearchAppointmentDto(
            @JsonProperty("agendamento_id") @JsonAlias("id") Object appointmentId,
            @JsonProperty("paciente_id") String patientId,
            @JsonProperty("profissional_id") String doctorId,
            @JsonProperty("nome") String doctorName, // Mapeia para a chave 'nome' do JSON
            @JsonProperty("unidade") String unitName,
            @JsonProperty("data") String appointmentDate,
            @JsonProperty("horario") String appointmentTime,
            @JsonProperty("status_id") String statusId) {
        }
}
