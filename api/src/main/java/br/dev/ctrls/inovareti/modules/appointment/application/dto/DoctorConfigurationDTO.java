package br.dev.ctrls.inovareti.modules.appointment.application.dto;

import br.dev.ctrls.inovareti.modules.appointment.domain.model.DoctorConfiguration;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para transferência de dados de configuração de médicos.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DoctorConfigurationDTO {
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

    public static DoctorConfigurationDTO fromDomain(DoctorConfiguration domain) {
        if (domain == null) return null;
        return DoctorConfigurationDTO.builder()
                .feegowProfissionalId(domain.getFeegowProfissionalId())
                .doctorName(domain.getDoctorName())
                .gerAcessoMatricula(domain.getGerAcessoMatricula())
                .gerAcessoCpf(domain.getGerAcessoCpf())
                .blipQueueId(domain.getBlipQueueId())
                .blipQueueName(domain.getBlipQueueName())
                .displayTimeOffsetMinutes(domain.getDisplayTimeOffsetMinutes())
                .advanceNoticeDays(domain.getAdvanceNoticeDays())
                .isActive(domain.getIsActive())
                .googleReviewUrl(domain.getGoogleReviewUrl())
                .build();
    }

    public DoctorConfiguration toDomain() {
        return DoctorConfiguration.builder()
                .feegowProfissionalId(this.feegowProfissionalId)
                .doctorName(this.doctorName)
                .gerAcessoMatricula(this.gerAcessoMatricula)
                .gerAcessoCpf(this.gerAcessoCpf)
                .blipQueueId(this.blipQueueId)
                .blipQueueName(this.blipQueueName)
                .displayTimeOffsetMinutes(this.displayTimeOffsetMinutes)
                .advanceNoticeDays(this.advanceNoticeDays)
                .isActive(this.isActive)
                .googleReviewUrl(this.googleReviewUrl)
                .build();
    }
}
