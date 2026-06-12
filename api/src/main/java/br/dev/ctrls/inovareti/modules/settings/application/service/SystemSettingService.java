package br.dev.ctrls.inovareti.modules.settings.application.service;

import br.dev.ctrls.inovareti.modules.settings.domain.model.SystemSetting;
import br.dev.ctrls.inovareti.modules.settings.domain.port.output.SystemSettingRepositoryPort;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SystemSettingService {

    private final SystemSettingRepositoryPort systemSettingRepository;

    @Transactional(readOnly = true)
    public List<SystemSetting> listAll() {
        return systemSettingRepository.findAllByOrderByIdAsc();
    }

    @Transactional
    public List<SystemSetting> updateSettings(Map<String, String> updates) {
        for (Map.Entry<String, String> entry : updates.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue() == null ? "" : entry.getValue().trim();

            Optional<SystemSetting> maybe = systemSettingRepository.findById(key);
            if (maybe.isPresent()) {
                SystemSetting setting = maybe.get();
                setting.setValue(value);
                systemSettingRepository.save(setting);
            } else {
                // create new setting when missing (upsert)
                SystemSetting created = SystemSetting.builder().id(key).value(value).description(null).build();
                systemSettingRepository.save(created);
            }
        }

        return systemSettingRepository.findAllByOrderByIdAsc();
    }
}
