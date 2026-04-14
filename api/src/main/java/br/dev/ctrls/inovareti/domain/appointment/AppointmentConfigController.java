package br.dev.ctrls.inovareti.domain.appointment;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.dev.ctrls.inovareti.domain.appointment.dto.BlipTemplateDto;
import br.dev.ctrls.inovareti.domain.appointment.dto.UpdateAppointmentConfigRequest;
import br.dev.ctrls.inovareti.domain.appointment.usecase.ListAppointmentDictionaryUseCase;
import br.dev.ctrls.inovareti.domain.appointment.usecase.UpdateAppointmentConfigUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/appointments/config")
@CrossOrigin(origins = "http://localhost:5173")
@RequiredArgsConstructor
public class AppointmentConfigController {

    private final ListAppointmentDictionaryUseCase listAppointmentDictionaryUseCase;
    private final BlipClient blipClient;
    private final UpdateAppointmentConfigUseCase updateAppointmentConfigUseCase;

    /**
     * Retorna o dicionário de variáveis disponíveis para templates
     */
    @GetMapping("/dictionary")
    public ResponseEntity<List<ListAppointmentDictionaryUseCase.DictionaryItem>> dictionary() {
        return ResponseEntity.ok(listAppointmentDictionaryUseCase.execute());
    }

    /**
     * Busca templates aprovados disponíveis na API do Blip
     * @return Lista de templates com id e nome
     */
    @GetMapping("/blip-templates")
    public ResponseEntity<List<BlipTemplateDto>> blipTemplates() {
        List<BlipTemplateDto> templates = blipClient.fetchTemplatesFromBlip();
        return ResponseEntity.ok(templates);
    }

    /**
     * Atualiza o template associado a uma categoria de agendamento
     * @param category Categoria (CONFIRMATION, NUDGE_1, NUDGE_FINAL)
     * @param request Contém o templateId a ser associado
     */
    @PutMapping("/{category}")
    public ResponseEntity<Map<String, Object>> updateConfig(
            @PathVariable String category,
            @RequestBody @Valid UpdateAppointmentConfigRequest request) {
        try {
            AppointmentCategory appointmentCategory = AppointmentCategory.valueOf(category.toUpperCase());
            AppointmentConfig updated = updateAppointmentConfigUseCase.execute(appointmentCategory, request.templateId());
            
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "category", appointmentCategory,
                    "templateId", updated.getTemplateId()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Categoria inválida: " + category));
        }
    }
}
