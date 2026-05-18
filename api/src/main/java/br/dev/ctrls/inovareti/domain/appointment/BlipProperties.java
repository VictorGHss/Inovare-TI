package br.dev.ctrls.inovareti.domain.appointment;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "blip")
public class BlipProperties {
    private String routerId;         // roteadorprincipal57@msging.net
    private String subbotId;         // fluxov1@msging.net
    private String flowId;           // 9271b2a2-9150-4391-8f55-e65b371007fb
    private String routerKey;        // Chave do Roteador
    private String subbotKey;        // Chave do subbot (Zmx1eG92MT...)
    
    private final Blocks blocks = new Blocks();

    @Data
    public static class Blocks {
        private String waitingResponse; // ID do bloco "Aguardando Resposta" (estacionamento)
        private String confirmSuccess;   // ID do bloco "Agradecer Confirmação" (407dfd28...)
        private String alterRequest;     // ID do bloco "Encaminhar Alter" (6ae4facc...)
    }
}
