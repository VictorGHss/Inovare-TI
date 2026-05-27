package br.dev.ctrls.inovareti.domain.analytics.dto;

/**
 * Projeção de leitura para contagens agregadas por setor e prioridade.
 */
public interface SectorPriorityMetricView {
    String getSector();

    String getPriority();

    Long getValue();
}