package br.dev.ctrls.inovareti.config;

import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import br.dev.ctrls.inovareti.domain.inventory.ItemCategory;
import br.dev.ctrls.inovareti.domain.inventory.ItemCategoryRepository;
import br.dev.ctrls.inovareti.domain.ticket.TicketCategory;
import br.dev.ctrls.inovareti.domain.ticket.TicketCategoryRepository;
import lombok.RequiredArgsConstructor;

/**
 * Seeds default ticket categories and item categories on startup when tables are empty.
 * Runs once after the application context is fully loaded.
 */
@Component
@RequiredArgsConstructor
public class DatabaseSeeder implements CommandLineRunner {

    private final TicketCategoryRepository ticketCategoryRepository;
    private final ItemCategoryRepository itemCategoryRepository;

    @Override
    public void run(String... args) {
        // Insere categorias padrão apenas se o repositório estiver vazio
        if (ticketCategoryRepository.count() == 0) {
            List<TicketCategory> defaultCategories = List.of(
                TicketCategory.builder()
                    .name("Hardware e Equipamentos")
                    .baseSlaHours(48)
                    .build(),
                TicketCategory.builder()
                    .name("Sistemas e Softwares")
                    .baseSlaHours(24)
                    .build(),
                TicketCategory.builder()
                    .name("Redes e Internet")
                    .baseSlaHours(12)
                    .build(),
                TicketCategory.builder()
                    .name("Acessos e Permissões")
                    .baseSlaHours(24)
                    .build()
            );
            ticketCategoryRepository.saveAll(defaultCategories);
        }

        // Insere categorias de itens padrão apenas se o repositório estiver vazio
        if (itemCategoryRepository.count() == 0) {
            List<ItemCategory> defaultItemCategories = List.of(
                ItemCategory.builder()
                    .name("Computadores e Desktops")
                    .isConsumable(false)
                    .build(),
                ItemCategory.builder()
                    .name("Periféricos (Mouse/Teclado)")
                    .isConsumable(false)
                    .build(),
                ItemCategory.builder()
                    .name("Suprimentos de Impressão")
                    .isConsumable(true)
                    .build(),
                ItemCategory.builder()
                    .name("Materiais de Escritório")
                    .isConsumable(true)
                    .build()
            );
            itemCategoryRepository.saveAll(defaultItemCategories);
        }
    }
}
