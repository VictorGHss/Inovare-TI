package br.dev.ctrls.inovareti.modules.notification.infrastructure.adapter.output;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import br.dev.ctrls.inovareti.modules.notification.domain.model.FaqTi;
import br.dev.ctrls.inovareti.modules.notification.domain.port.output.FaqTiRepositoryPort;
import br.dev.ctrls.inovareti.modules.notification.infrastructure.adapter.output.jpa.repository.FaqTiJpaRepository;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class FaqTiRepositoryAdapter implements FaqTiRepositoryPort {

    private final FaqTiJpaRepository repository;

    @Override
    public FaqTi save(FaqTi faqTi) {
        return repository.save(faqTi);
    }

    @Override
    public Optional<FaqTi> findById(Integer id) {
        return repository.findById(id);
    }

    @Override
    public List<FaqTi> searchFaq(String busca) {
        return repository.searchFaq(busca);
    }

    @Override
    public List<FaqTi> findAll() {
        return repository.findAll();
    }

    @Override
    public void deleteById(Integer id) {
        repository.deleteById(id);
    }
}
