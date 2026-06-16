package br.dev.ctrls.inovareti.modules.appointment.domain.model;

import java.time.LocalDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

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

    /**
     * @deprecated Removido em prol da centralização obrigatória de atendimento via Blip Desk.
     */
    @Deprecated
    @JsonIgnore
    private boolean external;

    private String itsmUserId;
    private String discordWebhookUrl;

    /**
     * @deprecated Removido em prol da centralização obrigatória de atendimento via Blip Desk.
     */
    @Deprecated
    private String externalWaLink;

    private boolean ignoreAutoSchedule;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * @deprecated Removido em prol da centralização obrigatória de atendimento via Blip Desk.
     */
    @Deprecated
    @JsonGetter("isExternal")
    public boolean getIsExternal() {
        return this.external;
    }

    /**
     * @deprecated Removido em prol da centralização obrigatória de atendimento via Blip Desk.
     */
    @Deprecated
    @JsonProperty("isExternal")
    public void setIsExternal(boolean value) {
        this.external = value;
    }
}
