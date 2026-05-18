package br.dev.ctrls.inovareti.domain.appointment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlipRedirectCommand {
    private String id;
    private final String to = "postmaster@msging.net";
    private final String method = "set";
    private String uri; // Ex: "/contexts/5511999999999@wa.gw.msging.net/state"
    private final String type = "application/vnd.lime.redirect+json";
    private RedirectResource resource;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RedirectResource {
        private String address; // Nome do sub-bot de destino no roteador (ex: "atendimento@msging.net")
        private String flow;    // ID do bloco de destino (ex: "2e3a6a6e-d18d-4d0d-b660-0d3dc7298262")
        private RedirectContext context;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RedirectContext {
        private final String type = "text/plain";
        private String value; // Aqui irá a string JSON serializada com os dados do agendamento
    }
}
