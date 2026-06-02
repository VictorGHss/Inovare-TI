package br.dev.ctrls.inovareti.modules.appointment.application.service;

import io.micrometer.observation.annotation.Observed;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentCategory;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSession;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.NotificationGroup;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentSessionRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.NotificationGroupRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.PatientExternalPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.FeegowPatient;
import br.dev.ctrls.inovareti.modules.appointment.application.usecase.SendAppointmentTemplateUseCase;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.AppointmentMotorProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Observed
public class NotificationAccumulatorService {

    private final AppointmentSessionRepositoryPort appointmentSessionRepository;
    private final NotificationGroupRepositoryPort notificationGroupRepository;
    private final PatientExternalPort patientExternalPort;
    private final SendAppointmentTemplateUseCase sendAppointmentTemplateUseCase;
    private final BlipNotificationService blipNotificationService;
    private final AppointmentMotorProperties motorProperties;
    private final TransactionTemplate transactionTemplate;

    @Scheduled(cron = "${app.appointment.motor.accumulator-cron:0 0/15 * * * ?}")
    public void accumulateAndSendNotifications() {
        if (!motorProperties.isEnabled()) {
            return;
        }

        log.info("[ACÃšMULO] Iniciando rotina de acÃºmulo de notificaÃ§Ãµes");

        // 1. Buscar agendamentos pendentes de notificaÃ§Ã£o no banco de dados
        List<AppointmentSession> pendingSessions = appointmentSessionRepository.findPendingNotifications();
        if (pendingSessions.isEmpty()) {
            log.info("[ACÃšMULO] Nenhum agendamento pendente de notificaÃ§Ã£o encontrado");
            return;
        }

        log.info("[ACÃšMULO] Encontrados {} agendamentos pendentes", pendingSessions.size());

        // 2. Agrupar essas entidades pelo telefone (contact_identity / patient_phone)
        Map<String, List<AppointmentSession>> groupedByPhone = pendingSessions.stream()
                .filter(s -> s.getPhoneNumber() != null && !s.getPhoneNumber().isBlank())
                .collect(Collectors.groupingBy(AppointmentSession::getPhoneNumber));

        for (Map.Entry<String, List<AppointmentSession>> entry : groupedByPhone.entrySet()) {
            String phoneNumber = entry.getKey();
            List<AppointmentSession> sessions = entry.getValue();

            // 3. Verificar se a lista tem tamanho > 1 (Ã© um grupo) ou == 1 (individual)
            if (sessions.size() > 1) {
                processGroupNotification(phoneNumber, sessions);
            } else {
                processIndividualNotification(sessions.get(0));
            }
        }
    }

    private void processIndividualNotification(AppointmentSession session) {
        log.info("[ACÃšMULO] Processando notificaÃ§Ã£o individual para o agendamento Feegow ID: {}", session.getFeegowAppointmentId());
        
        try {
            boolean sent = sendAppointmentTemplateUseCase.execute(session, AppointmentCategory.CONFIRMATION);
            if (sent) {
                transactionTemplate.executeWithoutResult(status -> {
                    AppointmentSession lockedSession = appointmentSessionRepository.findByIdLocked(session.getId()).orElse(null);
                    if (lockedSession != null) {
                        lockedSession.setLastNotificationSentAt(LocalDateTime.now());
                        appointmentSessionRepository.save(lockedSession);
                    }
                });
                log.info("[ACÃšMULO] NotificaÃ§Ã£o individual enviada com sucesso para: {}", session.getPhoneNumber());
            } else {
                log.warn("[ACÃšMULO] Falha ao enviar notificaÃ§Ã£o individual para: {}", session.getPhoneNumber());
            }
        } catch (Exception e) {
            log.error("[ACÃšMULO] Erro ao processar notificaÃ§Ã£o individual para sessionId: {}", session.getId(), e);
        }
    }

