package br.dev.ctrls.inovareti.modules.analytics.application.dto;

/**
 * Projeção de leitura para contagens agregadas por setor e prioridade.
 */
public interface SectorPriorityMetricView {
    String getSector();

    String getPriority();

    Long getValue();
}