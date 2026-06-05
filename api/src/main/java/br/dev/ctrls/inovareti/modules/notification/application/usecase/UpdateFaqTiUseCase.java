package br.dev.ctrls.inovareti.modules.notification.application.usecase;

import org.springframework.stereotype.Service;
import br.dev.ctrls.inovareti.core.shared.domain.model.exception.ConflictException;
import br.dev.ctrls.inovareti.core.shared.domain.model.exception.NotFoundException;
import br.dev.ctrls.inovareti.modules.notification.application.dto.FaqTiRequestDTO;
import br.dev.ctrls.inovareti.modules.notification.application.dto.FaqTiResponseDTO;
import br.dev.ctrls.inovareti.modules.notification.domain.model.FaqTi;
import br.dev.ctrls.inovareti.modules.notification.domain.port.output.FaqTiRepositoryPort;
import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;

/**
 * Caso de uso para atualizar um FAQ da TI existente.
 */
@Service
@RequiredArgsConstructor
@Observed
public class UpdateFaqTiUseCase {

    private final FaqTiRepositoryPort faqTiRepository;

    /**
     * Executa a atualização do FAQ da TI, validando duplicidade da palavra-chave.
     */
    public FaqTiResponseDTO execute(Integer id, FaqTiRequestDTO request) {
        FaqTi faqTi = faqTiRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("FAQ não encontrado com o ID: " + id));

        String novaPalavra = request.getPalavraChave().trim();
        if (!faqTi.getPalavraChave().equalsIgnoreCase(novaPalavra)) {
            faqTiRepository.findByPalavraChave(novaPalavra)
                    .ifPresent(existing -> {
                        throw new ConflictException("Já existe um FAQ cadastrado com a palavra-chave: " + novaPalavra);
                    });
        }

        faqTi.setPalavraChave(novaPalavra);
        faqTi.setPergunta(request.getPergunta().trim());
        faqTi.setResposta(request.getResposta().trim());

        FaqTi saved = faqTiRepository.save(faqTi);
        return FaqTiResponseDTO.from(saved);
    }
}