    private void processGroupNotification(String phoneNumber, List<AppointmentSession> sessions) {
        UUID groupId = UUID.randomUUID();
        log.info("[ACÃšMULO] Processando notificaÃ§Ã£o agrupada. group_id={}, telefone={}, total_consultas={}", 
            groupId, phoneNumber, sessions.size());

        // 4. Salvar na tabela 'notification_groups'
        List<NotificationGroup> groupEntities = new ArrayList<>();
        for (AppointmentSession session : sessions) {
            NotificationGroup group = NotificationGroup.builder()
                    .groupId(groupId)
                    .sessionId(session.getId())
                    .createdAt(LocalDateTime.now())
                    .build();
            groupEntities.add(group);
        }

        try {
            transactionTemplate.executeWithoutResult(status -> {
                notificationGroupRepository.saveAll(groupEntities);
                
                // Atualizar o lastNotificationSentAt para todas as sessÃµes do grupo
                for (AppointmentSession session : sessions) {
                    AppointmentSession lockedSession = appointmentSessionRepository.findByIdLocked(session.getId()).orElse(null);
                    if (lockedSession != null) {
                        lockedSession.setLastNotificationSentAt(LocalDateTime.now());
                        appointmentSessionRepository.save(lockedSession);
                    }
                }
            });
        } catch (Exception e) {
            log.error("[ACÃšMULO] Erro ao persistir grupo de notificaÃ§Ã£o ou atualizar sessÃµes no banco de dados", e);
            return;
        }

        // Buscar nome do paciente para personalizar o template (usando o primeiro da lista)
        String patientName = "Paciente";
        try {
            FeegowPatient patientInfo = patientExternalPort.patientInfo(sessions.get(0).getPatientId());
            if (patientInfo != null && patientInfo.name() != null && !patientInfo.name().isBlank()) {
                patientName = patientInfo.name().trim();
            }
        } catch (Exception e) {
            log.warn("[ACÃšMULO] NÃ£o foi possÃ­vel recuperar o nome do paciente via API externa. Usando fallback.", e);
        }

        // 5. Disparar o template de aviso passando o group_id no payload
        try {
            String templateName = motorProperties.getBlipGroupTemplateName();
            blipNotificationService.sendGroupTemplateMessage(phoneNumber, templateName, groupId, patientName);
            log.info("[ACÃšMULO] NotificaÃ§Ã£o agrupada enviada com sucesso para: {}", phoneNumber);
        } catch (Exception e) {
            log.error("[ACÃšMULO] Erro ao disparar template de notificaÃ§Ã£o agrupada para: {}", phoneNumber, e);
        }
    }

    @Scheduled(cron = "${app.appointment.motor.cleanup-cron:0 0 3 * * ?}")
    public void cleanupOldNotificationGroups() {
        log.info("[CLEANUP] Iniciando rotina de limpeza diÃ¡ria de grupos de notificaÃ§Ã£o e sessÃµes antigas");
        LocalDateTime threshold = LocalDateTime.now().minusDays(45);
        try {
            long count = transactionTemplate.execute(status -> 
                notificationGroupRepository.deleteByCreatedAtBefore(threshold)
            );
            log.info("[CLEANUP] Removidos grupos de notificaÃ§Ã£o com mais de 45 dias. Total: {}", count);
        } catch (Exception e) {
            log.error("[CLEANUP] Erro ao executar limpeza de grupos de notificaÃ§Ã£o antigos", e);
        }

        cleanupOldClosedSessions(threshold);
    }

    private void cleanupOldClosedSessions(LocalDateTime threshold) {
        log.info("[CLEANUP] Iniciando expurgo de sessÃµes de agendamento concluÃ­das/canceladas antigas");
        try {
            List<br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSessionStatus> finalStatuses = List.of(
                br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSessionStatus.CONFIRMED,
                br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSessionStatus.CANCELED,
                br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSessionStatus.CANCELED_NO_RESPONSE
            );
            long countSessions = transactionTemplate.execute(status ->
                appointmentSessionRepository.deleteByStatusInAndCreatedAtBefore(finalStatuses, threshold)
            );
            log.info("[CLEANUP] Removidas sessÃµes concluÃ­das/canceladas com mais de 45 dias. Total: {}", countSessions);
        } catch (Exception e) {
            log.error("[CLEANUP] Erro ao executar expurgo de sessÃµes de agendamento antigas", e);
        }
    }
}


