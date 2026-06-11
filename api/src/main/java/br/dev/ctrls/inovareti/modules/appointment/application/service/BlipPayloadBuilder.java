package br.dev.ctrls.inovareti.modules.appointment.application.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class BlipPayloadBuilder {

    /**
     * Constrói o mapa de dados que representa o payload JSON para envio do template 
     * aviso_agendamento_grupo com botões interativos dinâmicos.
     */
    public Map<String, Object> buildGroupTemplatePayload(String toPhone, String templateName, String namespace, UUID groupId) {
        String messageId = UUID.randomUUID().toString();
        
        // Parâmetro do botão de resposta rápida contendo o ID do grupo para o webhook capturar o clique
        Map<String, Object> buttonParameter = Map.of(
                "type", "payload",
                "payload", "ver_agenda_" + groupId.toString()
        );

        Map<String, Object> quickReplyComponent = Map.of(
                "type", "button",
                "sub_type", "quick_reply",
                "index", 0,
                "parameters", List.of(buttonParameter)
        );

        Map<String, Object> bodyComponent = Map.of(
                "type", "body",
                "parameters", List.of() // Caso o corpo do template possua variáveis, preencher aqui
        );

        Map<String, Object> templateDetails = Map.of(
                "name", templateName,
                "namespace", namespace,
                "language", Map.of("code", "pt_BR", "policy", "deterministic"),
                "components", List.of(bodyComponent, quickReplyComponent)
        );

        return Map.of(
                "id", messageId,
                "to", toPhone + "@wa.gw.msging.net",
                "type", "application/json",
                "content", Map.of(
                        "type", "template",
                        "template", templateDetails
                ),
                "metadata", Map.of("groupId", groupId.toString())
        );
    }
}