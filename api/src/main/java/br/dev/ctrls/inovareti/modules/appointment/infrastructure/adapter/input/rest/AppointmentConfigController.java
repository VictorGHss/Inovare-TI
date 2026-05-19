package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.input.rest;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.dev.ctrls.inovareti.modules.appointment.application.dto.BlipTemplateDto;
import br.dev.ctrls.inovareti.modules.appointment.application.dto.AppointmentTemplateMappingResponse;
import br.dev.ctrls.inovareti.modules.appointment.application.dto.SaveAppointmentTemplateMappingsRequest;
import br.dev.ctrls.inovareti.modules.appointment.application.dto.UpdateAppointmentConfigRequest;
import br.dev.ctrls.inovareti.modules.appointment.application.usecase.ListFeegowFieldsUseCase;
import br.dev.ctrls.inovareti.modules.appointment.application.usecase.ListAppointmentDictionaryUseCase;
import br.dev.ctrls.inovareti.modules.appointment.application.usecase.ListAppointmentTemplateMappingsUseCase;
import br.dev.ctrls.inovareti.modules.appointment.application.usecase.SaveAppointmentTemplateMappingsUseCase;
import br.dev.ctrls.inovareti.modules.appointment.application.usecase.UpdateAppointmentConfigUseCase;
import br.dev.ctrls.inovareti.modules.appointment.application.service.BlipNotificationService;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentCategory;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentConfig;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/appointments/config")
@CrossOrigin(origins = "http://localhost:5173")
@RequiredArgsConstructor
public class AppointmentConfigController {

    private final ListAppointmentDictionaryUseCase listAppointmentDictionaryUseCase;
    private final BlipNotificationService blipNotificationService;
    private final UpdateAppointmentConfigUseCase updateAppointmentConfigUseCase;
    private final ListFeegowFieldsUseCase listFeegowFieldsUseCase;
    private final SaveAppointmentTemplateMappingsUseCase saveAppointmentTemplateMappingsUseCase;
    private final ListAppointmentTemplateMappingsUseCase listAppointmentTemplateMappingsUseCase;

    /**
     * Retorna o dicionário de variáveis disponíveis para templates
     */
    @GetMapping("/dictionary")
    public ResponseEntity<List<ListAppointmentDictionaryUseCase.DictionaryItem>> dictionary() {
        return ResponseEntity.ok(listAppointmentDictionaryUseCase.execute());
    }

    /**
     * Lista os campos disponíveis do AppointmentTemplateData para o frontend montar os mapeamentos.
     */
    @GetMapping("/feegow-fields")
    public ResponseEntity<List<String>> feegowFields() {
        return ResponseEntity.ok(listFeegowFieldsUseCase.execute());
    }

    /**
     * Busca templates aprovados disponíveis na API do Blip
     * @return Lista de templates com id e nome
     */
    @GetMapping("/blip-templates")
    public ResponseEntity<List<BlipTemplateDto>> blipTemplates() {
        List<BlipTemplateDto> templates = blipNotificationService.fetchTemplatesFromBlip();
        return ResponseEntity.ok(templates);
    }

    /**
     * Atualiza o template associado a uma categoria de agendamento
     * @param category Categoria (CONFIRMATION, NUDGE_1, NUDGE_FINAL)
     * @param request Contém o template name/id a ser associado
     */
    @PutMapping("/{category}")
    public ResponseEntity<Map<String, Object>> updateConfig(
            @PathVariable String category,
            @RequestBody @Valid UpdateAppointmentConfigRequest request) {
        try {
            AppointmentCategory appointmentCategory = AppointmentCategory.valueOf(category.toUpperCase());
            String templateName = request.resolvedTemplateName();
            if (!StringUtils.hasText(templateName)) {
                return ResponseEntity.badRequest().body(Map.of(
                        "status", "error",
                        "message", "templateName é obrigatório"));
            }

            AppointmentConfig updated = updateAppointmentConfigUseCase.execute(appointmentCategory, templateName);
            
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "category", appointmentCategory,
                "templateName", updated.getTemplateId(),
                "templateId", updated.getTemplateId()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Categoria inválida: " + category));
        }
    }

    /**
     * Salva o mapeamento dinâmico de placeholders para campos do Feegow por template.
     */
    @PostMapping("/template-mappings")
    public ResponseEntity<Map<String, Object>> saveTemplateMappings(
            @RequestBody @Valid SaveAppointmentTemplateMappingsRequest request) {
        try {
            int savedCount = saveAppointmentTemplateMappingsUseCase.execute(request);
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "templateName", request.templateName(),
                    "savedMappings", savedCount));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", ex.getMessage()));
        }
    }

    /**
     * Retorna os mapeamentos salvos para um template específico.
     */
    @GetMapping("/template-mappings")
    public ResponseEntity<List<AppointmentTemplateMappingResponse>> templateMappings(
            @RequestParam String templateName) {
        try {
            return ResponseEntity.ok(listAppointmentTemplateMappingsUseCase.execute(templateName));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        }
    }
}
