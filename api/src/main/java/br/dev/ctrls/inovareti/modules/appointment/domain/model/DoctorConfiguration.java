package br.dev.ctrls.inovareti.modules.appointment.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Modelo de domínio para as configurações do médico.
 * Desacoplado de frameworks e persistência.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DoctorConfiguration {
    private Long feegowProfissionalId;
    private String doctorName;
    private String gerAcessoMatricula;
    private String gerAcessoCpf;
    private String blipQueueId;
    private String blipQueueName;
    @Builder.Default
    private Integer displayTimeOffsetMinutes = 0;
    @Builder.Default
    private Integer advanceNoticeDays = 1;

    public int getResolvedDisplayTimeOffsetMinutes() {
        return displayTimeOffsetMinutes != null ? displayTimeOffsetMinutes : 0;
    }

    public int getResolvedAdvanceNoticeDays() {
        return (advanceNoticeDays != null && advanceNoticeDays > 0) ? advanceNoticeDays : 1;
    }
}
