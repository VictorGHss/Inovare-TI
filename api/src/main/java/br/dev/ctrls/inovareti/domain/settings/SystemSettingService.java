package br.dev.ctrls.inovareti.domain.settings;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.core.exception.NotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SystemSettingService {

    private final SystemSettingRepository systemSettingRepository;

    @Transactional(readOnly = true)
    public List<SystemSetting> listAll() {
        return systemSettingRepository.findAllByOrderByIdAsc();
    }

    @Transactional
    public List<SystemSetting> updateSettings(Map<String, String> updates) {
        for (Map.Entry<String, String> entry : updates.entrySet()) {
            SystemSetting setting = systemSettingRepository.findById(entry.getKey())
                    .orElseThrow(() -> new NotFoundException("System setting not found: " + entry.getKey()));

            setting.setValue(entry.getValue() == null ? "" : entry.getValue().trim());
        }

        return systemSettingRepository.findAllByOrderByIdAsc();
    }
}
