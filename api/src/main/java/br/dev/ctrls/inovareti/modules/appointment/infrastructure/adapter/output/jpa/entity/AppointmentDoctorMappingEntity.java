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

    @Column(name = "itsm_user_id")
    private UUID itsmUserId;

    @Column(name = "discord_webhook_url", length = 500)
    private String discordWebhookUrl;

    @Column(name = "ignore_auto_schedule", nullable = false)
    private boolean ignoreAutoSchedule;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "subscription_end_date")
    private LocalDateTime subscriptionEndDate;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @SuppressWarnings("deprecation")
    public AppointmentDoctorMapping toDomain() {
        // Conversão de UUID da entidade para String no objeto de domínio puro
        // Retorna valores fixados (false/null) para propriedades depreciadas no domínio
        return AppointmentDoctorMapping.builder()
                .id(this.id)
                .profissionalId(this.profissionalId)
                .profissionalNome(this.profissionalNome)
                .secretaryNames(this.secretaryNames)
                .blipQueueId(this.blipQueueId)
                .external(false)
                .itsmUserId(this.itsmUserId != null ? this.itsmUserId.toString() : null)
                .discordWebhookUrl(this.discordWebhookUrl)
                .externalWaLink(null)
                .ignoreAutoSchedule(this.ignoreAutoSchedule)
                .isActive(this.isActive)
                .subscriptionEndDate(this.subscriptionEndDate)
                .createdAt(this.createdAt)
                .updatedAt(this.updatedAt)
                .build();
    }

    public static AppointmentDoctorMappingEntity fromDomain(AppointmentDoctorMapping domain) {
        if (domain == null) return null;

        // Converte com segurança a String itsmUserId do domínio para UUID antes de salvar na entidade JPA
        UUID parsedItsmUserId = null;
        if (domain.getItsmUserId() != null && !domain.getItsmUserId().isBlank()) {
            try {
                parsedItsmUserId = UUID.fromString(domain.getItsmUserId().trim());
            } catch (IllegalArgumentException e) {
                // Mantém nulo caso não seja um formato de UUID válido (garante integridade ACID no banco)
            }
        }

        return AppointmentDoctorMappingEntity.builder()
                .id(domain.getId())
                .profissionalId(domain.getProfissionalId())
                .profissionalNome(domain.getProfissionalNome())
                .secretaryNames(domain.getSecretaryNames())
                .blipQueueId(domain.getBlipQueueId())
                .itsmUserId(parsedItsmUserId)
                .discordWebhookUrl(domain.getDiscordWebhookUrl())
                .ignoreAutoSchedule(domain.isIgnoreAutoSchedule())
                .isActive(domain.isActive())
                .subscriptionEndDate(domain.getSubscriptionEndDate())
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .build();
    }
}