package br.dev.ctrls.inovareti.modules.appointment.application.usecase;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import br.dev.ctrls.inovareti.modules.appointment.domain.model.BlipDeliveryFailure;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.BlipDeliveryFailureRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Caso de Uso responsável por buscar e retornar o histórico de falhas de entrega do Blip.
 * Aplica paginação, filtros por agendamento e categoria, ordenando sempre pelos registros mais recentes (createdAt DESC).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FetchBlipDeliveryFailuresUseCase {

    private final BlipDeliveryFailureRepositoryPort blipDeliveryFailureRepository;

    /**
     * Executa a busca paginada e filtrada de falhas de entrega.
     * Garante a ordenação decrescente pela data de criação.
     *
     * @param appointmentId O ID do agendamento Feegow para filtro (opcional).
     * @param category A categoria do erro Blip/Meta para filtro (opcional).
     * @param pageable Parâmetros de paginação do Spring.
     * @return Página contendo as falhas de entrega encontradas.
     */
    public Page<BlipDeliveryFailure> execute(String appointmentId, String category, Pageable pageable) {
        log.debug("[CASO-USO] Buscando falhas de entrega Blip. Filtros: appointmentId='{}', category='{}'", appointmentId, category);

        // Força a ordenação decrescente por data de criação (createdAt DESC) para garantir que as mais recentes apareçam primeiro
        Pageable sortedPageable = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        return blipDeliveryFailureRepository.findAllFiltered(appointmentId, category, sortedPageable);
    }
}
