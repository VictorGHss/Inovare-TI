package br.dev.ctrls.inovareti.domain.analytics.dto;

/**
 * DTO representing a metric value with a label.
 * Used for charts and summaries.
 */
public record MetricDTO(
    String name,
    long value
) {}
