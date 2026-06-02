package br.dev.ctrls.inovareti.modules.appointment.application.service;

import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentDoctorMapping;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSession;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentVariableLog;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentDoctorMappingRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentVariableLogRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.PatientExternalPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.FeegowPatient;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import br.dev.ctrls.inovareti.modules.communication.infrastructure.config.NotificationProperties;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import br.dev.ctrls.inovareti.modules.user.domain.model.User;
import br.dev.ctrls.inovareti.modules.user.domain.port.output.UserRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final DateTimeFormatter BRAZILIAN_TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final String DOCTOR_NAME_KEY = "MEDICO_NOME";
    private static final String DEFAULT_APPOINTMENT_HOUR = "--:--";

    private final RestTemplate restTemplate;
    private final PatientExternalPort patientExternalPort;
    private final AppointmentVariableLogRepositoryPort appointmentVariableLogRepository;
    private final AppointmentDoctorMappingRepositoryPort appointmentDoctorMappingRepository;
    private final UserRepositoryPort userRepository;

    private final NotificationProperties notificationProperties;








    public void notifySecretary(AppointmentSession session, String message) {
        if (session == null || session.getId() == null) {
            log.warn("Notificação de secretária ignorada: sessão nula ou sem id.");
            return;
        }

        String resolvedMessage = StringUtils.hasText(message) ? message.trim() : buildDefaultMessage(session);
        DoctorRouting routing = resolveDoctorRouting(session.getDoctorProfissionalId());

        if (routing.usingSystemUserTarget() && sendItsmBotApi(
                resolvedMessage,
                session,
                routing.notificationTarget(),
                true)) {
            return;
        }

        if (routing.usingSystemUserTarget()) {
            log.warn("Não foi possível notificar usando o usuário do sistema resolvido por itsmUserId. sessionId={}, appointmentId={}, target={}",
                    session.getId(),
                    session.getFeegowAppointmentId(),
                    routing.notificationTarget());
            return;
        }

        boolean discordSent = sendDiscordWebhook(
                resolvedMessage,
                session,
                routing.discordWebhookUrl(),
                routing.usingSpecificDiscordWebhook());
        if (discordSent) {
            return;
        }

        log.warn("Não foi possível notificar secretária. sessionId={}, appointmentId={}",
                session.getId(),
                session.getFeegowAppointmentId());
    }

    private boolean sendDiscordWebhook(
            String message,
            AppointmentSession session,
            String targetWebhookUrl,
            boolean usingSpecificWebhook) {
        if (!StringUtils.hasText(targetWebhookUrl)) {
            return false;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> payload = Map.of("content", message);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    targetWebhookUrl.trim(),
                    new HttpEntity<>(payload, headers),
                    String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Notificação de secretária enviada via Discord webhook (fonte={}). sessionId={}, appointmentId={}",
                        usingSpecificWebhook ? "mapping" : "global",
                        session.getId(),
                        session.getFeegowAppointmentId());
                return true;
            }

            log.warn(
                    "Discord webhook respondeu status não esperado para notificação de secretária. fonte={}, status={}, sessionId={}, appointmentId={}",
                    usingSpecificWebhook ? "mapping" : "global",
                    response.getStatusCode().value(),
                    session.getId(),
                    session.getFeegowAppointmentId());
            return false;
        } catch (RestClientException ex) {
            log.warn(
                    "Falha ao enviar notificação de secretária via Discord webhook. fonte={}, sessionId={}, appointmentId={}, error={}",
                    usingSpecificWebhook ? "mapping" : "global",
                    session.getId(),
                    session.getFeegowAppointmentId(),
                    ex.getMessage());
            return false;
        }
    }

    private boolean sendItsmBotApi(
            String message,
            AppointmentSession session,
            String targetItsmUserId,
            boolean usingSpecificItsmUserId) {
        if (!StringUtils.hasText(notificationProperties.getItsmBotApiUrl())) {
            return false;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String normalizedToken = normalizeToken(notificationProperties.getItsmBotApiToken());
        if (StringUtils.hasText(normalizedToken)) {
            headers.setBearerAuth(normalizedToken);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("message", message);
        payload.put("source", "appointment-confirmation");
        payload.put("appointmentId", session.getFeegowAppointmentId());
        payload.put("sessionId", session.getId().toString());

        if (StringUtils.hasText(targetItsmUserId)) {
            String normalizedItsmUserId = targetItsmUserId.trim();
            payload.put("itsmUserId", normalizedItsmUserId);
            payload.put("itsm_user_id", normalizedItsmUserId);
        }

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    notificationProperties.getItsmBotApiUrl().trim(),
                    new HttpEntity<>(payload, headers),
                    String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
            log.info("Notificação de secretária enviada via API do bot ITSM (target={}). sessionId={}, appointmentId={}",
                usingSpecificItsmUserId ? "mapping-user-id" : "global-default",
                        session.getId(),
                        session.getFeegowAppointmentId());
                return true;
            }

            log.warn(
                "API do bot ITSM respondeu status não esperado para notificação de secretária. target={}, status={}, sessionId={}, appointmentId={}",
                usingSpecificItsmUserId ? "mapping-user-id" : "global-default",
                    response.getStatusCode().value(),
                    session.getId(),
                    session.getFeegowAppointmentId());
            return false;
        } catch (RestClientException ex) {
            log.warn(
                "Falha ao enviar notificação de secretária via API do bot ITSM. target={}, sessionId={}, appointmentId={}, error={}",
                usingSpecificItsmUserId ? "mapping-user-id" : "global-default",
                    session.getId(),
                    session.getFeegowAppointmentId(),
                    ex.getMessage());
            return false;
        }
    }

    private String buildDefaultMessage(AppointmentSession session) {
        String patientName = resolvePatientName(session);
        String doctorName = resolveDoctorName(session);
        String appointmentHour = resolveAppointmentHour(session);

        return String.format(
                "✅ Confirmação: Paciente %s confirmou consulta com %s às %s",
                patientName,
                doctorName,
                appointmentHour);
    }

    private String resolvePatientName(AppointmentSession session) {
        try {
            FeegowPatient patient = patientExternalPort.patientInfo(session.getPatientId());
            String name = patient != null ? patient.name() : null;
            if (name != null && StringUtils.hasText(name)) {
                return name.trim();
            }
        } catch (RuntimeException ex) {
            log.warn("Falha ao resolver nome do paciente para notificação de secretária. sessionId={}, patientId={}, error={}",
                    session.getId(),
                    session.getPatientId(),
                    ex.getMessage());
        }

        if (StringUtils.hasText(session.getPatientId())) {
            return "Paciente " + session.getPatientId().trim();
        }

        return "Paciente";
    }

    private String resolveDoctorName(AppointmentSession session) {
        return appointmentVariableLogRepository
                .findFirstBySessionIdAndDictionaryKeyOrderBySentAtDesc(session.getId(), DOCTOR_NAME_KEY)
                .map(AppointmentVariableLog::getResolvedValue)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .orElseGet(() -> {
                    if (StringUtils.hasText(session.getDoctorProfissionalId())) {
                        return "Profissional " + session.getDoctorProfissionalId().trim();
                    }

                    return "Profissional";
                });
    }

    private String resolveAppointmentHour(AppointmentSession session) {
        if (session.getAppointmentAt() == null) {
            return DEFAULT_APPOINTMENT_HOUR;
        }

        return session.getAppointmentAt().toLocalTime().format(BRAZILIAN_TIME);
    }

    private DoctorRouting resolveDoctorRouting(String doctorProfissionalId) {
        String normalizedDoctorId = normalizeValue(doctorProfissionalId);
        AppointmentDoctorMapping mapping = null;

        if (StringUtils.hasText(normalizedDoctorId)) {
            mapping = appointmentDoctorMappingRepository.findByProfissionalId(normalizedDoctorId).orElse(null);
        }

        String mappingWebhook = normalizeValue(mapping != null ? mapping.getDiscordWebhookUrl() : null);
        String mappingItsmUserId = normalizeValue(mapping != null ? mapping.getItsmUserId() : null);
        String userNotificationTarget = resolveUserNotificationTarget(mappingItsmUserId);

        String fallbackWebhook = normalizeValue(notificationProperties.getDiscordWebhook());
        String resolvedWebhook = StringUtils.hasText(mappingWebhook) ? mappingWebhook : fallbackWebhook;

        return new DoctorRouting(
                userNotificationTarget,
                resolvedWebhook,
                StringUtils.hasText(userNotificationTarget),
            StringUtils.hasText(mappingWebhook));
    }

    private String resolveUserNotificationTarget(String itsmUserId) {
        if (!StringUtils.hasText(itsmUserId)) {
            return null;
        }

        String normalizedItsmUserId = itsmUserId.trim();
        User user = findUserByItsmUserId(normalizedItsmUserId);
        if (user == null) {
            log.warn("itsmUserId do mapeamento sem usuário correspondente no sistema. itsmUserId={}", normalizedItsmUserId);
            return null;
        }

        String notificationTarget = firstNonBlank(user.getDiscordUserId(), user.getEmail());
        if (!StringUtils.hasText(notificationTarget)) {
            log.warn("Usuário encontrado sem discordUserId/email para notificação. userId={}", user.getId());
            return null;
        }

        return notificationTarget.trim();
    }

    private User findUserByItsmUserId(String itsmUserId) {
        if (!StringUtils.hasText(itsmUserId)) {
            return null;
        }

        try {
            UUID userId = UUID.fromString(itsmUserId.trim());
            User userById = userRepository.findById(userId).orElse(null);
            if (userById != null) {
                return userById;
            }
        } catch (IllegalArgumentException ignored) {
            // Não é UUID: tenta resolver por outros identificadores.
        }

        User userByDiscordId = userRepository.findByDiscordUserId(itsmUserId.trim()).orElse(null);
        if (userByDiscordId != null) {
            return userByDiscordId;
        }

        return userRepository.findByEmail(itsmUserId.trim()).orElse(null);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }

        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }

        return null;
    }

    private String normalizeToken(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }

        String normalized = token.trim();
        if (normalized.regionMatches(true, 0, "Bearer ", 0, 7)) {
            normalized = normalized.substring(7).trim();
        }

        return normalized;
    }

    private String normalizeValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        return value.trim();
    }

    private record DoctorRouting(
            String notificationTarget,
            String discordWebhookUrl,
            boolean usingSystemUserTarget,
            boolean usingSpecificDiscordWebhook) {
    }
}