package br.dev.ctrls.inovareti.modules.appointment.application.usecase;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Component;

import br.dev.ctrls.inovareti.modules.appointment.application.service.AppointmentTemplateDataBuilder;
import br.dev.ctrls.inovareti.modules.appointment.application.service.BlipNotificationService;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSession;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentSessionRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.AppointmentMotorProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Caso de Uso dedicado para envio do Lembrete Ativo de Proximidade (1 hora antes da consulta).
 * Utiliza o template ativo do WhatsApp 'lembrete_ativo_itsm_v1' para garantir a entrega
 * mesmo fora da janela de 24h, aplicando antecedência de horário e idempotência no banco de dados.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SendPreAppointmentNoticeUseCase {

    private static final String TEMPLATE_NAME = "lembrete_ativo_itsm_v1";

    private final AppointmentSessionRepositoryPort appointmentSessionRepository;
    private final AppointmentTemplateDataBuilder appointmentTemplateDataBuilder;
    private final BlipNotificationService blipNotificationService;
    private final AppointmentMotorProperties appointmentMotorProperties;

    public void execute() {
        if (!appointmentMotorProperties.isEnabled()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"));
        // Janela de 1h antes (de 45 a 75 minutos no futuro)
        LocalDateTime windowStart = now.plusMinutes(45);
        LocalDateTime windowEnd = now.plusMinutes(75);

        List<AppointmentSession> candidateSessions = appointmentSessionRepository.findConfirmedSessionsInWindow(windowStart, windowEnd);

        if (candidateSessions == null || candidateSessions.isEmpty()) {
            return;
        }

        log.info("[LEMBRETE-1H] Encontradas {} consulta(s) confirmada(s) elegíveis para o template '{}' 1h antes.",
                candidateSessions.size(), TEMPLATE_NAME);

        for (AppointmentSession session : candidateSessions) {
            try {
                if (session.getPhoneNumber() == null || session.getPhoneNumber().isBlank()) {
                    continue;
                }

                // Reconstrói dados do template aplicando regras de nome, médico e offset de horário (-10 min)
                var templateData = appointmentTemplateDataBuilder.build(session);

                log.info("[LEMBRETE-1H] Disparando template '{}' para paciente='{}', médico='{}', hora='{}', tel='{}'",
                        TEMPLATE_NAME, templateData.patientName(), templateData.doctorName(),
                        templateData.appointmentTime(), session.getPhoneNumber());

                blipNotificationService.sendTemplateMessage(session.getPhoneNumber(), TEMPLATE_NAME, templateData);

                // Marca idempotência no banco para não reenviar no próximo ciclo
                session.setStatusDetails("PRE_NOTICE_SENT_" + now.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")));
                session.setLastNotificationSentAt(now);
                appointmentSessionRepository.save(session);

            } catch (Exception ex) {
                log.error("[LEMBRETE-1H] Falha ao disparar template '{}' para sessão ID={}: {}",
                        TEMPLATE_NAME, session.getId(), ex.getMessage(), ex);
            }
        }
    }
}
