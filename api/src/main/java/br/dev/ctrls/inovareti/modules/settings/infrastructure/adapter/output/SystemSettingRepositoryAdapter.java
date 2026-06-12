package br.dev.ctrls.inovareti.modules.settings.infrastructure.adapter.output;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import br.dev.ctrls.inovareti.modules.settings.domain.model.SystemSetting;
import br.dev.ctrls.inovareti.modules.settings.domain.port.output.SystemSettingRepositoryPort;
import br.dev.ctrls.inovareti.modules.settings.infrastructure.adapter.output.jpa.repository.SystemSettingJpaRepository;

/**
 * Adaptador de saída que implementa a porta SystemSettingRepositoryPort delegando as chamadas
 * para o repositório JPA do Spring Data (SystemSettingJpaRepository).
 */
@Component
@RequiredArgsConstructor
public class SystemSettingRepositoryAdapter implements SystemSettingRepositoryPort {

    private final SystemSettingJpaRepository repository;

    @Override
    public List<SystemSetting> findAllByOrderByIdAsc() {
        return repository.findAllByOrderByIdAsc();
    }

    @Override
    public Optional<SystemSetting> findById(String id) {
        return repository.findById(id);
    }

    @Override
    public SystemSetting save(SystemSetting systemSetting) {
        return repository.save(systemSetting);
    }
}
