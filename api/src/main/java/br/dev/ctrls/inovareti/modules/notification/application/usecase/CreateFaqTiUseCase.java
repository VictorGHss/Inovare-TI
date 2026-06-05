package br.dev.ctrls.inovareti.modules.notification.application.usecase;

import org.springframework.stereotype.Service;
import br.dev.ctrls.inovareti.core.shared.domain.model.exception.ConflictException;
import br.dev.ctrls.inovareti.modules.notification.application.dto.FaqTiRequestDTO;
import br.dev.ctrls.inovareti.modules.notification.application.dto.FaqTiResponseDTO;
import br.dev.ctrls.inovareti.modules.notification.domain.model.FaqTi;
import br.dev.ctrls.inovareti.modules.notification.domain.port.output.FaqTiRepositoryPort;
import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;

/**
 * Caso de uso para criar um novo FAQ da TI.
 */
@Service
@RequiredArgsConstructor
@Observed
public class CreateFaqTiUseCase {

    private final FaqTiRepositoryPort faqTiRepository;

    /**
     * Executa a criação do FAQ da TI, validando a duplicidade da palavra-chave.
     */
    public FaqTiResponseDTO execute(FaqTiRequestDTO request) {
        faqTiRepository.findByPalavraChave(request.getPalavraChave().trim())
                .ifPresent(faq -> {
                    throw new ConflictException("Já existe um FAQ cadastrado com a palavra-chave: " + request.getPalavraChave());
                });

        FaqTi faqTi = FaqTi.builder()
                .palavraChave(request.getPalavraChave().trim())
                .pergunta(request.getPergunta().trim())
                .resposta(request.getResposta().trim())
                .build();

        FaqTi saved = faqTiRepository.save(faqTi);
        return FaqTiResponseDTO.from(saved);
    }
}
