package br.dev.ctrls.inovareti.modules.appointment.domain.model;

public enum AppointmentSessionStatus {
    PENDING,
    NUDGE_1_SENT,
    NUDGE_FINAL_SENT,
    CONFIRMED,
    CANCELED_NO_RESPONSE,
    CANCELED,
    ERROR_DELIVERY
}
