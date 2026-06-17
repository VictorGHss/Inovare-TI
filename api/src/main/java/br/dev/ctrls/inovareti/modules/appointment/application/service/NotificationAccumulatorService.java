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

        log.info("[ACÚMULO] Iniciando rotina de acúmulo de notificações");

        // 1. Buscar agendamentos pendentes de notificação no banco de dados
        List<AppointmentSession> pendingSessions = appointmentSessionRepository.findPendingNotifications();
        if (pendingSessions.isEmpty()) {
            log.info("[ACÚMULO] Nenhum agendamento pendente de notificação encontrado");
            return;
        }

        log.info("[ACÚMULO] Encontrados {} agendamentos pendentes", pendingSessions.size());

        // 2. Agrupar essas entidades pelo telefone (contact_identity / patient_phone)
        Map<String, List<AppointmentSession>> groupedByPhone = pendingSessions.stream()
                .filter(s -> s.getPhoneNumber() != null && !s.getPhoneNumber().isBlank())
                .collect(Collectors.groupingBy(AppointmentSession::getPhoneNumber));

        boolean hasSentBefore = false;
        for (Map.Entry<String, List<AppointmentSession>> entry : groupedByPhone.entrySet()) {
            String phoneNumber = entry.getKey();
            List<AppointmentSession> sessions = entry.getValue();

            // Implementação do Delay Seguro (Pacing/Throttling) em Português do Brasil (PT-BR)
            // Se já houver um disparo anterior neste lote do acumulador, aplica-se um delay controlado de 150 a 300 ms.
            // O uso de Thread.sleep é seguro porque o projeto utiliza as Virtual Threads do Java 21,
            // não bloqueando a CPU do servidor.
            if (hasSentBefore) {
                long delayMillis = java.util.concurrent.ThreadLocalRandom.current().nextLong(150, 301);
                log.info("[PACING-LOG] Aplicando espaçamento temporal controlado de {} milissegundos antes do próximo disparo de notificação acumulada.", delayMillis);
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("[PACING-LOG] O delay de espaçamento no acumulador foi interrompido: {}", e.getMessage());
                }
            } else {
                hasSentBefore = true;
            }

            // 3. Verificar se a lista tem tamanho > 1 (é um grupo) ou == 1 (individual)
            if (sessions.size() > 1) {
                processGroupNotification(phoneNumber, sessions);
            } else {
                processIndividualNotification(sessions.get(0));
            }
        }
    }

    private void processIndividualNotification(AppointmentSession session) {
        log.info("[ACÚMULO] Processando notificação individual para o agendamento Feegow ID: {}", session.getFeegowAppointmentId());
        
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
                log.info("[ACÚMULO] Notificação individual enviada com sucesso para: {}", session.getPhoneNumber());
            } else {
                log.warn("[ACÚMULO] Falha ao enviar notificação individual para: {}", session.getPhoneNumber());
            }
        } catch (RuntimeException e) {
            log.error("[ACÚMULO] Erro ao processar notificação individual para sessionId: {}", session.getId(), e);
        }
    }

    private void processGroupNotification(String phoneNumber, List<AppointmentSession> sessions) {
        UUID groupId = UUID.randomUUID();
        log.info("[ACÚMULO] Processando notificação agrupada. group_id={}, telefone={}, total_consultas={}", 
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
                
                // Atualizar o lastNotificationSentAt para todas as sessões do grupo
                for (AppointmentSession session : sessions) {
                    AppointmentSession lockedSession = appointmentSessionRepository.findByIdLocked(session.getId()).orElse(null);
                    if (lockedSession != null) {
                        lockedSession.setLastNotificationSentAt(LocalDateTime.now());
                        appointmentSessionRepository.save(lockedSession);
                    }
                }
            });
        } catch (RuntimeException e) {
            log.error("[ACÚMULO] Erro ao persistir grupo de notificação ou atualizar sessões no banco de dados", e);
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
            log.warn("[ACÚMULO] Não foi possível recuperar o nome do paciente via API externa. Usando fallback.", e);
        }

        // 5. Disparar o template de aviso passando o group_id no payload
        try {
            String templateName = motorProperties.getBlipTemplateGroup();
            blipNotificationService.sendGroupTemplateMessage(phoneNumber, templateName, groupId, patientName);
            log.info("[ACÚMULO] Notificação agrupada enviada com sucesso para: {}", phoneNumber);
        } catch (Exception e) {
            log.error("[ACÚMULO] Erro ao disparar template de notificação agrupada para: {}", phoneNumber, e);
        }
    }

    @Scheduled(cron = "${app.appointment.motor.cleanup-cron:0 0 3 * * ?}")
    public void cleanupOldNotificationGroups() {
        log.info("[CLEANUP] Iniciando rotina de limpeza diária de grupos de notificação e sessões antigas");
        LocalDateTime threshold = LocalDateTime.now().minusDays(45);
        try {
            long count = transactionTemplate.execute(status -> 
                notificationGroupRepository.deleteByCreatedAtBefore(threshold)
            );
            log.info("[CLEANUP] Removidos grupos de notificação com mais de 45 dias. Total: {}", count);
        } catch (RuntimeException e) {
            log.error("[CLEANUP] Erro ao executar limpeza de grupos de notificação antigos", e);
        }

        cleanupOldClosedSessions(threshold);
    }

    private void cleanupOldClosedSessions(LocalDateTime threshold) {
        log.info("[CLEANUP] Iniciando expurgo de sessões de agendamento concluídas/canceladas antigas");
        try {
            List<br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSessionStatus> finalStatuses = List.of(
                br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSessionStatus.CONFIRMED,
                br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSessionStatus.CANCELED,
                br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSessionStatus.CANCELED_NO_RESPONSE
            );
            long countSessions = transactionTemplate.execute(status ->
                appointmentSessionRepository.deleteByStatusInAndCreatedAtBefore(finalStatuses, threshold)
            );
            log.info("[CLEANUP] Removidas sessões concluídas/canceladas com mais de 45 dias. Total: {}", countSessions);
        } catch (RuntimeException e) {
            log.error("[CLEANUP] Erro ao executar expurgo de sessões de agendamento antigas", e);
        }
    }
}


