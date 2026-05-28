package br.dev.ctrls.inovareti.domain.user;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.dev.ctrls.inovareti.domain.user.dto.SectorRequestDTO;
import br.dev.ctrls.inovareti.domain.user.dto.SectorResponseDTO;
import br.dev.ctrls.inovareti.domain.user.usecase.CreateSectorUseCase;
import br.dev.ctrls.inovareti.domain.user.usecase.ListAllSectorsUseCase;
import br.dev.ctrls.inovareti.domain.user.usecase.UpdateSectorUseCase;
import br.dev.ctrls.inovareti.domain.user.usecase.ToggleSectorActiveUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Controller REST para gerenciamento de setores.
 * Base path: /api/sectors
 */
@RestController
@RequestMapping("/sectors")
@RequiredArgsConstructor
public class SectorController {

    private final CreateSectorUseCase createSectorUseCase;
    private final ListAllSectorsUseCase listAllSectorsUseCase;
    private final UpdateSectorUseCase updateSectorUseCase;
    private final ToggleSectorActiveUseCase toggleSectorActiveUseCase;

    /**
     * Cria um novo setor.
     * Retorna 201 Created com o setor criado no corpo da resposta.
     * Requer permissão ADMIN.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<SectorResponseDTO> create(@Valid @RequestBody SectorRequestDTO request) {
        SectorResponseDTO response = createSectorUseCase.execute(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Lista os setores cadastrados, opcionalmente filtrando apenas os ativos.
     * Retorna 200 OK com a lista.
     * Requer autenticação.
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public ResponseEntity<List<SectorResponseDTO>> listAll(
            @org.springframework.web.bind.annotation.RequestParam(required = false) Boolean activeOnly) {
        return ResponseEntity.ok(listAllSectorsUseCase.execute(activeOnly));
    }

    /**
     * Atualiza o nome de um setor existente.
     * Requer permissão ADMIN.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @org.springframework.web.bind.annotation.PutMapping("/{id}")
    public ResponseEntity<SectorResponseDTO> update(
            @org.springframework.web.bind.annotation.PathVariable java.util.UUID id,
            @Valid @RequestBody SectorRequestDTO request) {
        SectorResponseDTO response = updateSectorUseCase.execute(id, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Alterna o estado de ativação (ativo/inativo) de um setor.
     * Requer permissão ADMIN.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @org.springframework.web.bind.annotation.PatchMapping("/{id}/toggle-active")
    public ResponseEntity<SectorResponseDTO> toggleActive(
            @org.springframework.web.bind.annotation.PathVariable java.util.UUID id) {
        SectorResponseDTO response = toggleSectorActiveUseCase.execute(id);
        return ResponseEntity.ok(response);
    }
}
