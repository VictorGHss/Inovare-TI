package br.dev.ctrls.inovareti.modules.appointment.application.dto;

import java.util.List;
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
public class MessageDto {
    private String messageTemplate;
    private String messageTemplateLanguage;
    private List<String> messageParams;
    private String channelType;
}
