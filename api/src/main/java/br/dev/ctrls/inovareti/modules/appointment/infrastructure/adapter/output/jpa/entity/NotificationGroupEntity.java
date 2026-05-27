package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.jpa.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import br.dev.ctrls.inovareti.modules.appointment.domain.model.NotificationGroup;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "notification_groups",
    indexes = {
        @jakarta.persistence.Index(name = "idx_notification_groups_session_id", columnList = "session_id")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationGroupEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public NotificationGroup toDomain() {
        return NotificationGroup.builder()
                .id(this.id)
                .groupId(this.groupId)
                .sessionId(this.sessionId)
                .createdAt(this.createdAt)
                .build();
    }

    public static NotificationGroupEntity fromDomain(NotificationGroup domain) {
        if (domain == null) return null;
        return NotificationGroupEntity.builder()
                .id(domain.getId())
                .groupId(domain.getGroupId())
                .sessionId(domain.getSessionId())
                .createdAt(domain.getCreatedAt())
                .build();
    }
}
