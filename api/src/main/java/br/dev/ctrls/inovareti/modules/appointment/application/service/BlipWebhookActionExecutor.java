package br.dev.ctrls.inovareti.modules.appointment.application.service;

import java.util.List;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import br.dev.ctrls.inovareti.core.exception.NotFoundException;
import br.dev.ctrls.inovareti.modules.appointment.application.dto.AppointmentPayload;
import br.dev.ctrls.inovareti.modules.appointment.application.usecase.HandleBlipWebhookUseCase;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSession;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentSessionRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.FeegowPatient;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.PatientExternalPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Coordenador do pipeline de processamento das ações do webhook do Blip,
 * encapsulando as etapas comuns (duplicidade, persistência microscópica, notificações Blip).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BlipWebhookActionExecutor {

    private final List<BlipWebhookActionHandler> handlers;
    private final PatientExternalPort patientExternalPort;
    private final AppointmentSessionRepositoryPort appointmentSessionRepository;
    private final BlipContextService blipContextService;
    private final BlipIdempotencyService blipIdempotencyService;
    private final TransactionTemplate transactionTemplate;

    /**
     * Executa a pipeline completa da ação correspondente (confirm ou alter).
     * 
     * @param actionType tipo da ação ("confirm" ou "alter")
     * @param action a ação original recebida no webhook
     * @param appointmentId ID do agendamento Feegow
     * @param session entidade de sessão do agendamento
     * @param doctorName nome limpo do médico
     * @param queue fila resolvida para transferência
     * @param dispatchIdentity número/identificador do destinatário Blip
     * @return resultado estruturado WebhookResult
     */
    public HandleBlipWebhookUseCase.WebhookResult execute(
            String actionType,
            String action,
            String appointmentId,
            AppointmentSession session,
            String doctorName,
            String queue,
            String dispatchIdentity
    ) {
        BlipWebhookActionHandler handler = handlers.stream()
                .filter(h -> h.supports(actionType))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Ação não suportada pelo executor: " + actionType));

        // 1. Bloqueio de Duplicidade no Banco
        if ("CONFIRMED".equalsIgnoreCase(session.getStatus().name())) {
            log.info("[WEBHOOK] Agendamento {} já está confirmado no banco. Ignorando processamento duplicado para evitar múltiplas mensagens.", appointmentId);

            FeegowPatient patient = patientExternalPort.patientInfo(session.getPatientId());
            String patientName = (patient.name() == null || patient.name().isBlank()) ? "Paciente" : patient.name();
            String formattedBirthdate = formatBirthdate(patient.birthdate());
            HandleBlipWebhookUseCase.WebhookResult result = new HandleBlipWebhookUseCase.WebhookResult(
                    queue, patientName, patient.cpf(), formattedBirthdate, actionType, doctorName
            );

            log.info("[WEBHOOK] Agendamento {} já confirmado. Retornando WebhookResult populado: action={}, queue={}, patientName={}, doctorName={}",
                    appointmentId, result.action(), result.queue(), result.patientName(), result.doctorName());

            blipIdempotencyService.saveCachedResult(appointmentId, result);

            if (dispatchIdentity != null) {
                // Atualiza telefone no banco caso tenha vindo diferente (Transação microscópica de gravação)
                try {
                    transactionTemplate.executeWithoutResult(status -> {
                        AppointmentSession currentSession = appointmentSessionRepository.findByFeegowAppointmentId(appointmentId).orElse(null);
                        if (currentSession != null) {
                            currentSession.setPhoneNumber(dispatchIdentity);
                            appointmentSessionRepository.save(currentSession);
                        }
                    });
                } catch (DataAccessException | IllegalStateException ex) {
                    log.error("Falha ao atualizar telefone na sessão já confirmada. appointmentId={}", appointmentId, ex);
                }

                AppointmentPayload appointmentPayload = AppointmentPayload.builder()
                        .action(result.action() != null ? result.action() : "")
                        .doctorName(result.doctorName() != null ? result.doctorName() : "")
                        .queue(result.queue() != null ? result.queue() : "")
                        .patientName(result.patientName() != null ? result.patientName() : "")
                        .patientCPF(result.patientCPF() != null ? result.patientCPF() : "")
                        .patientBirthdate(result.patientBirthdate() != null ? result.patientBirthdate() : "")
                        .build();
                blipContextService.processAppointmentPush(dispatchIdentity, result.action(), appointmentPayload);
            }
            return result;
        }

        // FASE 2: Chamada externa pré-persistência específica da ação
        handler.prePersistence(session, action);

        // FASE 3: Persistência no Banco de Dados em Transação Microscópica
        try {
            transactionTemplate.executeWithoutResult(status -> {
                AppointmentSession currentSession = appointmentSessionRepository.findByFeegowAppointmentId(appointmentId)
                        .orElseThrow(() -> new NotFoundException("Sessão não encontrada para appointmentId=" + appointmentId));

                if (dispatchIdentity != null) {
                    currentSession.setPhoneNumber(dispatchIdentity);
                }

                handler.applySessionState(currentSession, action);

                appointmentSessionRepository.save(currentSession);
            });
        } catch (DataAccessException | IllegalStateException ex) {
            log.error("Falha grave na gravação dos dados do webhook no banco de dados para appointmentId={}. Detalhes: {}", appointmentId, ex.getMessage(), ex);
            throw ex;
        }

        log.info("[WEBHOOK] Processamento concluído para {}. Fila: {}", actionType, queue);

        // FASE 4: Chamada HTTP Externa para retornar dados do paciente (Pós-persistência)
        FeegowPatient patient = patientExternalPort.patientInfo(session.getPatientId());
        String patientName = (patient.name() == null || patient.name().isBlank()) ? "Paciente" : patient.name();
        String formattedBirthdate = formatBirthdate(patient.birthdate());

        HandleBlipWebhookUseCase.WebhookResult finalResult = new HandleBlipWebhookUseCase.WebhookResult(
                queue, patientName, patient.cpf(), formattedBirthdate, actionType, doctorName
        );

        blipIdempotencyService.saveCachedResult(appointmentId, finalResult);

        if (dispatchIdentity != null) {
            AppointmentPayload appointmentPayload = AppointmentPayload.builder()
                    .action(finalResult.action() != null ? finalResult.action() : "")
                    .doctorName(finalResult.doctorName() != null ? finalResult.doctorName() : "")
                    .queue(finalResult.queue() != null ? finalResult.queue() : "")
                    .patientName(finalResult.patientName() != null ? finalResult.patientName() : "")
                    .patientCPF(finalResult.patientCPF() != null ? finalResult.patientCPF() : "")
                    .patientBirthdate(finalResult.patientBirthdate() != null ? finalResult.patientBirthdate() : "")
                    .build();
            // Chamada externa Blip (fora de transação) que executa de forma assíncrona o CompletableFuture de transbordo
            blipContextService.processAppointmentPush(dispatchIdentity, finalResult.action(), appointmentPayload);
        }

        return finalResult;
    }

    private String formatBirthdate(String birthdate) {
        if (birthdate == null || birthdate.isBlank()) {
            return "";
        }
        String clean = birthdate.trim();
        if (clean.matches("\\d{2}/\\d{2}/\\d{4}")) {
            return clean;
        }
        try {
            java.time.LocalDate date;
            if (clean.contains("-")) {
                if (clean.indexOf('-') == 4) { // AAAA-MM-DD
                    date = java.time.LocalDate.parse(clean);
                } else { // DD-MM-AAAA
                    date = java.time.LocalDate.parse(clean, java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"));
                }
                return date.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            }
        } catch (Exception e) {
            log.warn("[FORMAT] Falha ao formatar data de nascimento: {}", birthdate);
        }
        return clean;
    }
}
