package br.dev.ctrls.inovareti.modules.report.infrastructure.adapter.input;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import br.dev.ctrls.inovareti.modules.report.domain.model.ReportSchedule;
import br.dev.ctrls.inovareti.modules.report.domain.port.output.ReportScheduleRepositoryPort;
import br.dev.ctrls.inovareti.modules.report.application.service.ReportAutomationService;
import lombok.Data;
import lombok.RequiredArgsConstructor;

/**
 * Controlador REST para gerenciar agendamentos de relatórios automatizados.
 */
@RestController
@RequestMapping("/report-schedules")
@RequiredArgsConstructor
public class ReportScheduleController {

    private final ReportScheduleRepositoryPort repository;
    private final ReportAutomationService automationService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<ReportSchedule> listAll() {
        return repository.findAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ReportSchedule> getById(@PathVariable UUID id) {
        return repository.findById(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ReportSchedule> create(@Valid @RequestBody ReportScheduleRequest req) {
        var entity = new ReportSchedule();
        entity.setReportType(req.getReportType());
        entity.setTargetUserId(req.getTargetUserId());

        Boolean sendEmailBox = req.getSendEmail();
        boolean sendEmail = sendEmailBox != null ? sendEmailBox : true;
        Boolean sendDiscordBox = req.getSendDiscord();
        boolean sendDiscord = sendDiscordBox != null ? sendDiscordBox : false;
        Integer scheduleDayBox = req.getScheduleDay();
        int scheduleDay = scheduleDayBox != null ? scheduleDayBox : 12;
        Boolean isActiveBox = req.getIsActive();
        boolean active = isActiveBox != null ? isActiveBox : true;

        entity.setSendEmail(sendEmail);
        entity.setSendDiscord(sendDiscord);
        entity.setScheduleDay(scheduleDay);
        entity.setActive(active);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        var saved = repository.save(entity);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ReportSchedule> update(@PathVariable UUID id, @Valid @RequestBody ReportScheduleRequest req) {
        return repository.findById(id).map(existing -> {
            Boolean isActiveBox = req.getIsActive();
            if (isActiveBox != null) {
                existing.setActive(isActiveBox);
                existing.setUpdatedAt(LocalDateTime.now());
                repository.save(existing);
            }
            return ResponseEntity.ok(existing);
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        return repository.findById(id).map(existing -> {
            repository.deleteById(id);
            return ResponseEntity.noContent().<Void>build();
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/trigger-test")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> triggerTest(@PathVariable UUID id) {
        return repository.findById(id).map(schedule -> {
            try {
                automationService.triggerTestReport(id);
                return ResponseEntity.ok().<Void>build();
            } catch (Exception ex) {
                return ResponseEntity.status(500).<Void>build();
            }
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Data
    public static class ReportScheduleRequest {
        @NotBlank(message = "O tipo de relatório é obrigatório.")
        private String reportType;
        
        @NotNull(message = "O usuário de destino é obrigatório.")
        private UUID targetUserId;
        
        private Boolean sendEmail;
        private Boolean sendDiscord;
        private Integer scheduleDay;
        
        @JsonProperty("isActive")
        private Boolean isActive;
    }
}
