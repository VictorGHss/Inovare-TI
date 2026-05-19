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
public class AppointmentVariableLog {

    private UUID id;
    private AppointmentSession session;
    private AppointmentCategory category;
    private Integer placeholderIndex;
    private String dictionaryKey;
    private String resolvedValue;
    private LocalDateTime sentAt;
}
