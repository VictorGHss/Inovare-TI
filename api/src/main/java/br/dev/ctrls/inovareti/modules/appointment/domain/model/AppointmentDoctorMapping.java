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
     * Campo armazenado internamente como "external".
     * Serializado/desserializado como "isExternal" no JSON para alinhar com o contrato do frontend.
     * O getter gerado pelo Lombok (isExternal()) é ignorado para evitar duplicação no JSON.
     */
    @JsonIgnore
    private boolean external;

    private String itsmUserId;
    private String discordWebhookUrl;
    private String externalWaLink;
    private boolean ignoreAutoSchedule;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @JsonGetter("isExternal")
    public boolean getIsExternal() {
        return this.external;
    }

    @JsonProperty("isExternal")
    public void setIsExternal(boolean value) {
        this.external = value;
    }
}
