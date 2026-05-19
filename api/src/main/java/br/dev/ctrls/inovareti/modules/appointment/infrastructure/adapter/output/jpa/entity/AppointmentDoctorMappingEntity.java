package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentDoctorMapping;

@Entity
@Table(name = "appointment_doctor_mapping")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentDoctorMappingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "profissional_id", nullable = false, length = 64, unique = true)
    private String profissionalId;

    @Column(name = "profissional_nome", length = 255)
    private String profissionalNome;

    @Column(name = "secretary_names", length = 255)
    private String secretaryNames;

    @Column(name = "blip_queue_id", nullable = false, length = 120)
    private String blipQueueId;

    @Column(name = "is_external", nullable = false)
    private boolean external;

    @Column(name = "itsm_user_id", length = 120)
    private String itsmUserId;

    @Column(name = "discord_webhook_url", length = 500)
    private String discordWebhookUrl;

    @Column(name = "external_wa_link", length = 500)
    private String externalWaLink;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public AppointmentDoctorMapping toDomain() {
        return AppointmentDoctorMapping.builder()
                .id(this.id)
                .profissionalId(this.profissionalId)
                .profissionalNome(this.profissionalNome)
                .secretaryNames(this.secretaryNames)
                .blipQueueId(this.blipQueueId)
                .external(this.external)
                .itsmUserId(this.itsmUserId)
                .discordWebhookUrl(this.discordWebhookUrl)
                .externalWaLink(this.externalWaLink)
                .createdAt(this.createdAt)
                .updatedAt(this.updatedAt)
                .build();
    }

    public static AppointmentDoctorMappingEntity fromDomain(AppointmentDoctorMapping domain) {
        if (domain == null) return null;
        return AppointmentDoctorMappingEntity.builder()
                .id(domain.getId())
                .profissionalId(domain.getProfissionalId())
                .profissionalNome(domain.getProfissionalNome())
                .secretaryNames(domain.getSecretaryNames())
                .blipQueueId(domain.getBlipQueueId())
                .external(domain.isExternal())
                .itsmUserId(domain.getItsmUserId())
                .discordWebhookUrl(domain.getDiscordWebhookUrl())
                .externalWaLink(domain.getExternalWaLink())
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .build();
    }
}