package br.dev.ctrls.inovareti.domain.ticket;

import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import br.dev.ctrls.inovareti.core.exception.NotFoundException;
import br.dev.ctrls.inovareti.core.exception.BadRequestException;

@RestController
@RequestMapping("/ticket-tags")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class TicketTagController {

    private final TicketTagRepository ticketTagRepository;

    @GetMapping
    public ResponseEntity<List<TicketTag>> listAll(@RequestParam(required = false) Boolean activeOnly) {
        if (Boolean.TRUE.equals(activeOnly)) {
            return ResponseEntity.ok(ticketTagRepository.findAllByActiveTrue());
        }
        return ResponseEntity.ok(ticketTagRepository.findAll());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIAN')")
    public ResponseEntity<TicketTag> create(@Valid @RequestBody TicketTag tag) {
        if (ticketTagRepository.findByNameIgnoreCase(tag.getName()).isPresent()) {
            throw new BadRequestException("Já existe uma tag com este nome: " + tag.getName());
        }
        TicketTag saved = ticketTagRepository.save(tag);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIAN')")
    public ResponseEntity<TicketTag> update(@PathVariable UUID id, @Valid @RequestBody TicketTag request) {
        TicketTag tag = ticketTagRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Tag não encontrada com o id: " + id));

        var existing = ticketTagRepository.findByNameIgnoreCase(request.getName());
        if (existing.isPresent() && !existing.get().getId().equals(id)) {
            throw new BadRequestException("Já existe uma tag com este nome: " + request.getName());
        }

        tag.setName(request.getName().trim());
        tag.setColor(request.getColor().trim());
        tag.setDefaultResolution(request.getDefaultResolution());
        tag.setActive(request.isActive());

        TicketTag saved = ticketTagRepository.save(tag);
        return ResponseEntity.ok(saved);
    }

    @PatchMapping("/{id}/toggle-active")
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIAN')")
    public ResponseEntity<TicketTag> toggleActive(@PathVariable UUID id) {
        TicketTag tag = ticketTagRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Tag não encontrada com o id: " + id));

        tag.setActive(!tag.isActive());
        TicketTag saved = ticketTagRepository.save(tag);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIAN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        TicketTag tag = ticketTagRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Tag não encontrada com o id: " + id));

        ticketTagRepository.delete(tag);
        return ResponseEntity.noContent().build();
    }
}
