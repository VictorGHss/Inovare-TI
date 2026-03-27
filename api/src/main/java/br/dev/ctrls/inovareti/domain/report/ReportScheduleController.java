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
        var entity = new ReportSchedule();
        entity.setReportType(req.getReportType());
        entity.setTargetUserId(req.getTargetUserId());
        entity.setSendEmail(req.isSendEmail());
        entity.setSendDiscord(req.isSendDiscord());
        entity.setScheduleDay(req.getScheduleDay());
        entity.setActive(req.isActive());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        var saved = repository.save(entity);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ReportSchedule> update(@PathVariable UUID id, @RequestBody ReportScheduleRequest req) {
        return repository.findById(id).map(existing -> {
            existing.setReportType(req.getReportType());
            existing.setTargetUserId(req.getTargetUserId());
            existing.setSendEmail(req.isSendEmail());
            existing.setSendDiscord(req.isSendDiscord());
            existing.setScheduleDay(req.getScheduleDay());
            existing.setActive(req.isActive());
            existing.setUpdatedAt(LocalDateTime.now());
            repository.save(existing);
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
        private String reportType;
        private UUID targetUserId;
        private boolean sendEmail = true;
        private boolean sendDiscord = false;
        private int scheduleDay = 12;
        private boolean active = true;
    }
}
