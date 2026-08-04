package br.dev.ctrls.inovareti.modules.appointment.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * DTO de resposta para consulta de agendamentos futuros via webhook Blip.
 * Comentários mantidos em PT-BR pelas Regras de Ouro.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PatientAppointmentsResponse {

    @JsonProperty("status")
    private String status;

    @JsonProperty("total")
    private Integer total;

    @JsonProperty("mensagem")
    private String mensagem;

    @JsonProperty("agendamentos")
    private List<AppointmentItem> agendamentos;

    /**
     * Item formatado contendo os detalhes de um agendamento futuro.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AppointmentItem {

        @JsonProperty("agendamento_id")
        private String agendamentoId;

        @JsonProperty("data")
        private String data;

        @JsonProperty("hora")
        private String hora;

        @JsonProperty("medico")
        private String medico;

        @JsonProperty("especialidade")
        private String especialidade;

        @JsonProperty("unidade")
        private String unidade;
    }
}
