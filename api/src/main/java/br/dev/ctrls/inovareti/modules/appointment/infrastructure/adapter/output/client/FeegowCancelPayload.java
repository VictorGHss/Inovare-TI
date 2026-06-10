package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.client;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Record de infraestrutura FeegowCancelPayload.
 * Representa o payload JSON esperado pela Feegow para cancelar agendamentos.
 */
public record FeegowCancelPayload(
        @JsonProperty("agendamento_id") Object agendamentoId,
        @JsonProperty("motivo_id") Integer motivoId,
        @JsonProperty("obs") String obs
) {}
