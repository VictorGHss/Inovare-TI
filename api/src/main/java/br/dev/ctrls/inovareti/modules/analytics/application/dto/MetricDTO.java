package br.dev.ctrls.inovareti.modules.analytics.application.dto;

/**
 * DTO representando um valor métrico com rótulo.
 * Utilizado em gráficos e resumos.
 */
public record MetricDTO(
    String name,
    long value
) {}
