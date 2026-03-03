package br.dev.ctrls.inovareti.config;

import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import br.dev.ctrls.inovareti.domain.inventory.ItemCategory;
import br.dev.ctrls.inovareti.domain.inventory.ItemCategoryRepository;
import br.dev.ctrls.inovareti.domain.ticket.TicketCategory;
import br.dev.ctrls.inovareti.domain.ticket.TicketCategoryRepository;
import br.dev.ctrls.inovareti.domain.user.Sector;
import br.dev.ctrls.inovareti.domain.user.SectorRepository;
import br.dev.ctrls.inovareti.domain.user.User;
import br.dev.ctrls.inovareti.domain.user.UserRepository;
import br.dev.ctrls.inovareti.domain.user.UserRole;
import lombok.RequiredArgsConstructor;

/**
 * Seeds default ticket categories, item categories, default sector and admin user on startup when tables are empty.
 * Runs once after the application context is fully loaded.
 */
@Component
@RequiredArgsConstructor
public class DatabaseSeeder implements CommandLineRunner {

    private final TicketCategoryRepository ticketCategoryRepository;
    private final ItemCategoryRepository itemCategoryRepository;
    private final SectorRepository sectorRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

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

        // Insere setor padrão apenas se o repositório estiver vazio
        if (sectorRepository.count() == 0) {
            Sector defaultSector = Sector.builder()
                    .name("TI")
                    .build();
            sectorRepository.save(defaultSector);
        }

        // Insere usuário admin padrão apenas se o repositório estiver vazio
        if (userRepository.count() == 0) {
            Sector tiSector = sectorRepository.findByName("TI")
                    .orElseThrow(() -> new RuntimeException("Setor TI não encontrado"));

            User adminUser = User.builder()
                    .name("Administrador")
                    .email("admin@inovare.med.br")
                    .passwordHash(passwordEncoder.encode("admin123"))
                    .role(UserRole.ADMIN)
                    .sector(tiSector)
                    .location("Sede")
                    .build();
            userRepository.save(adminUser);
        }
    }
}
