package br.dev.ctrls.inovareti.modules.notification.infrastructure.adapter.input;

import io.micrometer.observation.annotation.Observed;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.dev.ctrls.inovareti.modules.notification.application.dto.FaqTiRequestDTO;
import br.dev.ctrls.inovareti.modules.notification.application.dto.FaqTiResponseDTO;
import br.dev.ctrls.inovareti.modules.notification.application.usecase.CreateFaqTiUseCase;
import br.dev.ctrls.inovareti.modules.notification.application.usecase.DeleteFaqTiUseCase;
import br.dev.ctrls.inovareti.modules.notification.application.usecase.ListFaqTiUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Controller REST para gerenciamento de FAQ da TI.
 * Base path: /api/v1/admin/faqs (context-path /api + /v1/admin/faqs)
 */
@Slf4j
@RestController
@RequestMapping("/v1/admin/faqs")
@RequiredArgsConstructor
@Observed
public class FaqTiController {

    private final CreateFaqTiUseCase createFaqTiUseCase;
    private final ListFaqTiUseCase listFaqTiUseCase;
    private final DeleteFaqTiUseCase deleteFaqTiUseCase;

    /**
     * Cria um novo FAQ da TI (apenas ADMIN e TECHNICIAN/TI).
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIAN')")
    public ResponseEntity<FaqTiResponseDTO> create(@Valid @RequestBody FaqTiRequestDTO request) {
        log.info("Recebida requisição para criar FAQ com palavra-chave: {}", request.getPalavraChave());
        FaqTiResponseDTO response = createFaqTiUseCase.execute(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Lista todos os FAQs cadastrados (acessível publicamente para consultas).
     */
    @GetMapping
    public ResponseEntity<List<FaqTiResponseDTO>> listAll() {
        log.info("Recebida requisição para listar todos os FAQs");
        List<FaqTiResponseDTO> response = listFaqTiUseCase.execute();
        return ResponseEntity.ok(response);
    }

    /**
     * Remove um FAQ baseado no ID ou palavra-chave (apenas ADMIN e TECHNICIAN/TI).
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIAN')")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        log.info("Recebida requisição para deletar FAQ pelo identificador: {}", id);
        deleteFaqTiUseCase.execute(id);
        return ResponseEntity.noContent().build();
    }
}
