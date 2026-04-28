package br.dev.ctrls.inovareti.domain.appointment;

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

@Entity
@Table(name = "appointment_doctor_mapping")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentDoctorMapping {

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
}