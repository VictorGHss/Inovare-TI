package br.dev.ctrls.inovareti.modules.notification.infrastructure.listener;

import java.util.List;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import br.dev.ctrls.inovareti.modules.ticket.domain.event.TicketCreatedEvent;
import br.dev.ctrls.inovareti.modules.ticket.domain.event.TicketResolvedEvent;
import br.dev.ctrls.inovareti.modules.ticket.domain.event.TicketPermissionsChangedEvent;
import br.dev.ctrls.inovareti.modules.ticket.domain.port.output.DiscordTicketPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Listener that listens to ticket domain events and triggers Discord ticket channel operations.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DiscordTicketEventListener {

    private final DiscordTicketPort discordTicketPort;

    @Async
    @EventListener
    public void onTicketCreated(TicketCreatedEvent event) {
        log.info("[DISCORD-TICKET] Evento TicketCreatedEvent recebido para o chamado #{}.", event.ticket().getNumber());
        try {
            List<String> discordUserIds = event.assignedUsers().stream()
                    .filter(java.util.Objects::nonNull)
                    .map(u -> java.util.Objects.requireNonNull(u).getDiscordUserId())
                    .filter(id -> id != null && !id.isBlank())
                    .toList();

            discordTicketPort.createTicketChannel(event.ticket(), discordUserIds);
        } catch (Exception ex) {
            log.error("[DISCORD-TICKET] Falha ao processar TicketCreatedEvent para o chamado #{}", event.ticket().getNumber(), ex);
        }
    }

    @Async
    @EventListener
    public void onTicketResolved(TicketResolvedEvent event) {
        log.info("[DISCORD-TICKET] Evento TicketResolvedEvent recebido para o chamado #{}.", event.ticket().getNumber());
        try {
            discordTicketPort.archiveTicketChannel(event.ticket());
        } catch (Exception ex) {
            log.error("[DISCORD-TICKET] Falha ao processar TicketResolvedEvent para o chamado #{}", event.ticket().getNumber(), ex);
        }
    }

    @Async
    @EventListener
    public void onTicketPermissionsChanged(TicketPermissionsChangedEvent event) {
        log.info("[DISCORD-TICKET] Evento TicketPermissionsChangedEvent recebido para o chamado #{}.", event.ticket().getNumber());
        try {
            discordTicketPort.syncTicketChannelPermissions(event.ticket());
        } catch (Exception ex) {
            log.error("[DISCORD-TICKET] Falha ao processar TicketPermissionsChangedEvent para o chamado #{}", event.ticket().getNumber(), ex);
        }
    }
}
