package br.dev.ctrls.inovareti.modules.appointment.application.service;

import io.micrometer.observation.annotation.Observed;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSession;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.NotificationGroup;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentDoctorMappingRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentExternalPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentSessionRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.NotificationGroupRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.AppointmentMotorProperties;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.BlipProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Componente responsável pelo processamento de baixa e cancelamento em lote
 * de agendamentos no banco de dados local e na API externa do Feegow ERP.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Observed
public class FeegowBulkIntegrationHandler {

    private final NotificationGroupRepositoryPort notificationGroupRepository;
    private final AppointmentSessionRepositoryPort appointmentSessionRepository;
    private final AppointmentDoctorMappingRepositoryPort appointmentDoctorMappingRepository;
    private final AppointmentExternalPort appointmentExternalPort;
    private final ConfirmationStateMachineService confirmationStateMachineService;
    private final AppointmentMotorProperties appointmentMotorProperties;
    private final TransactionTemplate transactionTemplate;
    private final BlipContextService blipContextService;
    private final BlipProperties blipProperties;
    // Responsável por resolver identidades de túnel, aliases e UUIDs do Blip para o telefone real do banco
    private final BlipIdentityReconciler blipIdentityReconciler;
    private final BlipNotificationService blipNotificationService;

    /**
     * Executa a confirmação em lote de todos os agendamentos pertencentes ao grupo
     * e os sincroniza com a API do Feegow ERP.
     *
     * Estratégias de busca de sessões (em ordem de prioridade):
     *   A) Por groupId na tabela notification_groups
     *   B) Por currentGroupId diretamente na tabela de sessões (populado na ingestão)
     *   C) Fallback por telefone purificado (caso A e B falhem)
     *
     * @param groupId ID do grupo de notificações
     * @param purifiedPhone telefone purificado do paciente
     * @return lista de sessões de agendamento afetadas
     */
    public List<AppointmentSession> executeConfirmBatch(UUID groupId, String purifiedPhone) {
        log.info("[BULK-INTEGRATION] Iniciando busca robusta de grupo para confirmação em lote. groupId: {}", groupId);

        java.util.Map<UUID, AppointmentSession> uniqueSessions = new java.util.LinkedHashMap<>();

        // --- Estratégia A: Buscar da tabela 'notification_groups' via groupId ---
        List<NotificationGroup> groups = notificationGroupRepository.findByGroupId(groupId);
        log.info("[BULK-INTEGRATION] [A] Busca por groupId na tabela notification_groups: {} registros.", groups.size());
        for (NotificationGroup group : groups) {
            appointmentSessionRepository.findById(group.getSessionId())
                .ifPresent(s -> {
                    uniqueSessions.put(s.getId(), s);
                    log.info("[BULK-INTEGRATION] [A] Sessão carregada via grupo: id={}, feegowId={}", s.getId(), s.getFeegowAppointmentId());
                });
        }

        // --- Estratégia B: Buscar diretamente pelo campo 'currentGroupId' nas sessões ---
        // Este campo é populado durante a ingestão (populateCurrentGroupIdOnSessions),
        // garantindo que funcione mesmo sem o clique em "Ver Agendamentos".
        List<AppointmentSession> sessionsByGroupField = appointmentSessionRepository.findByCurrentGroupId(groupId);
        log.info("[BULK-INTEGRATION] [B] Busca por currentGroupId na tabela de sessões: {} registros.", sessionsByGroupField.size());
        for (AppointmentSession s : sessionsByGroupField) {
            if (!uniqueSessions.containsKey(s.getId())) {
                uniqueSessions.put(s.getId(), s);
                log.info("[BULK-INTEGRATION] [B] Sessão carregada via campo currentGroupId: id={}, feegowId={}", s.getId(), s.getFeegowAppointmentId());
            }
        }

        // --- Estratégia C (Fallback): Se ainda vazio, buscar sessões ativas pelo telefone ---
        if (uniqueSessions.isEmpty() && purifiedPhone != null && !purifiedPhone.isBlank()) {
            log.warn("[BULK-INTEGRATION] [C] Estratégias A e B não encontraram sessões para groupId={}. Aplicando fallback por telefone: {}", groupId, purifiedPhone);
            List<AppointmentSession> activeContactSessions = appointmentSessionRepository.findActiveByPhoneNumber(purifiedPhone);
            for (AppointmentSession s : activeContactSessions) {
                uniqueSessions.put(s.getId(), s);
                log.info("[BULK-INTEGRATION] [C] Sessão de fallback carregada do telefone: id={}, feegowId={}", s.getId(), s.getFeegowAppointmentId());
            }
        }

        List<AppointmentSession> sessionList = new ArrayList<>(uniqueSessions.values());
        log.info("[BULK-INTEGRATION] Total de sessões elegíveis unificadas para confirmação em lote: {}", sessionList.size());

        // Guarda de segurança: sem sessões, não há o que confirmar
        if (sessionList.isEmpty()) {
            log.error("[BULK-INTEGRATION] ERRO CRÍTICO: Nenhuma sessão encontrada para groupId={}. " +
                "O Feegow NÃO será chamado. Verifique se currentGroupId foi populado na ingestão " +
                "e se o groupId recebido no webhook é o mesmo gerado na ingestão.", groupId);
            return sessionList;
        }

        // Resolver o statusConfirmado antes do loop
        int statusConfirmado = 7;
        String configuredStatusId = appointmentMotorProperties.getFeegowConfirmedStatusId();
        if (configuredStatusId != null && !configuredStatusId.isBlank()) {
            String trimmed = configuredStatusId.trim();
            if (!"2".equals(trimmed)) {
                try {
                    statusConfirmado = Integer.parseInt(trimmed);
                } catch (NumberFormatException ignored) {}
            }
        }

        // Atualizar status local para CONFIRMED e sincronizar com o Feegow para cada sessão
        for (AppointmentSession groupSession : sessionList) {
            // Usa findById (sem lock) em vez de findByIdLocked (SELECT FOR UPDATE).
            // O lock pessimista (PESSIMISTIC_WRITE) causava deadlock em VirtualThreads quando
            // outro processo (nudge, ingestão) tentava acessar a mesma sessão simultaneamente,
            // abortando o loop inteiro e impedindo qualquer chamada ao Feegow.
            transactionTemplate.executeWithoutResult(status -> {
                AppointmentSession fresh = appointmentSessionRepository.findById(groupSession.getId()).orElse(null);
                if (fresh != null) {
                    confirmationStateMachineService.markConfirmed(fresh);
                    appointmentSessionRepository.save(fresh);
                    log.info("[BULK-INTEGRATION] Status local da sessão {} atualizado para CONFIRMED.", fresh.getId());
                } else {
                    log.warn("[BULK-INTEGRATION] Sessão {} não encontrada no banco ao tentar confirmar.", groupSession.getId());
                }
            });

            // A chamada ao Feegow é feita FORA da transação de banco intencionalmente.
            // Chamadas de rede externas nunca devem estar dentro de transações JPA
            // para evitar bloqueio de conexões do pool durante a espera da resposta HTTP.
            try {
                log.info("[BULK-INTEGRATION] Enviando confirmação ao Feegow para agendamento ID: {}", groupSession.getFeegowAppointmentId());
                appointmentExternalPort.updateStatus(groupSession.getFeegowAppointmentId(), statusConfirmado);
                log.info("[BULK-INTEGRATION] Resposta do Feegow: SUCCESS para agendamento ID: {}", groupSession.getFeegowAppointmentId());
            } catch (Exception ex) {
                log.error("[BULK-INTEGRATION] Resposta do Feegow: ERROR para agendamento ID: {}. Erro: {}",
                    groupSession.getFeegowAppointmentId(), ex.getMessage(), ex);
            }
        }

        return sessionList;
    }

