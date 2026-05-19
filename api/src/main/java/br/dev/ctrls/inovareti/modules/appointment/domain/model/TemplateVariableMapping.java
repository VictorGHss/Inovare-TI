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
public class TemplateVariableMapping {

    private UUID id;
    private AppointmentConfig config;
    private Integer placeholderIndex;
    private String dictionaryKey;
    private LocalDateTime createdAt;
}
