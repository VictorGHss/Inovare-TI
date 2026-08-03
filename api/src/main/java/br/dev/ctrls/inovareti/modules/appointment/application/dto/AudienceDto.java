package br.dev.ctrls.inovareti.modules.appointment.application.dto;

import java.util.Map;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AudienceDto {
    private String recipient;
    private Map<String, String> messageParams;
    private Map<String, String> contextVariables;
}
