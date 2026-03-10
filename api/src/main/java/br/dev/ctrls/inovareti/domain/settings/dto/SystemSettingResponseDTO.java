package br.dev.ctrls.inovareti.domain.settings.dto;

import br.dev.ctrls.inovareti.domain.settings.SystemSetting;

public record SystemSettingResponseDTO(
        String id,
        String value,
        String description
) {
    public static SystemSettingResponseDTO from(SystemSetting setting) {
        return new SystemSettingResponseDTO(
                setting.getId(),
                setting.getValue(),
                setting.getDescription()
        );
    }
}
