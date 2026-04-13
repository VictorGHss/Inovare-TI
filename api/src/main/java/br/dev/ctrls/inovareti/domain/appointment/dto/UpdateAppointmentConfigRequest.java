package br.dev.ctrls.inovareti.domain.appointment.dto;

/**
 * DTO para requisição de atualização de configuração de template
 */
public record UpdateAppointmentConfigRequest(
        String templateId) {
}
