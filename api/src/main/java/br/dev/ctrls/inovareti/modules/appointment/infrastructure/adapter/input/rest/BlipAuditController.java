package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.input.rest;

import io.micrometer.observation.annotation.Observed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.dev.ctrls.inovareti.modules.appointment.application.usecase.FetchBlipDeliveryFailuresUseCase;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.BlipDeliveryFailure;

/**
 * Controlador REST responsável por expor os dados de auditoria relacionados a falhas
 * de entrega de notificações/mensagens enviadas através da Blip.
 */
@Slf4j
@RestController
@RequestMapping("/v1/audit/blip-failures")
@RequiredArgsConstructor
@Tag(name = "Auditoria - Falhas Blip", description = "Endpoints de auditoria e consulta de falhas de entrega do Blip")
@Observed
public class BlipAuditController {

    private final FetchBlipDeliveryFailuresUseCase fetchBlipDeliveryFailuresUseCase;

    /**
     * Recupera o histórico de falhas de entrega do Blip de forma paginada e filtrada.
     * Os registros retornados são sempre ordenados do mais recente para o mais antigo.
     * Exige autenticação com perfil de administrador ou técnico do ecossistema.
     *
     * @param appointmentId ID opcional do agendamento para filtrar as falhas.
     * @param category Categoria opcional do erro para filtrar as falhas.
     * @param page Número da página a ser retornada (padrão é 0).
     * @param size Quantidade de itens por página (padrão é 15, com limite de 100).
     * @return Uma página contendo os registros de falhas de entrega.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIAN')")
    @Operation(
        summary = "Lista falhas de entrega do Blip de forma paginada",
        description = "Retorna uma lista paginada e filtrada de falhas de entrega do Blip, ordenadas por data de criação decrescente."
    )
    public ResponseEntity<Page<BlipDeliveryFailure>> getBlipFailures(
            @RequestParam(required = false) String appointmentId,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size) {

        log.info("[AUDITORIA] Requisição recebida para listar falhas de entrega do Blip. Filtros: appointmentId='{}', category='{}', page={}, size={}",
                appointmentId, category, page, size);

        // Define um limite seguro para o tamanho da página para evitar sobrecarga no banco de dados
        int safeSize = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, safeSize);

        Page<BlipDeliveryFailure> failures = fetchBlipDeliveryFailuresUseCase.execute(appointmentId, category, pageable);

        return ResponseEntity.ok(failures);
    }
}
