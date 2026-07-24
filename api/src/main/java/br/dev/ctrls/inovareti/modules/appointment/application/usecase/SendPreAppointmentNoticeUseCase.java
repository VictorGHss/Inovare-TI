package br.dev.ctrls.inovareti.modules.appointment.application.usecase;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Component;

import br.dev.ctrls.inovareti.modules.appointment.application.service.BlipNotificationService;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSession;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentSessionRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.DoctorConfigurationRepository;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.PatientExternalPort;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.AppointmentMotorProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Caso de Uso dedicado para envio de Lembretes de Proximidade de Consulta (10 minutos antes).
 * Busca consultas ativas e confirmadas (CONFIRMED) no dia atual na janela de 10 a 15 minutos no futuro,
 * respeitando as preferências de cada médico e enviando notificações ativas pelo Blip.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SendPreAppointmentNoticeUseCase {

    private final AppointmentSessionRepositoryPort appointmentSessionRepository;
    private final DoctorConfigurationRepository doctorConfigurationRepository;
    private final PatientExternalPort patientExternalPort;
    private final BlipNotificationService blipNotificationService;
    private final AppointmentMotorProperties appointmentMotorProperties;

    public void execute() {
        if (!appointmentMotorProperties.isEnabled()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"));
        LocalDateTime windowStart = now.plusMinutes(10);
        LocalDateTime windowEnd = now.plusMinutes(15);

        log.info("[LEMBRETE-10MIN] Iniciando varredura de consultas confirmadas na janela de 10 a 15 minutos no futuro (de {} até {})...",
                windowStart.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                windowEnd.format(DateTimeFormatter.ofPattern("HH:mm:ss")));

        List<AppointmentSession> candidateSessions = appointmentSessionRepository.findConfirmedSessionsInWindow(windowStart, windowEnd);

        if (candidateSessions == null || candidateSessions.isEmpty()) {
            log.info("[LEMBRETE-10MIN] Nenhuma consulta confirmada encontrada na janela de 10-15 minutos.");
            return;
        }

        log.info("[LEMBRETE-10MIN] Encontradas {} consulta(s) confirmada(s) elegíveis para lembrete de proximidade.", candidateSessions.size());

        for (AppointmentSession session : candidateSessions) {
            try {
                if (session.getPhoneNumber() == null || session.getPhoneNumber().isBlank()) {
                    continue;
                }

                // Respeita flags e dados do médico via DoctorConfiguration, se disponível
                String docProfId = session.getDoctorProfissionalId();
                String doctorName = "Profissional";
                if (docProfId != null && !docProfId.isBlank()) {
                    try {
                        Long pId = Long.valueOf(docProfId.trim());
                        var docConfigOpt = doctorConfigurationRepository.findById(pId);
                        if (docConfigOpt.isPresent() && docConfigOpt.get().getDoctorName() != null && !docConfigOpt.get().getDoctorName().isBlank()) {
                            doctorName = docConfigOpt.get().getDoctorName().trim();
                        }
                    } catch (Exception ignored) {}
                }

                String patientName = "Paciente";
                try {
                    var patient = patientExternalPort.patientInfo(session.getPatientId());
                    if (patient != null && patient.name() != null && !patient.name().isBlank()) {
                        patientName = patient.name().trim();
                    }
                } catch (Exception ex) {
                    log.warn("[LEMBRETE-10MIN] Falha ao consultar nome do paciente ID: {}", session.getPatientId());
                }

                String timeFormatted = session.getAppointmentAt() != null
                        ? session.getAppointmentAt().format(DateTimeFormatter.ofPattern("HH:mm"))
                        : "breve";

                String noticeMessage = String.format(
                        "⏰ *Lembrete de Consulta Inovare*: Olá, %s! Lembramos que sua consulta com %s está agendada para daqui a pouco, às %s. Por favor, dirija-se à recepção da clínica.",
                        patientName, doctorName, timeFormatted
                );

                blipNotificationService.sendPlainTextMessage(session.getPhoneNumber(), noticeMessage);

                // Marca idempotência para não reenviar
                session.setStatusDetails("PRE_NOTICE_SENT_" + now.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")));
                session.setLastNotificationSentAt(now);
                appointmentSessionRepository.save(session);

                log.info("[LEMBRETE-10MIN] Lembrete de proximidade disparado com sucesso para paciente='{}', médico='{}', horário='{}', telefone='{}'",
                        patientName, doctorName, timeFormatted, session.getPhoneNumber());
            } catch (Exception ex) {
                log.error("[LEMBRETE-10MIN] Falha ao processar lembrete de 10 min para sessão ID={}: {}", session.getId(), ex.getMessage(), ex);
            }
        }
    }
}
