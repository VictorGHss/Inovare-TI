package br.dev.ctrls.inovareti.domain.appointment;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "blip")
public class BlipProperties {
    private String routerId;         // Mapeado de blip.router-id (roteadorprincipal57@msging.net)
    private String subbotId;         // Mapeado de blip.subbot-id (fluxov1@msging.net)
    private String flowId;           // Mapeado de blip.flow-id (9271b2a2-9150-4391-8f55-e65b371007fb)
    private String routerKey;        // Mapeado de blip.router-key (Chave do Roteador)
    private String subbotKey;        // Mapeado de blip.subbot-key (Chave do subbot)
    
    private Blocks blocks = new Blocks();

    @Data
    public static class Blocks {
        private String waitingResponse; // Mapeado de blip.blocks.waiting-response (Estacionamento)
        private String confirmSuccess;   // Mapeado de blip.blocks.confirm-success (Agradecer Confirmação)
        private String alterRequest;     // Mapeado de blip.blocks.alter-request (Encaminhar Alter)
    }
}
