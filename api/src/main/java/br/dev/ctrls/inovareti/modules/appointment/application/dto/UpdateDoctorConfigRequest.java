package br.dev.ctrls.inovareti.modules.appointment.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para atualização individual ou em lote das configurações do médico.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateDoctorConfigRequest {
    private Long feegowProfissionalId;
    private String doctorName;
    private String gerAcessoMatricula;
    private String gerAcessoCpf;
    private String blipQueueId;
    private String blipQueueName;
    private Integer displayTimeOffsetMinutes;
    private Integer advanceNoticeDays;
    private Boolean isActive;
    private String googleReviewUrl;
}
