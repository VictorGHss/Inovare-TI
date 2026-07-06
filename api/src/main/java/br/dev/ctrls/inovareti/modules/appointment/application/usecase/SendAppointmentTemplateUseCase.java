package br.dev.ctrls.inovareti.modules.appointment.application.usecase;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.dev.ctrls.inovareti.core.shared.domain.model.exception.NotFoundException;
import br.dev.ctrls.inovareti.modules.appointment.application.dto.AppointmentDispatchContext;
import br.dev.ctrls.inovareti.modules.appointment.application.dto.AppointmentTemplateData;
import br.dev.ctrls.inovareti.modules.appointment.application.service.AppointmentTemplateDataBuilder;
import br.dev.ctrls.inovareti.modules.appointment.application.service.BlipContextService;
import br.dev.ctrls.inovareti.modules.appointment.application.service.BlipNotificationService;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentCategory;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentConfig;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSession;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSessionStatus;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentConfigRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentSessionRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.BlipUserIdentityReconciliationRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.AppointmentMotorProperties;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.BlipProperties;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.utils.BlipErrorMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Caso de Uso encarregado do envio de templates de mensagens do Blip
 * (tanto consultas individuais quanto fluxos de nudges parametrizados).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SendAppointmentTemplateUseCase {

    private static final String DEFAULT_TEMPLATE_VALUE = "Recepção";
    private static final String DEFAULT_PROVIDER_VALUE = "Clínica Inovare";
    private static final String LAST_PENDING_APPOINTMENT_ID_CONTEXT_KEY = "last_pending_appointment_id";

    private final AppointmentConfigRepositoryPort appointmentConfigRepository;
    private final AppointmentSessionRepositoryPort appointmentSessionRepository;
    private final BlipNotificationService blipNotificationService;
    private final BlipContextService blipContextService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final AppointmentTemplateDataBuilder appointmentTemplateDataBuilder;
    private final AppointmentMotorProperties appointmentMotorProperties;
    private final BlipProperties blipProperties;
    private final BlipUserIdentityReconciliationRepositoryPort blipUserIdentityReconciliationRepository;

    /**
     * Executa o envio a partir de um contexto de despacho resolvido previamente.
     */
    public boolean execute(AppointmentDispatchContext ctx, AppointmentCategory category) {
        AppointmentConfig config = transactionTemplate.execute(status ->
            appointmentConfigRepository.findByCategory(category)
                .orElseThrow(() -> new NotFoundException("Configuração não encontrada para categoria " + category))
        );

        log.info("[DISPATCH CTX] Enviando template com dados pré-resolvidos: paciente='{}', médico='{}', data='{}', hora='{}'",
            ctx.patientName(), ctx.doctorName(), ctx.appointmentDateShort(), ctx.appointmentTime());

        AppointmentTemplateData templateData = new AppointmentTemplateData(
            ctx.feegowAppointmentId(),
            ctx.patientId(),
            fallbackValue(ctx.patientName()),
            fallbackValue(ctx.patientPhone()),
            ctx.doctorProfissionalId(),
            fallbackProviderValue(ctx.doctorName()),
            DEFAULT_PROVIDER_VALUE,
            DEFAULT_PROVIDER_VALUE,
            ctx.appointmentDate(),
            ctx.appointmentDateShort(),
            ctx.appointmentTime(),
            ctx.appointmentDate()
        );

        AppointmentSession session = transactionTemplate.execute(status ->
            appointmentSessionRepository.findById(ctx.sessionId()).orElse(null)
        );

        try {
            String templateId = resolveTemplateId(config.getTemplateId(), category);
            String pendingAppointmentId = resolvePendingAppointmentId(ctx.feegowAppointmentId(), ctx.sessionId());
            blipContextService.setUserContextForUser(ctx.phoneNumber(), LAST_PENDING_APPOINTMENT_ID_CONTEXT_KEY, pendingAppointmentId);

            // Limpa contexto de grupo e redireciona para Preparar_Atendimento de forma assíncrona
            cleanGroupContextAndRedirectToPrepararAtendimentoAsync(ctx.phoneNumber());

            if (session != null) {
                switch (category) {
                    case CONFIRMATION -> session.setStatus(AppointmentSessionStatus.PENDING);
                    case NUDGE_1 -> session.setStatus(AppointmentSessionStatus.NUDGE_1_SENT);
                    case NUDGE_FINAL -> session.setStatus(AppointmentSessionStatus.NUDGE_FINAL_SENT);
                    case GROUP_NOTIFICATION -> session.setStatus(AppointmentSessionStatus.PENDING);
                    case GROUP_NUDGE_1 -> session.setStatus(AppointmentSessionStatus.NUDGE_1_SENT);
                    case GROUP_NUDGE_FINAL -> session.setStatus(AppointmentSessionStatus.NUDGE_FINAL_SENT);
                }
                session.setLastInteractionAt(LocalDateTime.now());
                session.setLastNotificationSentAt(LocalDateTime.now());
                saveWithRetry(session, null);
            }

            blipNotificationService.sendTemplateMessage(ctx.phoneNumber(), templateId, templateData);

            log.info("[MENSAGERIA] Template ativo disparado. Sessão local salva no banco.");
            return true;
        } catch (RestClientResponseException ex) {
            handleRestClientException(session, ex);
            return false;
        } catch (RuntimeException ex) {
            handleGenericException(session, ex);
            return false;
        }
    }

    /**
     * Executa o envio com base em uma sessão de agendamento, consultando dados externos síncronos.
     */
    public boolean execute(AppointmentSession session, AppointmentCategory category) {
        AppointmentConfig config = transactionTemplate.execute(status ->
            appointmentConfigRepository.findByCategory(category)
                .orElseThrow(() -> new NotFoundException("Configuração não encontrada para categoria " + category))
        );

        AppointmentTemplateData templateData = appointmentTemplateDataBuilder.build(session);

        try {
            String templateId = resolveTemplateId(config.getTemplateId(), category);
            String pendingAppointmentId = resolvePendingAppointmentId(session.getFeegowAppointmentId(), session.getId());
            blipContextService.setUserContextForUser(session.getPhoneNumber(), LAST_PENDING_APPOINTMENT_ID_CONTEXT_KEY, pendingAppointmentId);

            // Limpa contexto de grupo e redireciona para Preparar_Atendimento de forma assíncrona
            cleanGroupContextAndRedirectToPrepararAtendimentoAsync(session.getPhoneNumber());

            switch (category) {
                case CONFIRMATION -> session.setStatus(AppointmentSessionStatus.PENDING);
                case NUDGE_1 -> session.setStatus(AppointmentSessionStatus.NUDGE_1_SENT);
                case NUDGE_FINAL -> session.setStatus(AppointmentSessionStatus.NUDGE_FINAL_SENT);
                case GROUP_NOTIFICATION -> session.setStatus(AppointmentSessionStatus.PENDING);
                case GROUP_NUDGE_1 -> session.setStatus(AppointmentSessionStatus.NUDGE_1_SENT);
                case GROUP_NUDGE_FINAL -> session.setStatus(AppointmentSessionStatus.NUDGE_FINAL_SENT);
            }
            session.setLastInteractionAt(LocalDateTime.now());
            session.setLastNotificationSentAt(LocalDateTime.now());
            saveWithRetry(session, null);

            blipNotificationService.sendTemplateMessage(session.getPhoneNumber(), templateId, templateData);
            
            log.info("[MENSAGERIA] Template ativo disparado. Sessão local salva no banco.");
            return true;
        } catch (RestClientResponseException ex) {
            handleRestClientException(session, ex);
            return false;
        } catch (RuntimeException ex) {
            handleGenericException(session, ex);
            return false;
        }
    }

    /**
     * Executa o envio de um template simples do Blip associado à sessão.
     */
    public boolean executeSimpleTemplate(AppointmentSession session, String templateName) {
        AppointmentTemplateData templateData = appointmentTemplateDataBuilder.build(session);

        try {
            if ("aviso_final_cancelamento".equalsIgnoreCase(templateName)) {
                session.setStatus(AppointmentSessionStatus.CANCELED_NO_RESPONSE);
                session.setLastInteractionAt(LocalDateTime.now());
                session.setClosedAt(LocalDateTime.now());
            }
            saveWithRetry(session, null);

            blipNotificationService.sendSimpleTemplateMessage(session.getPhoneNumber(), templateName, templateData);
            log.info("[MENSAGERIA] Template simples '{}' disparado.", templateName);
            return true;
        } catch (RestClientResponseException ex) {
            handleRestClientException(session, ex);
            return false;
        } catch (RuntimeException ex) {
            handleGenericException(session, ex);
            return false;
        }
    }

    private String resolveTemplateId(String rawTemplateId, AppointmentCategory category) {
        String templateId = rawTemplateId;
        if (category == AppointmentCategory.NUDGE_1 || category == AppointmentCategory.NUDGE_FINAL) {
            if (templateId == null || templateId.isBlank() 
                    || "confirmacao_consulta_v6_itsm".equalsIgnoreCase(templateId.trim())
                    || "aviso_agendamento_grupo".equalsIgnoreCase(templateId.trim())
                    || "aviso_confirmacao_pendente".equalsIgnoreCase(templateId.trim())) {
                templateId = appointmentMotorProperties.getBlipTemplateNudgePending();
            }
        }
        return templateId;
    }

    private void handleRestClientException(AppointmentSession session, RestClientResponseException ex) {
        Integer blipCode = extractBlipErrorCode(ex.getResponseBodyAsString());
        BlipErrorMapper mappedError = BlipErrorMapper.fromCode(blipCode);
        String statusDetails = blipCode == null
                ? mappedError.getDescription()
                : "Código " + blipCode + ": " + mappedError.getDescription();
        if (session != null) {
            saveWithRetry(session, statusDetails);
        }
        log.error("Falha ao enviar template para Blip. sessionId={}, blipCode={}, details={}",
                session != null ? session.getId() : null, blipCode, statusDetails, ex);
    }

    private void handleGenericException(AppointmentSession session, RuntimeException ex) {
        String statusDetails = "Erro desconhecido na API do Blip.";
        if (session != null) {
            saveWithRetry(session, statusDetails);
        }
        log.error("Falha inesperada ao enviar template para Blip. sessionId={}, details={}",
                session != null ? session.getId() : null, statusDetails, ex);
    }

    private void saveWithRetry(AppointmentSession session, String statusDetails) {
        int maxRetries = 3;
        for (int i = 0; i < maxRetries; i++) {
            try {
                if (session.getId() == null) {
                    session.setStatusDetails(statusDetails);
                    return;
                }
                transactionTemplate.executeWithoutResult(status -> {
                    AppointmentSession currentSession = appointmentSessionRepository.findByIdLocked(session.getId())
                            .orElse(session);
                    currentSession.setStatus(session.getStatus());
                    currentSession.setLastInteractionAt(session.getLastInteractionAt());
                    currentSession.setLastNotificationSentAt(session.getLastNotificationSentAt());
                    currentSession.setClosedAt(session.getClosedAt());
                    currentSession.setStatusDetails(statusDetails);
                    appointmentSessionRepository.save(currentSession);
                });
                return;
            } catch (org.springframework.orm.ObjectOptimisticLockingFailureException | jakarta.persistence.OptimisticLockException e) {
                if (i == maxRetries - 1) throw e;
                try {
                    java.util.concurrent.TimeUnit.MILLISECONDS.sleep(100);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(ie);
                }
            } catch (RuntimeException ex) {
                if (i == maxRetries - 1) throw ex;
            }
        }
    }

    private Integer extractBlipErrorCode(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) return null;
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            Integer topLevelCode = asInteger(root.get("code"));
            if (topLevelCode != null) return topLevelCode;

            JsonNode errorNode = root.get("error");
            Integer nestedErrorCode = asInteger(errorNode != null ? errorNode.get("code") : null);
            if (nestedErrorCode != null) return nestedErrorCode;

            JsonNode resourceNode = root.get("resource");
            return asInteger(resourceNode != null ? resourceNode.get("code") : null);
        } catch (JsonProcessingException ignored) {
            return null;
        }
    }

    private Integer asInteger(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isInt() || node.isLong()) return node.intValue();
        if (node.isTextual()) {
            try {
                return Integer.valueOf(node.asText().trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String fallbackValue(String value) {
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value.trim()) || "Informação não disponível".equalsIgnoreCase(value.trim())) {
            return DEFAULT_TEMPLATE_VALUE;
        }
        return value.trim();
    }

    private String fallbackProviderValue(String value) {
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value.trim()) || "Informação não disponível".equalsIgnoreCase(value.trim())) {
            return DEFAULT_PROVIDER_VALUE;
        }
        return value.trim();
    }

    private String resolvePendingAppointmentId(String feegowAppointmentId, java.util.UUID sessionId) {
        if (feegowAppointmentId != null && !feegowAppointmentId.isBlank()
                && !"null".equalsIgnoreCase(feegowAppointmentId.trim())) {
            return feegowAppointmentId.trim();
        }
        return sessionId != null ? sessionId.toString() : null;
    }

    private void cleanGroupContextAndRedirectToPrepararAtendimentoAsync(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) return;
        final String soloPhone = phoneNumber.trim();
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                // 1. Limpa variáveis de contexto
                blipContextService.setUserContextForUser(soloPhone, "isConfirmingAgenda", "false");
                blipContextService.deleteUserContext(soloPhone, "groupId");
                log.info("[SOLO-CTX] Contexto de grupo limpo para template individual. phone={}", soloPhone);

                // 2. Resolve target de estado (Preparar_Atendimento)
                String prepararAtendimentoBlockId = blipProperties.getBlocks().getPrepararAtendimento();
                if (prepararAtendimentoBlockId == null || prepararAtendimentoBlockId.isBlank()) {
                    prepararAtendimentoBlockId = "a0776d9c-6486-42f3-8a4f-2706f0185908";
                }

                String cleanPhone = soloPhone;
                String phoneDigits = cleanPhone;
                if (phoneDigits.contains("@")) {
                    phoneDigits = phoneDigits.substring(0, phoneDigits.indexOf('@'));
                }
                phoneDigits = phoneDigits.replaceAll("\\D", "");
                if (!phoneDigits.startsWith("55") && !phoneDigits.isEmpty()) {
                    phoneDigits = "55" + phoneDigits;
                }

                // Busca se há reconciliação de identidade
                List<br.dev.ctrls.inovareti.modules.appointment.domain.model.BlipUserIdentityReconciliation> reconciliations = new ArrayList<>();
                try {
                    reconciliations.addAll(blipUserIdentityReconciliationRepository.findByPhoneNumber(phoneDigits));
                    String altPhone = phoneDigits.startsWith("55") ? phoneDigits.substring(2) : "55" + phoneDigits;
                    reconciliations.addAll(blipUserIdentityReconciliationRepository.findByPhoneNumber(altPhone));
                } catch (Exception ex) {
                    log.warn("[SOLO-CTX] Falha ao consultar reconciliação de identidades: {}", ex.getMessage());
                }

                List<String> tunnelIdentities = new ArrayList<>();
                String subbotId = blipProperties.getSubbotId();
                String subbotLocalPart = null;
                if (subbotId != null && !subbotId.isBlank()) {
                    subbotLocalPart = subbotId.trim();
                    if (subbotLocalPart.contains("@")) {
                        subbotLocalPart = subbotLocalPart.substring(0, subbotLocalPart.indexOf('@'));
                    }
                }

                if (subbotLocalPart != null) {
                    String deterministicTunnel = phoneDigits + "." + subbotLocalPart + "@tunnel.msging.net";
                    tunnelIdentities.add(deterministicTunnel);
                }

                if (reconciliations != null && !reconciliations.isEmpty()) {
                    for (var rec : reconciliations) {
                        if (rec.getBlipGuid() != null && !rec.getBlipGuid().isBlank()) {
                            String realTunnel = rec.getBlipGuid().trim() + "@tunnel.msging.net";
                            if (!tunnelIdentities.contains(realTunnel)) {
                                tunnelIdentities.add(realTunnel);
                            }
                        }
                    }
                }

                // Redireciona Master-State
                try {
                    blipContextService.setMasterState(cleanPhone, subbotId, prepararAtendimentoBlockId);
                    log.info("[SOLO-CTX] Master-State do Roteador atualizado ao enviar template solo para {}", cleanPhone);
                } catch (Exception e) {
                    log.error("[SOLO-CTX] Erro ao atualizar Master-State no Roteador para {}", cleanPhone, e);
                }

                for (String tunnel : tunnelIdentities) {
                    try {
                        blipContextService.setBuilderMasterState(tunnel, prepararAtendimentoBlockId);
                        log.info("[SOLO-CTX] Builder Master-State atualizado ao enviar template solo para tunnel {}", tunnel);
                    } catch (Exception e) {
                        log.error("[SOLO-CTX] Erro ao atualizar Builder Master-State no Subbot para tunnel {}", tunnel, e);
                    }
                }
            } catch (Exception e) {
                log.warn("[SOLO-CTX] Falha no fluxo de limpeza e redirecionamento solo para {}: {}", soloPhone, e.getMessage());
            }
        });
    }
}
