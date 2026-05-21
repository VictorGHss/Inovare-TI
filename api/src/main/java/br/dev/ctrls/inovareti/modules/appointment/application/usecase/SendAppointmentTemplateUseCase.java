package br.dev.ctrls.inovareti.modules.appointment.application.usecase;

import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.dev.ctrls.inovareti.core.exception.NotFoundException;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentCategory;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentConfig;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentConfigRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentDoctorMapping;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentDoctorMappingRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSession;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentSessionRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.utils.BlipErrorMapper;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.BlipProperties;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.PatientExternalPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentExternalPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.FeegowPatient;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.FeegowAppointment;
import br.dev.ctrls.inovareti.modules.appointment.application.dto.AppointmentDispatchContext;
import br.dev.ctrls.inovareti.modules.appointment.application.dto.AppointmentTemplateData;
import br.dev.ctrls.inovareti.modules.appointment.application.service.BlipContextService;
import br.dev.ctrls.inovareti.modules.appointment.application.service.BlipNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class SendAppointmentTemplateUseCase {

    private static final DateTimeFormatter BRAZILIAN_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter SHORT_BRAZILIAN_DATE = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter BRAZILIAN_TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final String DEFAULT_TEMPLATE_VALUE = "Recepção";
    private static final String DEFAULT_PROVIDER_VALUE = "Clínica Inovare";
    private static final String LAST_PENDING_APPOINTMENT_ID_CONTEXT_KEY = "last_pending_appointment_id";

    private final AppointmentConfigRepositoryPort appointmentConfigRepository;
    private final AppointmentSessionRepositoryPort appointmentSessionRepository;
    private final PatientExternalPort patientExternalPort;
    private final AppointmentExternalPort appointmentExternalPort;
    private final BlipNotificationService blipNotificationService;
    private final BlipContextService blipContextService;
    private final BlipProperties blipProperties;
    private final ObjectMapper objectMapper;
    private final AppointmentDoctorMappingRepositoryPort appointmentDoctorMappingRepository;
    private final TransactionTemplate transactionTemplate;

    public boolean execute(AppointmentDispatchContext ctx, AppointmentCategory category) {
        // FASE 1: Leitura do Banco em Transação Microscópica
        // Isolamos a busca da configuração para liberar rapidamente a conexão HikariCP antes do I/O de rede
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
            ctx.appointmentDate());

        AppointmentSession session = transactionTemplate.execute(status ->
            appointmentSessionRepository.findById(ctx.sessionId()).orElse(null)
        );

        // FASE 2: Chamadas HTTP Externas ao Blip (Totalmente fora de transação do banco)
        try {
            String pendingAppointmentId = resolvePendingAppointmentId(ctx.feegowAppointmentId(), ctx.sessionId());
            blipContextService.setUserContextForUser(ctx.phoneNumber(), LAST_PENDING_APPOINTMENT_ID_CONTEXT_KEY, pendingAppointmentId);
            blipNotificationService.sendTemplateMessage(ctx.phoneNumber(), config.getTemplateId(), templateData);

            if (session != null) saveWithRetry(session, null);
            log.info("[MENSAGERIA] Template ativo disparado. Sessão local salva e guardada no banco. Roteamento delegado ao payload do Builder.");
            return true;
        } catch (RestClientResponseException ex) {
            Integer blipCode = extractBlipErrorCode(ex.getResponseBodyAsString());
            BlipErrorMapper mappedError = BlipErrorMapper.fromCode(blipCode);
            String statusDetails = blipCode == null
                    ? mappedError.getDescription()
                    : "Código " + blipCode + ": " + mappedError.getDescription();
            if (session != null) saveWithRetry(session, statusDetails);
            log.error("[DISPATCH CTX] Falha Blip. sessionId={}, blipCode={}, details={}",
                    ctx.sessionId(), blipCode, statusDetails, ex);
            return false;
        } catch (RuntimeException ex) {
            if (session != null) saveWithRetry(session, "Erro desconhecido na API do Blip.");
            log.error("[DISPATCH CTX] Falha inesperada. sessionId={}", ctx.sessionId(), ex);
            return false;
        }
    }

    public boolean execute(AppointmentSession session, AppointmentCategory category) {
        // FASE 1: Leitura do Banco em Transação Microscópica
        AppointmentConfig config = transactionTemplate.execute(status ->
            appointmentConfigRepository.findByCategory(category)
                .orElseThrow(() -> new NotFoundException("Configuração não encontrada para categoria " + category))
        );

        // FASE 2: Chamadas de Rede Externas para a API da Feegow (Fora de Transação)
        FeegowAppointment appointment = appointmentExternalPort.searchAppointments(
            session.getAppointmentAt().toLocalDate(),
            1, // status Marcado
            session.getDoctorProfissionalId()
        ).stream()
         .filter(a -> a.id().equals(session.getFeegowAppointmentId()))
         .findFirst()
         .orElse(new FeegowAppointment(
            session.getFeegowAppointmentId(),
            session.getPatientId(),
            session.getDoctorProfissionalId(),
            null,
            null,
            session.getAppointmentAt(),
            null));

        FeegowPatient patient = patientExternalPort.patientInfo(session.getPatientId());

        String normalizedProfissionalId = session.getDoctorProfissionalId();
        if (normalizedProfissionalId != null) {
            normalizedProfissionalId = normalizedProfissionalId.trim();
        }

        log.info("[DIAGNOSTICO MÉDICO] Buscando nome para ProfissionalID: {} | Nome vindo da Feegow: {}",
            normalizedProfissionalId,
            appointment.doctorName());

        // FASE 1B: Leitura transacional do Mapeamento de Médico (Pessimistic Read exige transação)
        String doctorName = null;
        if (normalizedProfissionalId != null && !normalizedProfissionalId.isBlank() && !"null".equalsIgnoreCase(normalizedProfissionalId.trim())) {
            final String docId = normalizedProfissionalId.trim();
            try {
                doctorName = transactionTemplate.execute(status ->
                    appointmentDoctorMappingRepository.findByProfissionalIdLocked(docId)
                        .map(AppointmentDoctorMapping::getProfissionalNome)
                        .filter(nome -> !nome.isBlank() && !"null".equalsIgnoreCase(nome.trim()))
                        .orElse(null)
                );
            } catch (RuntimeException ex) {
                log.warn("Erro ao buscar mapeamento de médico para profissional_id={} no banco de dados. Continuando com fallback.", docId, ex);
            }
        }
            
        if (doctorName == null || doctorName.isBlank() || "null".equalsIgnoreCase(doctorName.trim())) {
            doctorName = appointment.doctorName();
        }
        
        if (doctorName == null || doctorName.isBlank() || "null".equalsIgnoreCase(doctorName.trim())) {
            log.debug("[FEEGOW] Nome do médico não encontrado para ID: {}", normalizedProfissionalId);
            doctorName = "Clínica Inovare";
        }

        String appointmentDate = appointment.startAt() != null
            ? appointment.startAt().toLocalDate().format(BRAZILIAN_DATE)
            : DEFAULT_TEMPLATE_VALUE;
        String appointmentDateShort = appointment.startAt() != null
            ? appointment.startAt().toLocalDate().format(SHORT_BRAZILIAN_DATE)
            : DEFAULT_TEMPLATE_VALUE;
        String appointmentTime = appointment.startAt() != null
            ? appointment.startAt().toLocalTime().format(BRAZILIAN_TIME)
            : DEFAULT_TEMPLATE_VALUE;

        AppointmentTemplateData templateData = new AppointmentTemplateData(
            fallbackValue(appointment.id()),
            fallbackValue(appointment.patientId()),
            fallbackValue(patient != null ? patient.name() : null),
            fallbackValue(patient != null ? patient.phone() : null),
            fallbackValue(appointment.doctorId()),
            fallbackProviderValue(doctorName),
            DEFAULT_PROVIDER_VALUE,
            fallbackProviderValue(appointment.unitName()),
            appointmentDate,
            appointmentDateShort,
            appointmentTime,
            appointmentDate);

        // FASE 2B: Chamadas de Rede Externas ao Blip (Fora de Transação)
        try {
            String templateId = config.getTemplateId();
            String pendingAppointmentId = resolvePendingAppointmentId(session.getFeegowAppointmentId(), session.getId());
            blipContextService.setUserContextForUser(session.getPhoneNumber(), LAST_PENDING_APPOINTMENT_ID_CONTEXT_KEY, pendingAppointmentId);
            blipNotificationService.sendTemplateMessage(session.getPhoneNumber(), templateId, templateData);
            
            saveWithRetry(session, null);
            log.info("[MENSAGERIA] Template ativo disparado. Sessão local salva e guardada no banco. Roteamento delegado ao payload do Builder.");
            return true;
        } catch (RestClientResponseException ex) {
            Integer blipCode = extractBlipErrorCode(ex.getResponseBodyAsString());
            BlipErrorMapper mappedError = BlipErrorMapper.fromCode(blipCode);
            String statusDetails = blipCode == null
                    ? mappedError.getDescription()
                    : "Código " + blipCode + ": " + mappedError.getDescription();

            saveWithRetry(session, statusDetails);

            log.error("Falha ao enviar template para Blip. sessionId={}, category={}, statusHttp={}, blipCode={}, details={}, responseBody={}",
                    session.getId(),
                    category,
                    ex.getStatusCode().value(),
                    blipCode,
                    statusDetails,
                    ex.getResponseBodyAsString(),
                    ex);
            return false;
        } catch (RuntimeException ex) {
            String statusDetails = "Erro desconhecido na API do Blip.";
            saveWithRetry(session, statusDetails);

            log.error("Falha inesperada ao enviar template para Blip. sessionId={}, category={}, details={}",
                    session.getId(),
                    category,
                    statusDetails,
                    ex);
            return false;
        }
    }

    private void saveWithRetry(AppointmentSession session, String statusDetails) {
        int maxRetries = 3;
        for (int i = 0; i < maxRetries; i++) {
            try {
                if (session.getId() == null) {
                    session.setStatusDetails(statusDetails);
                    return;
                }
                // FASE 3: Persistência em Transação Microscópica
                // O uso do TransactionTemplate aqui garante gravação e liberação imediata da conexão HikariCP.
                transactionTemplate.executeWithoutResult(status -> {
                    AppointmentSession currentSession = appointmentSessionRepository.findByIdLocked(session.getId())
                            .orElse(session);
                    currentSession.setStatusDetails(statusDetails);
                    appointmentSessionRepository.save(currentSession);
                });
                return;
            } catch (org.springframework.orm.ObjectOptimisticLockingFailureException | jakarta.persistence.OptimisticLockException e) {
                if (i == maxRetries - 1) {
                    log.error("Falha de Lock Otimista esgotou as tentativas (Retries: {}). sessionId={}", maxRetries, session.getId());
                    throw e;
                }
                try {
                    java.util.concurrent.TimeUnit.MILLISECONDS.sleep(100);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Thread interrompida durante o retry de salvamento da sessão", ie);
                }
            } catch (RuntimeException ex) {
                log.error("Erro ao gravar alteração da sessão de agendamento no banco de dados. Tentativa {}/{}. Detalhes: {}", i + 1, maxRetries, ex.getMessage(), ex);
                if (i == maxRetries - 1) {
                    throw ex;
                }
            }
        }
    }

    private Integer extractBlipErrorCode(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }

        try {
            JsonNode root = objectMapper.readTree(responseBody);

            Integer topLevelCode = asInteger(root.get("code"));
            if (topLevelCode != null) {
                return topLevelCode;
            }

            JsonNode errorNode = root.get("error");
            Integer nestedErrorCode = asInteger(errorNode != null ? errorNode.get("code") : null);
            if (nestedErrorCode != null) {
                return nestedErrorCode;
            }

            JsonNode resourceNode = root.get("resource");
            return asInteger(resourceNode != null ? resourceNode.get("code") : null);
        } catch (JsonProcessingException ignored) {
            return null;
        }
    }

    private Integer asInteger(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }

        if (node.isInt() || node.isLong()) {
            return node.intValue();
        }

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
            return "Recepção";
        }
        return value.trim();
    }

    private String fallbackProviderValue(String value) {
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value.trim()) || "Informação não disponível".equalsIgnoreCase(value.trim())) {
            return "Clínica Inovare";
        }
        return value.trim();
    }



    private String resolvePendingAppointmentId(String feegowAppointmentId, java.util.UUID sessionId) {
        if (feegowAppointmentId != null && !feegowAppointmentId.isBlank()
                && !"null".equalsIgnoreCase(feegowAppointmentId.trim())) {
            return feegowAppointmentId.trim();
        }
        if (sessionId != null) {
            return sessionId.toString();
        }
        return null;
    }
}
