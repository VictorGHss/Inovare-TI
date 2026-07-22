package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.DoctorConfiguration;

/**
 * Entidade JPA DoctorConfigurationEntity. Mapeia a tabela doctor_configurations.
 */
@Entity
@Table(name = "doctor_configurations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DoctorConfigurationEntity {

    @Id
    @Column(name = "feegow_profissional_id")
    private Long feegowProfissionalId;

    @Column(name = "doctor_name")
    private String doctorName;

    @Column(name = "ger_acesso_matricula")
    private String gerAcessoMatricula;

    @Column(name = "ger_acesso_cpf")
    private String gerAcessoCpf;

    @Column(name = "blip_queue_id")
    private String blipQueueId;

    @Column(name = "blip_queue_name")
    private String blipQueueName;

    @Column(name = "display_time_offset_minutes")
    private Integer displayTimeOffsetMinutes;

    @Column(name = "advance_notice_days")
    private Integer advanceNoticeDays;

    public DoctorConfiguration toDomain() {
        return DoctorConfiguration.builder()
                .feegowProfissionalId(this.feegowProfissionalId)
                .doctorName(this.doctorName)
                .gerAcessoMatricula(this.gerAcessoMatricula)
                .gerAcessoCpf(this.gerAcessoCpf)
                .blipQueueId(this.blipQueueId)
                .blipQueueName(this.blipQueueName)
                .displayTimeOffsetMinutes(this.displayTimeOffsetMinutes != null ? this.displayTimeOffsetMinutes : 0)
                .advanceNoticeDays(this.advanceNoticeDays != null ? this.advanceNoticeDays : 1)
                .build();
    }

    public static DoctorConfigurationEntity fromDomain(DoctorConfiguration domain) {
        if (domain == null) return null;
        return DoctorConfigurationEntity.builder()
                .feegowProfissionalId(domain.getFeegowProfissionalId())
                .doctorName(domain.getDoctorName())
                .gerAcessoMatricula(domain.getGerAcessoMatricula())
                .gerAcessoCpf(domain.getGerAcessoCpf())
                .blipQueueId(domain.getBlipQueueId())
                .blipQueueName(domain.getBlipQueueName())
                .displayTimeOffsetMinutes(domain.getDisplayTimeOffsetMinutes())
                .advanceNoticeDays(domain.getAdvanceNoticeDays())
                .build();
    }
}
