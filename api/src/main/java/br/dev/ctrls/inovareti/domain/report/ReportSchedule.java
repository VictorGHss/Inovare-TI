package br.dev.ctrls.inovareti.domain.report;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "report_schedules")
@Data
@NoArgsConstructor
public class ReportSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "report_type", nullable = false)
    private String reportType;

    @Column(name = "target_user_id")
    private UUID targetUserId;

    @Column(name = "send_email", nullable = false)
    private boolean sendEmail = true;

    @Column(name = "send_discord", nullable = false)
    private boolean sendDiscord = false;

    @Column(name = "schedule_day", nullable = false)
    private int scheduleDay = 12;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
