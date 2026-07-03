package br.dev.ctrls.inovareti.modules.appointment.application.dto;

import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
public class BlipContactUpdateCommand {
    private final String id = "update-contact-" + UUID.randomUUID().toString();
    private final String to = "postmaster@msging.net";
    private final String method = "set";
    private final String uri = "/contacts";
    private final String type = "application/vnd.lime.contact+json";
    private ContactResource resource;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public static class ContactResource {
        private String identity; // {phone}@wa.gw.msging.net
        private String name;
        private String taxDocument; // CPF
        private String birthDate;
        private Map<String, String> extras;
    }
}
