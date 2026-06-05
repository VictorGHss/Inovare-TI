package br.dev.ctrls.inovareti.modules.appointment.application.service;

import io.micrometer.observation.annotation.Observed;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.BlipClientPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.BlipClientPort.AuthorizationScope;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Observed
public class BlipTicketService {

    private final BlipClientPort limeClient;

    public BlipTicketService(BlipClientPort limeClient) {
        this.limeClient = limeClient;
    }

    public void closeActiveTickets(String tunnelOriginator) {
        if (tunnelOriginator == null || tunnelOriginator.isBlank()) return;

        String ticketsQuery = "/tickets?$filter=customerIdentity%20eq%20'" + tunnelOriginator + "'%20and%20status%20eq%20'Open'";
        
        Map<String, Object> command = Map.of(
                "id", UUID.randomUUID().toString(),
                "to", "postmaster@desk.msging.net",
                "method", "get",
                "uri", ticketsQuery
        );

        try {
            // executeCommand retorna Map<String, Object> diretamente (porta de domínio, sem ResponseEntity)
            Map<String, Object> response = limeClient.executeCommand(command, AuthorizationScope.ROUTER);
            String openTicketId = extractOpenTicketId(response);

            if (openTicketId != null && !openTicketId.isBlank()) {
                closeDeskTicketWithRouterKey(openTicketId);
            } else {
                log.debug("Nenhum ticket aberto encontrado para originator={}", tunnelOriginator);
            }
        } catch (RestClientException ex) {
            log.warn("Erro ao buscar tickets abertos via Router Key para {}.", tunnelOriginator, ex);
        }
    }

    private void closeDeskTicketWithRouterKey(String ticketId) {
        Map<String, Object> command = Map.of(
            "id", UUID.randomUUID().toString(),
            "to", "postmaster@desk.msging.net",
            "method", "set",
            "uri", "/tickets/" + ticketId + "/status",
            "type", "text/plain",
            "resource", "ClosedAttendant"
        );

        try {
            limeClient.executeCommand(command, AuthorizationScope.ROUTER);
            log.info("Ticket fechado via Desk (usando Router Key). ticketId={}", ticketId);
        } catch (RestClientException ex) {
            log.warn("Falha ao fechar ticket via Desk. ticketId={}", ticketId, ex);
        }
    }

    private String extractOpenTicketId(Map<String, Object> response) {
        if (response == null) return null;

        return switch (response.get("resource")) {
            case Map<?, ?> resourceMap -> {
                Object items = resourceMap.get("items");
                if (items == null) items = resourceMap.get("tickets");
                if (items == null) items = resourceMap.get("data");

                if (items instanceof List<?> list) {
                    yield extractTicketIdFromList(list);
                }
                
                String directId = valueAsString(resourceMap.get("id"));
                if (directId != null && !directId.isBlank()) {
                    yield directId;
                }
                yield null;
            }
            case List<?> list -> extractTicketIdFromList(list);
            case null, default -> null;
        };
    }

    private String extractTicketIdFromList(List<?> list) {
        for (Object item : list) {
            if (item instanceof Map<?, ?> itemMap) {
                String id = valueAsString(itemMap.get("id"));
                if (id == null || id.isBlank()) {
                    id = valueAsString(itemMap.get("ticketId"));
                }
                if (id != null && !id.isBlank()) return id;
            }
        }
        return null;
    }

    private String valueAsString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}

