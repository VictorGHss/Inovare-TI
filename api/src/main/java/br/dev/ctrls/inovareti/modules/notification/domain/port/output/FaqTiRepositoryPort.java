package br.dev.ctrls.inovareti.modules.notification.domain.port.output;

import java.util.List;
import java.util.Optional;
import br.dev.ctrls.inovareti.modules.notification.domain.model.FaqTi;

public interface FaqTiRepositoryPort {
    FaqTi save(FaqTi faqTi);
    Optional<FaqTi> findById(Integer id);
    Optional<FaqTi> findByPalavraChave(String palavraChave);
    List<FaqTi> searchFaq(String busca);
    List<FaqTi> findAll();
    void deleteById(Integer id);
}
