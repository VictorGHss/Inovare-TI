package br.dev.ctrls.inovareti.modules.appointment.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO que representa o médico ou serviço encontrado pelo Motor de Intenções.
 * Formatado para consumo imediato no Take Blip Builder.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DoctorMatchDto {

    private String doctorName;
    private String specialty;
    private Boolean isInternal;
    private String route;
    private String queue;
    private String externalPhone;
    private String externalLink;
    private Boolean isSynthetic;
    private Integer nextPage;
}
