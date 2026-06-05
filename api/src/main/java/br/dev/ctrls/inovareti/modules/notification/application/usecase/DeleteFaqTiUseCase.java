package br.dev.ctrls.inovareti.modules.notification.application.usecase;

import org.springframework.stereotype.Service;
import br.dev.ctrls.inovareti.core.shared.domain.model.exception.NotFoundException;
import br.dev.ctrls.inovareti.modules.notification.domain.model.FaqTi;
import br.dev.ctrls.inovareti.modules.notification.domain.port.output.FaqTiRepositoryPort;
import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;

/**
 * Caso de uso para deletar um FAQ da TI baseado no ID ou na palavra-chave.
 */
@Service
@RequiredArgsConstructor
@Observed
public class DeleteFaqTiUseCase {

    private final FaqTiRepositoryPort faqTiRepository;

    /**
     * Remove o FAQ. Se o identificador for numérico, busca pelo ID, senão pela palavra-chave.
     */
    public void execute(String identifier) {
        try {
            Integer id = Integer.parseInt(identifier);
            FaqTi faqTi = faqTiRepository.findById(id)
                    .orElseThrow(() -> new NotFoundException("FAQ não encontrado com o ID: " + id));
            faqTiRepository.deleteById(faqTi.getId());
        } catch (NumberFormatException e) {
            FaqTi faqTi = faqTiRepository.findByPalavraChave(identifier.trim())
                    .orElseThrow(() -> new NotFoundException("FAQ não encontrado com a palavra-chave: " + identifier));
            faqTiRepository.deleteById(faqTi.getId());
        }
    }
}
