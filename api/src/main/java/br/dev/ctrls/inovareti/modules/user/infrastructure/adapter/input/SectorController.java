package br.dev.ctrls.inovareti.modules.user.infrastructure.adapter.input;

import io.micrometer.observation.annotation.Observed;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.dev.ctrls.inovareti.modules.user.application.dto.SectorRequestDTO;
import br.dev.ctrls.inovareti.modules.user.application.dto.SectorResponseDTO;
import br.dev.ctrls.inovareti.modules.user.application.service.CreateSectorUseCase;
import br.dev.ctrls.inovareti.modules.user.application.service.ListAllSectorsUseCase;
import br.dev.ctrls.inovareti.modules.user.application.service.UpdateSectorUseCase;
import br.dev.ctrls.inovareti.modules.user.application.service.ToggleSectorActiveUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Controller REST para gerenciamento de setores.
 * Base path: /api/sectors
 */
@RestController
@RequestMapping("/sectors")
@RequiredArgsConstructor
@Observed
public class SectorController {

    private final CreateSectorUseCase createSectorUseCase;
    private final ListAllSectorsUseCase listAllSectorsUseCase;
    private final UpdateSectorUseCase updateSectorUseCase;
    private final ToggleSectorActiveUseCase toggleSectorActiveUseCase;

    /**
     * Cria um novo setor.
     * Retorna 201 Created com o setor criado no corpo da resposta.
     * Requer permissÃ£o ADMIN.
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
     * Requer autenticaÃ§Ã£o.
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public ResponseEntity<List<SectorResponseDTO>> listAll(
            @org.springframework.web.bind.annotation.RequestParam(required = false) Boolean activeOnly) {
        return ResponseEntity.ok(listAllSectorsUseCase.execute(activeOnly));
    }

    /**
     * Atualiza o nome de um setor existente.
     * Requer permissÃ£o ADMIN.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @org.springframework.web.bind.annotation.PutMapping("/{id}")
    public ResponseEntity<SectorResponseDTO> update(
            @org.springframework.web.bind.annotation.PathVariable UUID id,
            @Valid @RequestBody SectorRequestDTO request) {
        SectorResponseDTO response = updateSectorUseCase.execute(id, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Alterna o estado de ativaÃ§Ã£o (ativo/inativo) de um setor.
     * Requer permissÃ£o ADMIN.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @org.springframework.web.bind.annotation.PatchMapping("/{id}/toggle-active")
    public ResponseEntity<SectorResponseDTO> toggleActive(
            @org.springframework.web.bind.annotation.PathVariable UUID id) {
        SectorResponseDTO response = toggleSectorActiveUseCase.execute(id);
        return ResponseEntity.ok(response);
    }
}


