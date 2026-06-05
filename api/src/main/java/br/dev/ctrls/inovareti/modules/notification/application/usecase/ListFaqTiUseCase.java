package br.dev.ctrls.inovareti.modules.notification.application.usecase;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import br.dev.ctrls.inovareti.modules.notification.application.dto.FaqTiResponseDTO;
import br.dev.ctrls.inovareti.modules.notification.domain.port.output.FaqTiRepositoryPort;
import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;

/**
 * Caso de uso para listar todos os FAQs da TI cadastrados.
 */
@Service
@RequiredArgsConstructor
@Observed
public class ListFaqTiUseCase {

    private final FaqTiRepositoryPort faqTiRepository;

    /**
     * Retorna a lista de todos os FAQs mapeada para DTOs.
     */
    public List<FaqTiResponseDTO> execute() {
        return faqTiRepository.findAll().stream()
                .map(FaqTiResponseDTO::from)
                .collect(Collectors.toList());
    }
}
