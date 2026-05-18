package br.dev.ctrls.inovareti.domain.appointment.dto;

import java.util.UUID;
import lombok.Data;

@Data
public class BlipTextMessage {
    private final String id = "msg-" + UUID.randomUUID().toString();
    private String to; // {phone}@wa.gw.msging.net
    private final String type = "text/plain";
    private String content; // Texto da mensagem
}