    /**
     * Resolve a fila Blip de redirecionamento com base nos médicos das sessões afetadas.
     *
     * @param sessionList lista de sessões de agendamento afetadas
     * @return nome da fila Blip resolvida
     */
    public String resolveTargetQueue(List<AppointmentSession> sessionList) {
        String targetQueue = null;
        for (AppointmentSession groupSession : sessionList) {
            var mappingOpt = appointmentDoctorMappingRepository.findByProfissionalId(groupSession.getDoctorProfissionalId());
            if (mappingOpt.isPresent()) {
                String queue = mappingOpt.get().getBlipQueueId();
                if (queue != null && !queue.isBlank() && !"null".equalsIgnoreCase(queue.trim())) {
                    targetQueue = queue.trim();
                    break;
                }
            }
        }

        if (targetQueue == null || targetQueue.isBlank()) {
            targetQueue = "Recepção Central / Suporte";
        }
        return targetQueue;
    }

    /**
     * Resolve a fila Blip de redirecionamento para o grupo correspondente à solicitação de alteração.
     *
     * @param groupId ID do grupo de notificações
     * @return nome da fila Blip resolvida
     */
    public String resolveAlterGroupQueue(UUID groupId) {
        String targetQueue = "Recepção Central / Suporte";
        List<NotificationGroup> groups = notificationGroupRepository.findByGroupId(groupId);
        if (groups != null && !groups.isEmpty()) {
            UUID firstSessionId = groups.get(0).getSessionId();
            AppointmentSession firstSession = transactionTemplate.execute(status ->
                appointmentSessionRepository.findById(firstSessionId).orElse(null)
            );
            if (firstSession != null) {
                var mappingOpt = appointmentDoctorMappingRepository.findByProfissionalId(firstSession.getDoctorProfissionalId());
                if (mappingOpt.isPresent()) {
                    String queue = mappingOpt.get().getBlipQueueId();
                    if (queue != null && !queue.isBlank() && !"null".equalsIgnoreCase(queue.trim())) {
                        targetQueue = queue.trim();
                    }
                }
            }
        }
        return targetQueue;
    }

