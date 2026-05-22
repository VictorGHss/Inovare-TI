package br.dev.ctrls.inovareti.modules.appointment.domain.model;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationGroup {
    private UUID id;
    private UUID groupId;
    private UUID sessionId;
    private LocalDateTime createdAt;
}
