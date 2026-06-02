package br.dev.ctrls.inovareti.modules.ticket.domain.model;
import br.dev.ctrls.inovareti.modules.ticket.domain.port.output.TicketTagRepositoryPort;


import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

/**
 * Componente responsável por extrair tags automáticas baseadas
 * no título e na descrição de um chamado, de forma dinâmica a partir do banco de dados.
 */
@Component
@RequiredArgsConstructor
public class TicketTagExtractor {

    private final TicketTagRepositoryPort ticketTagRepository;

    public Set<TicketTag> extractTags(String title, String description) {
        Set<TicketTag> extractedTags = new HashSet<>();
        if (title == null) title = "";
        if (description == null) description = "";

        // Converte para minúsculas para comparação case-insensitive
        String combined = (title + " " + description).toLowerCase();

        List<TicketTag> activeTags = ticketTagRepository.findAllByActiveTrue();
        for (TicketTag tag : activeTags) {
            // Ignora a tag especial de parada crítica na extração automática de texto para evitar disparos acidentais
            if ("#🚨ParadaCrítica".equalsIgnoreCase(tag.getName())) {
                continue;
            }
            if (combined.contains(tag.getName().toLowerCase())) {
                extractedTags.add(tag);
            }
        }

        return extractedTags;
    }
}
