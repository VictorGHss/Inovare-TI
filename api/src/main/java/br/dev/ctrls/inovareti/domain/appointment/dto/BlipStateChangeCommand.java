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
public class BlipStateChangeCommand {
    private final String id = "state-change-" + UUID.randomUUID().toString();
    private final String to = "postmaster@msging.net";
    private final String method = "set";
    private String uri; // /contexts/{phone}@wa.gw.msging.net/stateid@9271b2a2-9150-4391-8f55-e65b371007fb
    private final String type = "text/plain";
    private String resource; // ID do bloco final no Builder
}
