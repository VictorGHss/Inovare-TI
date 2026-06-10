package br.dev.ctrls.inovareti.modules.appointment.domain.port.output;

import java.time.LocalDate;
import java.util.List;

/**
 * Porta de Saída do Domínio: AppointmentExternalPort.
 * Interface Java pura que define os métodos focados em obter e atualizar status de agendamentos a partir de sistemas externos.
 */
public interface AppointmentExternalPort {

    /**
     * Busca os agendamentos da Feegow em uma data específica com um status específico.
     *
     * @param date data do agendamento
     * @param statusId ID do status na Feegow
     * @return lista de agendamentos
     */
    List<FeegowAppointment> searchAppointments(LocalDate date, int statusId);

    /**
     * Busca os agendamentos da Feegow em uma data e status específicos para um médico específico.
     *
     * @param date data do agendamento
     * @param statusId ID do status na Feegow
     * @param profissionalId ID do profissional de saúde (opcional)
     * @return lista de agendamentos
     */
    List<FeegowAppointment> searchAppointments(LocalDate date, int statusId, String profissionalId);

    /**
     * Atualiza o status de um agendamento na Feegow por meio do identificador (String).
     *
     * @param appointmentId ID do agendamento
     * @param statusId ID do status
     */
    void updateAppointmentStatus(String appointmentId, String statusId);

    /**
     * Atualiza o status de um agendamento na Feegow por meio do identificador e ID numérico.
     *
     * @param appointmentId ID do agendamento
     * @param statusId ID numérico do status
     */
    void updateStatus(String appointmentId, int statusId);

    /**
     * Cancela uma consulta na Feegow enviando o ID e um motivo/observação.
     *
     * @param appointmentId ID do agendamento
     * @param obs observação/motivo do cancelamento
     */
    void cancelAppointment(String appointmentId, String obs);
}
