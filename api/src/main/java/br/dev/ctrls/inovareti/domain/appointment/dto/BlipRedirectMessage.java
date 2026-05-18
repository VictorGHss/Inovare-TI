package br.dev.ctrls.inovareti.domain.appointment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlipRedirectMessage {
    private String id;
    private String to; // Identidade do usuário: {phone}@wa.gw.msging.net
    private final String type = "application/vnd.lime.redirect+json";
    private RedirectContent content;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RedirectContent {
        private String address; // sub-bot de destino: inovare_subbot_atendimento@msging.net
        private String flow;    // ID do bloco de destino: 2e3a6a6e-d18d-4d0d-b660-0d3dc7298262
        private RedirectContext context;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RedirectContext {
        private final String type = "text/plain";
        private String value; // Payload JSON em string
    }
}
