package br.dev.ctrls.inovareti.domain.ticket;

import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Componente responsável por extrair tags automáticas baseadas
 * no título e na descrição de um chamado.
 */
@Component
public class TicketTagExtractor {

    public Set<String> extractTags(String title, String description) {
        Set<String> tags = new HashSet<>();
        if (title == null) title = "";
        if (description == null) description = "";

        // Converte para minúsculas para comparação case-insensitive
        String combined = (title + " " + description).toLowerCase();

        // Regras para IMPRESSORA
        if (combined.contains("impressora") || combined.contains("toner") || combined.contains("impressao") || combined.contains("scanner")) {
            tags.add("IMPRESSORA");
        }

        // Regras para REDE
        if (combined.contains("internet") || combined.contains("rede") || combined.contains("wi-fi") || combined.contains("wifi") || combined.contains("cabo") || combined.contains("lento")) {
            tags.add("REDE");
        }

        // Regras para SOFTWARE
        if (combined.contains("feegow") || combined.contains("conta azul") || combined.contains("blip") || combined.contains("sistema")) {
            tags.add("SOFTWARE");
        }

        return tags;
    }
}
