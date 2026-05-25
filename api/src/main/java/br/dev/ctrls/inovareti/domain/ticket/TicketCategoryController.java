package br.dev.ctrls.inovareti.domain.ticket;

import br.dev.ctrls.inovareti.domain.ticket.dto.TicketCategoryRequestDTO;
import br.dev.ctrls.inovareti.domain.ticket.dto.TicketCategoryResponseDTO;
import br.dev.ctrls.inovareti.domain.ticket.usecase.CreateTicketCategoryUseCase;
import br.dev.ctrls.inovareti.domain.ticket.usecase.DeleteTicketCategoryUseCase;
import br.dev.ctrls.inovareti.domain.ticket.usecase.ListAllTicketCategoriesUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Controller REST para gerenciamento de categorias de chamado.
 * Base path: /api/ticket-categories
 */
@RestController
@RequestMapping("/ticket-categories")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class TicketCategoryController {

    private final CreateTicketCategoryUseCase createTicketCategoryUseCase;
    private final ListAllTicketCategoriesUseCase listAllTicketCategoriesUseCase;
    private final DeleteTicketCategoryUseCase deleteTicketCategoryUseCase;

    /**
     * Cria uma nova categoria de chamado.
     * Retorna 201 Created com a categoria criada no corpo da resposta.
     */
    @PostMapping
    public ResponseEntity<TicketCategoryResponseDTO> create(
            @Valid @RequestBody TicketCategoryRequestDTO request) {
        TicketCategoryResponseDTO response = createTicketCategoryUseCase.execute(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Lista todas as categorias de chamado cadastradas.
     * Retorna 200 OK com a lista.
     */
    @GetMapping
    public ResponseEntity<List<TicketCategoryResponseDTO>> listAll() {
        return ResponseEntity.ok(listAllTicketCategoriesUseCase.execute());
    }

    /**
     * Exclui uma categoria de chamado pelo UUID.
     * Retorna 409 Conflict se houver tickets vinculados.
     * Requer papel ADMIN.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        deleteTicketCategoryUseCase.execute(id);
        return ResponseEntity.noContent().build();
    }
}

