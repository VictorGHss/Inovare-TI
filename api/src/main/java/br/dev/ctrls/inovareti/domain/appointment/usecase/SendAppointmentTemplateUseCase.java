package br.dev.ctrls.inovareti.domain.appointment.usecase;

import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.dev.ctrls.inovareti.core.exception.NotFoundException;
import br.dev.ctrls.inovareti.domain.appointment.AppointmentCategory;
import br.dev.ctrls.inovareti.domain.appointment.AppointmentConfig;
import br.dev.ctrls.inovareti.domain.appointment.AppointmentConfigRepository;
import br.dev.ctrls.inovareti.domain.appointment.AppointmentDoctorMapping;
import br.dev.ctrls.inovareti.domain.appointment.AppointmentDoctorMappingRepository;
import br.dev.ctrls.inovareti.domain.appointment.AppointmentSession;
import br.dev.ctrls.inovareti.domain.appointment.AppointmentSessionRepository;
import br.dev.ctrls.inovareti.domain.appointment.BlipErrorMapper;
import br.dev.ctrls.inovareti.domain.appointment.FeegowClient;
import br.dev.ctrls.inovareti.domain.appointment.AppointmentMotorProperties;
import br.dev.ctrls.inovareti.domain.appointment.service.BlipNotificationService;
import br.dev.ctrls.inovareti.domain.appointment.service.BlipContextService;
import br.dev.ctrls.inovareti.domain.appointment.dto.AppointmentTemplateData;
import br.dev.ctrls.inovareti.domain.appointment.dto.AppointmentDispatchContext;
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

    private final AppointmentConfigRepository appointmentConfigRepository;
    private final AppointmentSessionRepository appointmentSessionRepository;
    private final FeegowClient feegowClient;
    private final BlipNotificationService blipNotificationService;
    private final BlipContextService blipContextService;
    private final AppointmentMotorProperties appointmentMotorProperties;
    private final ObjectMapper objectMapper;
    private final AppointmentDoctorMappingRepository appointmentDoctorMappingRepository;

    @Transactional
    public boolean execute(AppointmentDispatchContext ctx, AppointmentCategory category) {
        AppointmentConfig config = appointmentConfigRepository.findByCategory(category)
            .orElseThrow(() -> new NotFoundException("Configuração não encontrada para categoria " + category));

        // Dados já resolvidos e imutáveis — sem re-busca no banco ou Feegow
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

        // Busca a sessão para poder salvar o resultado
        AppointmentSession session = appointmentSessionRepository.findById(ctx.sessionId()).orElse(null);

        try {
            blipNotificationService.sendTemplateMessage(ctx.phoneNumber(), config.getTemplateId(), templateData);

            // Pré-armar o teletransporte: trava o usuário no bloco de aterrissagem do fluxov1
            // para que o clique no botão não caía no 'Início' do bot.
            armLandingState(ctx.phoneNumber());

            if (session != null) saveWithRetry(session, null);
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

    @Transactional
    public boolean execute(AppointmentSession session, AppointmentCategory category) {

        AppointmentConfig config = appointmentConfigRepository.findByCategory(category)
            .orElseThrow(() -> new NotFoundException("Configuração não encontrada para categoria " + category));

        // Busca o agendamento real
        FeegowClient.FeegowAppointment appointment = feegowClient.searchAppointments(
            session.getAppointmentAt().toLocalDate(),
            1, // status Marcado
            session.getDoctorProfissionalId()
        ).stream()
         .filter(a -> a.id().equals(session.getFeegowAppointmentId()))
         .findFirst()
         .orElse(new FeegowClient.FeegowAppointment(
            session.getFeegowAppointmentId(),
            session.getPatientId(),
            session.getDoctorProfissionalId(),
            null,
            null,
            session.getAppointmentAt(),
            null));

        FeegowClient.FeegowPatient patient = feegowClient.patientInfo(session.getPatientId());

        String normalizedProfissionalId = session.getDoctorProfissionalId();
        if (normalizedProfissionalId != null) {
            normalizedProfissionalId = normalizedProfissionalId.trim();
        }

        log.info("[DIAGNOSTICO MÉDICO] Buscando nome para ProfissionalID: {} | Nome vindo da Feegow: {}",
            normalizedProfissionalId,
            appointment.doctorName());

        // Inversão de prioridade: busca no banco primeiro
        String doctorName = null;
        if (normalizedProfissionalId != null && !normalizedProfissionalId.isBlank() && !"null".equalsIgnoreCase(normalizedProfissionalId.trim())) {
            doctorName = appointmentDoctorMappingRepository.findByProfissionalIdLocked(normalizedProfissionalId.trim())
                .map(AppointmentDoctorMapping::getProfissionalNome)
                .filter(nome -> !nome.isBlank() && !"null".equalsIgnoreCase(nome.trim()))
                .orElse(null);
        }
            
        if (doctorName == null || doctorName.isBlank() || "null".equalsIgnoreCase(doctorName.trim())) {
            doctorName = appointment.doctorName(); // Fallback para a API da Feegow
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

        try {
            String templateId = config.getTemplateId();
            blipNotificationService.sendTemplateMessage(session.getPhoneNumber(), templateId, templateData);
            saveWithRetry(session, null);
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
                // Se a sessão ainda não foi salva no banco (ID null), apenas atualiza o objeto atual.
                if (session.getId() == null) {
                    session.setStatusDetails(statusDetails);
                    return;
                }
                AppointmentSession currentSession = appointmentSessionRepository.findByIdLocked(session.getId()).orElse(session);
                currentSession.setStatusDetails(statusDetails);
                appointmentSessionRepository.save(currentSession);
                return;
            } catch (org.springframework.orm.ObjectOptimisticLockingFailureException | jakarta.persistence.OptimisticLockException e) {
                if (i == maxRetries - 1) {
                    log.error("Falha de Lock Otimista esgotou as tentativas (Retries: {}). sessionId={}", maxRetries, session.getId());
                    throw e;
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Thread interrompida durante o retry de salvamento da sessão", ie);
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

    /**
     * Pré-arma o estado do usuário no bloco de aterrissagem do fluxov1.
     * Isso garante que o clique no botão do template caia no bloco correto
     * em vez de reiniciar o bot do início.
     * Falhas são apenas logadas — não interrompem o envio do template.
     */
    private void armLandingState(String phoneNumber) {
        try {
            String flowId  = appointmentMotorProperties.getState().getBlipFluxov1FlowId();
            String blockId = appointmentMotorProperties.getState().getBlipLandingBlockId();

            if (flowId == null || flowId.isBlank() || blockId == null || blockId.isBlank()) {
                log.warn("[ARM STATE] blip-fluxov1-flow-id ou blip-landing-block-id não configurados. Teletransporte preventivo ignorado.");
                return;
            }

            blipContextService.setUserState(phoneNumber, flowId, blockId);
            log.info("[ARM STATE] Usuário travado no bloco de aterrissagem. phone={}, flowId={}, blockId={}", phoneNumber, flowId, blockId);
        } catch (Exception ex) {
            log.warn("[ARM STATE] Falha ao pré-armar estado do usuário. phone={}. O template foi enviado normalmente.", phoneNumber, ex);
        }
    }
}
