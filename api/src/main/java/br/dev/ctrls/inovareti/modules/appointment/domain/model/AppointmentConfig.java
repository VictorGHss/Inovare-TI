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
public class AppointmentConfig {

    private UUID id;
    private AppointmentCategory category;
    private String templateId;
    private Integer timingHours;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
