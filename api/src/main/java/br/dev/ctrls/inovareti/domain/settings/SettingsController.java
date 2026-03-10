package br.dev.ctrls.inovareti.domain.settings;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.dev.ctrls.inovareti.domain.settings.dto.SystemSettingResponseDTO;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/settings")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class SettingsController {

    private final SystemSettingService systemSettingService;

    @GetMapping
    public ResponseEntity<List<SystemSettingResponseDTO>> listAll() {
        List<SystemSettingResponseDTO> response = systemSettingService.listAll()
                .stream()
                .map(SystemSettingResponseDTO::from)
                .toList();

        return ResponseEntity.ok(response);
    }

    @PutMapping
    public ResponseEntity<List<SystemSettingResponseDTO>> update(@RequestBody Map<String, String> updates) {
        List<SystemSettingResponseDTO> response = systemSettingService.updateSettings(updates)
                .stream()
                .map(SystemSettingResponseDTO::from)
                .toList();

        return ResponseEntity.ok(response);
    }
}
