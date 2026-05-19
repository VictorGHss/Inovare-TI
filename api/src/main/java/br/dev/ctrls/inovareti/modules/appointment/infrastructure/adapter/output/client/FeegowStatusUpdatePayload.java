package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.client;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Record de infraestrutura FeegowStatusUpdatePayload.
 * Representa o payload JSON esperado pela Feegow para atualização de status de agendamentos.
 */
public record FeegowStatusUpdatePayload(
        @JsonProperty("AgendamentoID") Object agendamentoId,
        @JsonProperty("StatusID") Integer statusId,
        @JsonProperty("Obs") String obs
) {}
