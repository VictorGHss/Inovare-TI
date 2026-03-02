package br.dev.ctrls.inovareti.domain.user;

import br.dev.ctrls.inovareti.domain.user.dto.SectorRequestDTO;
import br.dev.ctrls.inovareti.domain.user.dto.SectorResponseDTO;
import br.dev.ctrls.inovareti.domain.user.usecase.CreateSectorUseCase;
import br.dev.ctrls.inovareti.domain.user.usecase.ListAllSectorsUseCase;
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
 * Controller REST para gerenciamento de setores.
 * Base path: /api/sectors
 */
@RestController
@RequestMapping("/api/sectors")
@RequiredArgsConstructor
public class SectorController {

    private final CreateSectorUseCase createSectorUseCase;
    private final ListAllSectorsUseCase listAllSectorsUseCase;

    /**
     * Cria um novo setor.
     * Retorna 201 Created com o setor criado no corpo da resposta.
     */
    @PostMapping
    public ResponseEntity<SectorResponseDTO> create(@Valid @RequestBody SectorRequestDTO request) {
        SectorResponseDTO response = createSectorUseCase.execute(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Lista todos os setores cadastrados.
     * Retorna 200 OK com a lista.
     */
    @GetMapping
    public ResponseEntity<List<SectorResponseDTO>> listAll() {
        return ResponseEntity.ok(listAllSectorsUseCase.execute());
    }
}
