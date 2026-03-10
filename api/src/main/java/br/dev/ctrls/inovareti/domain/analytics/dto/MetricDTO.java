package br.dev.ctrls.inovareti.domain.analytics.dto;

/**
 * DTO representando um valor métrico com rótulo.
 * Utilizado em gráficos e resumos.
 */
public record MetricDTO(
    String name,
    long value
) {}