    /**
     * Processa de forma assíncrona a confirmação em lote de todos os agendamentos pertencentes ao grupo
     * e os sincroniza com a API do Feegow ERP, redirecionando o fluxo do paciente no Blip.
     */
    @org.springframework.scheduling.annotation.Async
    public void confirmGroupAsync(UUID groupId, String fromPhone) {
        log.info("[ASYNC-BATCH] Iniciando processamento assíncrono de confirm_group para groupId: {}", groupId);
        try {
            // Resolve o telefone real do paciente usando o reconciliador de identidade do Blip.
            // Isso garante que túneis, aliases e UUIDs do Blip sejam convertidos para o número
            // telefônico real armazenado no banco, necessário para as estratégias de fallback.
            String dbPhone = fromPhone != null
                ? blipIdentityReconciler.resolveAndReconcileIdentity(fromPhone, null)
                : null;
            log.info("[ASYNC-BATCH] Telefone resolvido para confirmação em lote: fromPhone={} -> dbPhone={}", fromPhone, dbPhone);

            // 1. Executa a confirmação em lote local e na API Feegow
            List<AppointmentSession> sessionList = executeConfirmBatch(groupId, dbPhone);

            // 2. Resolve a fila de desempate para redirecionamento no Blip
            String targetQueue = resolveTargetQueue(sessionList);

            // 3. Configura o redirecionamento no Blip em background
            if (fromPhone != null && !fromPhone.isBlank()) {
                // Envia a mensagem de sucesso de confirmação configurada nas propriedades
                String confirmSuccessText = blipProperties.getTexts().getConfirmSuccess();
                if (confirmSuccessText != null && !confirmSuccessText.isBlank()) {
                    log.info("[ASYNC-BATCH] Enviando mensagem de sucesso de confirmação para {} via Roteador", fromPhone);
                    blipNotificationService.sendPlainTextMessage(fromPhone.trim(), confirmSuccessText);
                }

                blipContextService.setQueueRedirect(fromPhone.trim(), targetQueue);
                String deskBlockId = blipProperties.getBlocks().getDeskStateId();
                blipContextService.setMasterState(fromPhone.trim(), "desk@msging.net", deskBlockId);
                log.info("[ASYNC-BATCH] Paciente {} redirecionado com sucesso para a fila '{}', bloco desk: '{}'",
                    fromPhone, targetQueue, deskBlockId);
            }
        } catch (Exception e) {
            log.error("[ASYNC-BATCH] Erro crítico no processamento assíncrono de confirmação do grupo: " + groupId, e);
        }
    }

    /**
     * Processa de forma assíncrona o redirecionamento de alteração em lote do grupo de agendamentos no Blip.
     */
    @org.springframework.scheduling.annotation.Async
    public void alterGroupAsync(UUID groupId, String fromPhone) {
        log.info("[ASYNC-BATCH] Iniciando processamento assíncrono de alter_group para groupId: {}", groupId);
        try {
            String targetQueue = resolveAlterGroupQueue(groupId);

            if (fromPhone != null && !fromPhone.isBlank()) {
                // Envia a mensagem de solicitação de alteração configurada nas propriedades
                String alterRequestText = blipProperties.getTexts().getAlterRequest();
                if (alterRequestText != null && !alterRequestText.isBlank()) {
                    String finalAlterText = alterRequestText;
                    try {
                        String dbPhone = blipIdentityReconciler.resolveAndReconcileIdentity(fromPhone, null);
                        List<AppointmentSession> sessions = appointmentSessionRepository.findActiveByPhoneNumber(dbPhone);
                        if (sessions != null && !sessions.isEmpty()) {
                            String patientName = "Paciente";
                            String doctorName = "Médico";
                            
                            AppointmentSession s = sessions.get(0);
                            var mapping = appointmentDoctorMappingRepository.findByProfissionalId(s.getDoctorProfissionalId()).orElse(null);
                            if (mapping != null && mapping.getProfissionalNome() != null && !mapping.getProfissionalNome().isBlank()) {
                                doctorName = mapping.getProfissionalNome();
                            }
                            
                            finalAlterText = finalAlterText.replace("{patientName}", patientName)
                                                           .replace("{doctorName}", doctorName);
                        }
                    } catch (Exception ex) {
                        log.warn("Erro ao fazer replace de variáveis no texto de alteração: {}", ex.getMessage());
                    }

                    log.info("[ASYNC-BATCH] Enviando mensagem de solicitação de alteração para {} via Roteador", fromPhone);
                    blipNotificationService.sendPlainTextMessage(fromPhone.trim(), finalAlterText);
                }

                blipContextService.setQueueRedirect(fromPhone.trim(), targetQueue);
                String deskBlockId = blipProperties.getBlocks().getDeskStateId();
                blipContextService.setMasterState(fromPhone.trim(), "desk@msging.net", deskBlockId);
                log.info("[ASYNC-BATCH] Paciente {} redirecionado com sucesso para alteração na fila '{}', bloco desk: '{}'",
                    fromPhone, targetQueue, deskBlockId);
            }
        } catch (Exception e) {
            log.error("[ASYNC-BATCH] Erro crítico no processamento assíncrono de alteração do grupo: " + groupId, e);
        }
    }
}