package br.dev.ctrls.inovareti.modules.access.domain.model;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Representa as informações de acesso obtidas do Feegow por meio do id_agendamento.
 * Comentários em PT-BR pelas Regras de Ouro.
 */
public record FeegowPatientAccessInfo(
    String appointmentId,
    String patientId,
    String name,
    String cpf,
    LocalDate appointmentDate,
    LocalTime appointmentTime,
    String doctorId,
    String doctorName,
    String phone
) {}
