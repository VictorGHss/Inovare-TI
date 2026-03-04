package br.dev.ctrls.inovareti.config;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import br.dev.ctrls.inovareti.domain.inventory.Item;
import br.dev.ctrls.inovareti.domain.inventory.ItemCategory;
import br.dev.ctrls.inovareti.domain.inventory.ItemCategoryRepository;
import br.dev.ctrls.inovareti.domain.inventory.ItemRepository;
import br.dev.ctrls.inovareti.domain.ticket.Ticket;
import br.dev.ctrls.inovareti.domain.ticket.TicketCategory;
import br.dev.ctrls.inovareti.domain.ticket.TicketCategoryRepository;
import br.dev.ctrls.inovareti.domain.ticket.TicketPriority;
import br.dev.ctrls.inovareti.domain.ticket.TicketRepository;
import br.dev.ctrls.inovareti.domain.ticket.TicketStatus;
import br.dev.ctrls.inovareti.domain.user.Sector;
import br.dev.ctrls.inovareti.domain.user.SectorRepository;
import br.dev.ctrls.inovareti.domain.user.User;
import br.dev.ctrls.inovareti.domain.user.UserRepository;
import br.dev.ctrls.inovareti.domain.user.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Seeds default data on startup when tables are empty.
 * Creates categories, sectors, users, inventory items, and simulated tickets with varied data.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseSeeder implements CommandLineRunner {

    private final TicketCategoryRepository ticketCategoryRepository;
    private final ItemCategoryRepository itemCategoryRepository;
    private final SectorRepository sectorRepository;
    private final UserRepository userRepository;
    private final ItemRepository itemRepository;
    private final TicketRepository ticketRepository;
    private final PasswordEncoder passwordEncoder;

    private final Random random = new Random();

    @Override
    public void run(String... args) {
        log.info("Starting database seeding...");

        // Seed ticket categories
        if (ticketCategoryRepository.count() == 0) {
            seedTicketCategories();
        }

        // Seed item categories
        if (itemCategoryRepository.count() == 0) {
            seedItemCategories();
        }

        // Seed sectors
        if (sectorRepository.count() == 0) {
            seedSectors();
        }

        // Seed users
        if (userRepository.count() == 0) {
            seedUsers();
        }

        // Seed inventory items
        if (itemRepository.count() == 0) {
            seedInventoryItems();
        }

        // Seed tickets
        if (ticketRepository.count() == 0) {
            seedTickets();
        }

        log.info("Database seeding completed successfully");
    }

    private void seedTicketCategories() {
        List<TicketCategory> categories = List.of(
            TicketCategory.builder()
                .name("Hardware")
                .baseSlaHours(48)
                .build(),
            TicketCategory.builder()
                .name("Software")
                .baseSlaHours(24)
                .build(),
            TicketCategory.builder()
                .name("Rede")
                .baseSlaHours(12)
                .build(),
            TicketCategory.builder()
                .name("Acessos")
                .baseSlaHours(24)
                .build()
        );
        ticketCategoryRepository.saveAll(categories);
        log.info("Seeded {} ticket categories", categories.size());
    }

    private void seedItemCategories() {
        List<ItemCategory> categories = List.of(
            ItemCategory.builder()
                .name("Computadores e Desktops")
                .isConsumable(false)
                .build(),
            ItemCategory.builder()
                .name("Periféricos")
                .isConsumable(false)
                .build(),
            ItemCategory.builder()
                .name("Suprimentos de Impressão")
                .isConsumable(true)
                .build(),
            ItemCategory.builder()
                .name("Cabos e Conectores")
                .isConsumable(true)
                .build()
        );
        itemCategoryRepository.saveAll(categories);
        log.info("Seeded {} item categories", categories.size());
    }

    private void seedSectors() {
        List<Sector> sectors = List.of(
            Sector.builder().name("TI").build(),
            Sector.builder().name("Financeiro").build(),
            Sector.builder().name("Recursos Humanos").build(),
            Sector.builder().name("Operações").build()
        );
        sectorRepository.saveAll(sectors);
        log.info("Seeded {} sectors", sectors.size());
    }

    private void seedUsers() {
        Sector tiSector = sectorRepository.findByName("TI")
            .orElseThrow(() -> new RuntimeException("TI sector not found"));
        Sector finSector = sectorRepository.findByName("Financeiro")
            .orElseThrow(() -> new RuntimeException("Financeiro sector not found"));
        Sector hrSector = sectorRepository.findByName("Recursos Humanos")
            .orElseThrow(() -> new RuntimeException("Recursos Humanos sector not found"));
        Sector opsSector = sectorRepository.findByName("Operações")
            .orElseThrow(() -> new RuntimeException("Operações sector not found"));

        List<User> users = List.of(
            User.builder()
                .name("Administrador")
                .email("admin@inovare.med.br")
                .passwordHash(passwordEncoder.encode("admin123"))
                .role(UserRole.ADMIN)
                .sector(tiSector)
                .location("Sede")
                .build(),
            User.builder()
                .name("Técnico Suporte")
                .email("tecnico@inovare.med.br")
                .passwordHash(passwordEncoder.encode("tech123"))
                .role(UserRole.TECHNICIAN)
                .sector(tiSector)
                .location("Sede")
                .build(),
            User.builder()
                .name("João Silva")
                .email("joao.silva@inovare.med.br")
                .passwordHash(passwordEncoder.encode("user123"))
                .role(UserRole.USER)
                .sector(finSector)
                .location("Andar 3")
                .build(),
            User.builder()
                .name("Maria Santos")
                .email("maria.santos@inovare.med.br")
                .passwordHash(passwordEncoder.encode("user123"))
                .role(UserRole.USER)
                .sector(hrSector)
                .location("Andar 2")
                .build(),
            User.builder()
                .name("Pedro Costa")
                .email("pedro.costa@inovare.med.br")
                .passwordHash(passwordEncoder.encode("user123"))
                .role(UserRole.USER)
                .sector(opsSector)
                .location("Galpão")
                .build()
        );
        userRepository.saveAll(users);
        log.info("Seeded {} users", users.size());
    }

    private void seedInventoryItems() {
        ItemCategory peripherals = itemCategoryRepository.findByName("Periféricos")
            .orElseThrow(() -> new RuntimeException("Periféricos category not found"));
        ItemCategory supplies = itemCategoryRepository.findByName("Suprimentos de Impressão")
            .orElseThrow(() -> new RuntimeException("Suprimentos category not found"));
        ItemCategory cables = itemCategoryRepository.findByName("Cabos e Conectores")
            .orElseThrow(() -> new RuntimeException("Cabos category not found"));

        List<Item> items = List.of(
            Item.builder()
                .name("Mouse Wireless Logitech")
                .itemCategory(peripherals)
                .currentStock(15)
                .build(),
            Item.builder()
                .name("Teclado Mecânico RGB")
                .itemCategory(peripherals)
                .currentStock(8)
                .build(),
            Item.builder()
                .name("Monitor Dell 24\"")
                .itemCategory(peripherals)
                .currentStock(3)
                .build(),
            Item.builder()
                .name("Toner HP LaserJet")
                .itemCategory(supplies)
                .currentStock(12)
                .build(),
            Item.builder()
                .name("Papel A4 (resma)")
                .itemCategory(supplies)
                .currentStock(25)
                .build(),
            Item.builder()
                .name("Cabo HDMI 2m")
                .itemCategory(cables)
                .currentStock(20)
                .build(),
            Item.builder()
                .name("Cabo Ethernet Cat6")
                .itemCategory(cables)
                .currentStock(50)
                .build()
        );
        itemRepository.saveAll(items);
        log.info("Seeded {} inventory items", items.size());
    }

    private void seedTickets() {
        // Busca múltiplos requesters USER para distribuir tickets
        User requesterJoao = userRepository.findByEmail("joao.silva@inovare.med.br")
            .orElseThrow(() -> new RuntimeException("Requester João user not found"));
        User requesterMaria = userRepository.findByEmail("maria.santos@inovare.med.br")
            .orElseThrow(() -> new RuntimeException("Requester Maria user not found"));
        User requesterPedro = userRepository.findByEmail("pedro.costa@inovare.med.br")
            .orElseThrow(() -> new RuntimeException("Requester Pedro user not found"));
        User technician = userRepository.findByEmail("tecnico@inovare.med.br")
            .orElseThrow(() -> new RuntimeException("Technician user not found"));

        // Lista de requesters para distribuição aleatória
        List<User> requesters = List.of(requesterJoao, requesterMaria, requesterPedro);

        List<TicketCategory> categories = ticketCategoryRepository.findAll();
        List<TicketStatus> statuses = List.of(TicketStatus.OPEN, TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED, TicketStatus.CLOSED);
        List<TicketPriority> priorities = List.of(TicketPriority.LOW, TicketPriority.NORMAL, TicketPriority.HIGH, TicketPriority.URGENT);

        List<Ticket> tickets = new java.util.ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        // Generate 25 simulated tickets
        for (int i = 1; i <= 25; i++) {
            TicketStatus status = statuses.get(random.nextInt(statuses.size()));
            TicketCategory category = categories.get(random.nextInt(categories.size()));
            TicketPriority priority = priorities.get(random.nextInt(priorities.size()));

            // Distribute requester randomly among the 3 USER accounts
            User requester = requesters.get(random.nextInt(requesters.size()));

            // Spread created dates over the last 30 days
            LocalDateTime createdAt = now.minusDays(random.nextInt(30)).minusHours(random.nextInt(24)).minusMinutes(random.nextInt(60));

            // If closed, set closedAt to sometime after createdAt
            LocalDateTime closedAt = null;
            if (status == TicketStatus.CLOSED || status == TicketStatus.RESOLVED) {
                closedAt = createdAt.plusHours(random.nextInt(72) + 1);
            }

            // Calculate SLA deadline (category-based + created date)
            TicketCategory categoryForSla = category;
            LocalDateTime slaDeadline = createdAt.plusHours(categoryForSla.getBaseSlaHours());

            User assignedUser = (status == TicketStatus.OPEN) ? null : technician;

            Ticket ticket = Ticket.builder()
                .title("Chamado #" + i + " - " + category.getName() + " (" + priority + ")")
                .description("Descrição simulada do chamado " + i + ". Status: " + status)
                .status(status)
                .priority(priority)
                .requester(requester)
                .assignedTo(assignedUser)
                .category(category)
                .slaDeadline(slaDeadline)
                .createdAt(createdAt)
                .closedAt(closedAt)
                .build();

            tickets.add(ticket);
        }

        ticketRepository.saveAll(tickets);
        log.info("Seeded {} tickets", tickets.size());
    }
}
