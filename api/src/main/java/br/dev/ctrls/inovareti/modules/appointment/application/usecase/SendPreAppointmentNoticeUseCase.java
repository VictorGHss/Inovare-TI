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
    private final br.dev.ctrls.inovareti.modules.access.domain.port.output.BlipContactClientPort blipContactClientPort;
    private final br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentDoctorMappingRepositoryPort appointmentDoctorMappingRepository;
    private final br.dev.ctrls.inovareti.modules.appointment.application.service.BlipContextService blipContextService;
    private final br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentExternalPort appointmentExternalPort;

    public void execute() {
        if (!appointmentMotorProperties.isEnabled()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"));
        // Janela de 2h a 1h antes da consulta (de 60 a 120 minutos no futuro)
        LocalDateTime windowStart = now.plusMinutes(60);
        LocalDateTime windowEnd = now.plusMinutes(120);

        List<AppointmentSession> candidateSessions = appointmentSessionRepository.findConfirmedSessionsInWindow(windowStart, windowEnd);

        if (candidateSessions == null || candidateSessions.isEmpty()) {
            return;
        }

        log.info("[LEMBRETE-ANTECEDENCIA] Encontradas {} consulta(s) confirmada(s) elegíveis para o template '{}' (2h a 1h antes).",
                candidateSessions.size(), TEMPLATE_NAME);

        String eligibleIdsProp = appointmentMotorProperties.getEligibleProcedureIds();
        List<String> eligibleProcedureIds = (eligibleIdsProp != null && !eligibleIdsProp.isBlank())
                ? java.util.Arrays.stream(eligibleIdsProp.split(",")).map(id -> id.trim()).filter(s -> !s.isEmpty()).toList()
                : java.util.Collections.emptyList();

        for (AppointmentSession session : candidateSessions) {
            try {
                if (session.getPhoneNumber() == null || session.getPhoneNumber().isBlank()) {
                    continue;
                }

                // Filtros de segurança: verifica se o agendamento no Feegow é encaixe ou procedimento inelegível (ex: tarefa, cirurgia)
                if (session.getFeegowAppointmentId() != null && !session.getFeegowAppointmentId().isBlank()) {
                    try {
                        var feegowAppt = appointmentExternalPort.findById(session.getFeegowAppointmentId().trim());
                        if (feegowAppt != null) {
                            if (feegowAppt.encaixe() != null && feegowAppt.encaixe()) {
                                log.info("[LEMBRETE-ANTECEDENCIA] Abortando envio para sessão ID={} (Feegow ID={}) pois é um ENCAIXE.", session.getId(), session.getFeegowAppointmentId());
                                continue;
                            }
                            if (feegowAppt.procedureId() != null && !eligibleProcedureIds.isEmpty() && !eligibleProcedureIds.contains(feegowAppt.procedureId().trim())) {
                                log.info("[LEMBRETE-ANTECEDENCIA] Abortando envio para sessão ID={} (Feegow ID={}) pois o procedimento '{}' ({}) não é elegível para lembrete.",
                                        session.getId(), session.getFeegowAppointmentId(), feegowAppt.procedureId(), feegowAppt.procedureName());
                                continue;
                            }
                        }
                    } catch (Exception fEx) {
                        log.warn("[LEMBRETE-ANTECEDENCIA] Falha ao consultar Feegow para verificar elegibilidade do agendamento ID={}: {}", session.getFeegowAppointmentId(), fEx.getMessage());
                    }
                }

                // Reconstrói dados do template aplicando regras de nome, médico e offset de horário (-10 min)
                var templateData = appointmentTemplateDataBuilder.build(session);

                String resolvedQueue = "Recepção Central / Suporte";
                if (session.getDoctorProfissionalId() != null && !session.getDoctorProfissionalId().isBlank()) {
                    var mappingOpt = appointmentDoctorMappingRepository.findByProfissionalId(session.getDoctorProfissionalId().trim());
                    if (mappingOpt.isPresent()) {
                        String queueId = mappingOpt.get().getBlipQueueId();
                        if (queueId != null && !queueId.isBlank() && !"null".equalsIgnoreCase(queueId.trim())) {
                            resolvedQueue = blipContextService.resolveQueueName(queueId.trim());
                        }
                    }
                }

                // Sincroniza o contato no Blip com a fila exata do médico
                blipContactClientPort.syncContact(session.getPhoneNumber(), templateData.patientName(), "", resolvedQueue, session.getDoctorProfissionalId());

                // Força a atualização do Master-State do paciente no Blip para o bloco Preparar_Atendimento (stateId = a0776d9c-6486-42f3-8a4f-2706f0185908)
                String prepararAtendimentoBlockId = "a0776d9c-6486-42f3-8a4f-2706f0185908";
                try {
                    String cleanPhone = session.getPhoneNumber().replaceAll("\\D", "");
                    if (!cleanPhone.startsWith("55") && !cleanPhone.isBlank()) {
                        cleanPhone = "55" + cleanPhone;
                    }
                    String masterIdentity = cleanPhone + "@wa.gw.msging.net";
                    String tunnelIdentity = cleanPhone + ".fluxov1@tunnel.msging.net";
                    
                    blipContextService.setBuilderMasterState(masterIdentity, prepararAtendimentoBlockId);
                    blipContextService.setBuilderMasterState(tunnelIdentity, prepararAtendimentoBlockId);
                    blipContextService.setBuilderMasterState(session.getPhoneNumber(), prepararAtendimentoBlockId);
                    log.info("[LEMBRETE-ANTECEDENCIA] Master-State do Blip atualizado para Preparar_Atendimento ({}) no paciente {}", prepararAtendimentoBlockId, session.getPhoneNumber());
                } catch (Exception ex) {
                    log.warn("[LEMBRETE-ANTECEDENCIA] Falha ao atualizar Master-State no Blip para {}: {}", session.getPhoneNumber(), ex.getMessage());
                }

                log.info("[LEMBRETE-ANTECEDENCIA] Disparando template '{}' para paciente='{}', médico='{}', hora='{}', tel='{}', fila='{}'",
                        TEMPLATE_NAME, templateData.patientName(), templateData.doctorName(),
                        templateData.appointmentTime(), session.getPhoneNumber(), resolvedQueue);

                blipNotificationService.sendTemplateMessage(session.getPhoneNumber(), TEMPLATE_NAME, templateData);

                // Marca idempotência no banco para não reenviar no próximo ciclo
                session.setStatusDetails("PRE_NOTICE_SENT_" + now.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")));
                session.setLastNotificationSentAt(now);
                appointmentSessionRepository.save(session);

            } catch (Exception ex) {
                log.error("[LEMBRETE-ANTECEDENCIA] Falha ao disparar template '{}' para sessão ID={}: {}",
                        TEMPLATE_NAME, session.getId(), ex.getMessage(), ex);
            }
        }
    }
}
