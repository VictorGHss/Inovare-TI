package br.dev.ctrls.inovareti.modules.appointment.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO com o resultado refinado do Motor de Intenções para o Take Blip.
 * Inclui dados de paginação para proteger limites de listas interativas do WhatsApp (máx 10 itens).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IntentAnalysisResultDto {

    private String rawInput;
    private String cleanedInput;
    private String intent;
    private String extractedSpecialty;
    private boolean hasAmbiguity;

    // Campos de Paginação
    @Builder.Default
    private int page = 1;
    @Builder.Default
    private int pageSize = 9;
    private int totalMatches;
    private int totalPages;
    private boolean hasNextPage;

    @Builder.Default
    private List<DoctorMatchDto> matches = new ArrayList<>();
}
