package br.dev.ctrls.inovareti.domain.appointment.dto;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlipMasterStateCommand {
    private final String id = "master-state-" + UUID.randomUUID().toString();
    private final String to = "postmaster@msging.net";
    private final String method = "set";
    private String uri; // Mapear para: "/contexts/{identity}/Master-State"
    private final String type = "text/plain";
    private final String resource = "fluxov1@msging.net"; // ID do sub-bot de destino no roteador
}
