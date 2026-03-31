package br.dev.ctrls.inovareti.domain.report;

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

import lombok.Data;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/report-schedules")
@RequiredArgsConstructor
public class ReportScheduleController {

    private final ReportScheduleRepository repository;

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
    public ResponseEntity<ReportSchedule> create(@RequestBody ReportScheduleRequest req) {
        if (req.getReportType() == null || req.getReportType().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        var entity = new ReportSchedule();
        entity.setReportType(req.getReportType());
        entity.setTargetUserId(req.getTargetUserId());

        // apply defaults when request omits optional fields (use boxed locals to avoid unboxing warnings)
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
    public ResponseEntity<ReportSchedule> update(@PathVariable UUID id, @RequestBody ReportScheduleRequest req) {
        return repository.findById(id).map(existing -> {
            // Preserve all original fields and update only 'isActive' when provided to avoid nulling required columns
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

    @Data
    public static class ReportScheduleRequest {
        // nullable request fields allow partial updates
        private String reportType;
        private UUID targetUserId;
        private Boolean sendEmail;
        private Boolean sendDiscord;
        private Integer scheduleDay;
        @JsonProperty("isActive")
        private Boolean isActive;
    }
}
