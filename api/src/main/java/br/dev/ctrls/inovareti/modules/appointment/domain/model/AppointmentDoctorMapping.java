package br.dev.ctrls.inovareti.modules.appointment.domain.model;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentDoctorMapping {

    private UUID id;
    private String profissionalId;
    private String profissionalNome;
    private String secretaryNames;
    private String blipQueueId;
    private boolean external;
    private String itsmUserId;
    private String discordWebhookUrl;
    private String externalWaLink;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
