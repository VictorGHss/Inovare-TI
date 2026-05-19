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
public class AppointmentTemplateMapping {

    private UUID id;
    private String templateName;
    private Integer placeholderIndex;
    private String feegowFieldName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
