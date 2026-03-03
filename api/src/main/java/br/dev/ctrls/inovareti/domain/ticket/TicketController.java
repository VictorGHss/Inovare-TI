package br.dev.ctrls.inovareti.domain.ticket;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.dev.ctrls.inovareti.domain.ticket.dto.TicketRequestDTO;
import br.dev.ctrls.inovareti.domain.ticket.dto.TicketResponseDTO;
import br.dev.ctrls.inovareti.domain.ticket.usecase.CloseTicketUseCase;
import br.dev.ctrls.inovareti.domain.ticket.usecase.CreateTicketUseCase;
import br.dev.ctrls.inovareti.domain.ticket.usecase.ListAllTicketsUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Controller REST para gerenciamento de chamados (tickets).
 * Base path: /api/tickets
 */
@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final CreateTicketUseCase createTicketUseCase;
    private final CloseTicketUseCase closeTicketUseCase;
    private final ListAllTicketsUseCase listAllTicketsUseCase;

    /**
     * Lista todos os chamados com suas relações carregadas.
     * Retorna 200 OK com a lista de chamados.
     */
    @GetMapping
    public ResponseEntity<List<TicketResponseDTO>> listAll() {
        return ResponseEntity.ok(listAllTicketsUseCase.execute());
    }

    /**
     * Abre um novo chamado com status OPEN e slaDeadline calculado automaticamente.
     * Retorna 201 Created com os dados do chamado.
     */
    @PostMapping
    public ResponseEntity<TicketResponseDTO> create(@Valid @RequestBody TicketRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(createTicketUseCase.execute(request));
    }

    /**
     * Fecha um chamado existente.
     * Se o chamado tiver item e quantidade solicitados, debita o estoque atomicamente.
     * Retorna 200 OK com o chamado atualizado.
     */
    @PatchMapping("/{id}/close")
    public ResponseEntity<TicketResponseDTO> close(@PathVariable UUID id) {
        return ResponseEntity.ok(closeTicketUseCase.execute(id));
    }
}
