package br.dev.ctrls.inovareti.domain.ticket;

import br.dev.ctrls.inovareti.domain.ticket.dto.TicketCategoryRequestDTO;
import br.dev.ctrls.inovareti.domain.ticket.dto.TicketCategoryResponseDTO;
import br.dev.ctrls.inovareti.domain.ticket.usecase.CreateTicketCategoryUseCase;
import br.dev.ctrls.inovareti.domain.ticket.usecase.ListAllTicketCategoriesUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controller REST para gerenciamento de categorias de chamado.
 * Base path: /api/ticket-categories
 */
@RestController
@RequestMapping("/api/ticket-categories")
@RequiredArgsConstructor
public class TicketCategoryController {

    private final CreateTicketCategoryUseCase createTicketCategoryUseCase;
    private final ListAllTicketCategoriesUseCase listAllTicketCategoriesUseCase;

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
}
