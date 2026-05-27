package br.dev.ctrls.inovareti.domain.analytics.dto;

/**
 * Métrica agregada por setor e nível consolidado de prioridade.
 */
public record SectorPriorityMetricDTO(
        String sector,
        String priority,
        long value
) {}