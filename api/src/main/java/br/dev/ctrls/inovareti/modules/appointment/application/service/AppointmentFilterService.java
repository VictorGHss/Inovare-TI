package br.dev.ctrls.inovareti.modules.appointment.application.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentDoctorMapping;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSession;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSessionStatus;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentSessionRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.FeegowAppointment;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.AppointmentMotorProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço responsável por aplicar as regras de filtragem e desduplicação dos agendamentos do Feegow.
 * Garante que apenas agendamentos elegíveis e ativos de médicos válidos sejam processados.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentFilterService {

    private final AppointmentMotorProperties appointmentMotorProperties;
    private final AppointmentSessionRepositoryPort appointmentSessionRepository;
    private final Optional<AppointmentSendIdempotencyService> appointmentSendIdempotencyService;
    private final Optional<NoopAppointmentSendIdempotencyService> noopAppointmentSendIdempotencyService;

    /**
     * Filtra a lista inicial de agendamentos vindos do Feegow.
     * Remove registros antigos, inativos ou de médicos não-assinantes.
     */
    public List<FeegowAppointment> filterInitialAppointments(
            List<FeegowAppointment> appointments,
            Map<String, AppointmentDoctorMapping> doctorMappingCache) {
        
        int total = appointments.size();
        
        // Remove agendamentos com data anterior a hoje
        List<FeegowAppointment> activeAppointments = appointments.stream()
                .filter(a -> a.startAt() != null && !a.startAt().toLocalDate().isBefore(LocalDate.now()))
                .filter(a -> !"12".equals(a.statusId())) // Remove status de cancelado
                .collect(Collectors.toList());
                
        int totalBeforeDoctorFilter = activeAppointments.size();
        
        // Remove agendamentos de médicos não-assinantes ou inativos
        List<FeegowAppointment> filtered = activeAppointments.stream()
                .filter(appointment -> {
                    var mapping = doctorMappingCache.get(appointment.doctorId());
                    return mapping != null && !"inactive".equalsIgnoreCase(mapping.getBlipQueueId()) && !mapping.isIgnoreAutoSchedule();
                })
                .collect(Collectors.toList());
                
        int removedByDoctorFilter = totalBeforeDoctorFilter - filtered.size();
        if (removedByDoctorFilter > 0) {
            log.info("[INGESTAO-AUDITORIA] Ignorados {} agendamentos pertencentes a médicos inativos ou não-assinantes.", removedByDoctorFilter);
        }
        
        return filtered;
    }

    /**
     * Filtra agendamentos elegíveis em um grupo, desduplicando e aplicando as verificações de idempotência.
     */
    public List<FeegowAppointment> filterEligibleAppointments(
            List<FeegowAppointment> group,
            Map<String, AppointmentSession> sessionCache,
            Map<String, AppointmentDoctorMapping> doctorMappingCache) {
        
        List<FeegowAppointment> eligible = new ArrayList<>();
        for (FeegowAppointment appointment : group) {
            String feegowAppointmentId = normalizeFeegowAppointmentId(appointment.id());
            if (feegowAppointmentId.isBlank()) continue;

            var mapping = doctorMappingCache.get(appointment.doctorId());
            if (mapping == null || "inactive".equalsIgnoreCase(mapping.getBlipQueueId()) || mapping.isIgnoreAutoSchedule()) {
                continue;
            }

            AppointmentSession existing = sessionCache.get(feegowAppointmentId);
            Optional<AppointmentSession> existingSessionOpt = Optional.ofNullable(existing);

            String confirmedStatusId = appointmentMotorProperties.getFeegowConfirmedStatusId();
            if (confirmedStatusId == null || confirmedStatusId.isBlank()) {
                confirmedStatusId = "7";
            }
            boolean isConfirmedOnFeegow = confirmedStatusId.trim().equalsIgnoreCase(appointment.statusId());
            boolean isConfirmedLocally = existingSessionOpt.map(s -> s.getStatus() == AppointmentSessionStatus.CONFIRMED).orElse(false);

            if (isConfirmedLocally || isConfirmedOnFeegow) {
                continue;
            }

            if (existingSessionOpt.isPresent()) {
                AppointmentSessionStatus status = existingSessionOpt.get().getStatus();
                if (status == AppointmentSessionStatus.PENDING || status == AppointmentSessionStatus.NUDGE_1_SENT ||
                    status == AppointmentSessionStatus.NUDGE_FINAL_SENT || status == AppointmentSessionStatus.CONFIRMED) {
                    continue;
                }
            }

            boolean canSend = appointmentSendIdempotencyService.map(s -> s.registerIfFirstSend(feegowAppointmentId))
                    .orElseGet(() -> noopAppointmentSendIdempotencyService.map(s -> s.registerIfFirstSend(feegowAppointmentId)).orElse(true));

            if (canSend) {
                eligible.add(appointment);
            }
        }
        return eligible;
    }

    public boolean isDoctorAllowedInTestMode(String doctorId) {
        if (!appointmentMotorProperties.isTestMode()) {
            return true;
        }
        String testDoctorId = appointmentMotorProperties.getTestDoctorId();
        List<String> allowedIds = java.util.Arrays.stream(testDoctorId.split(","))
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .toList();
        
        String docId = doctorId != null ? doctorId.trim() : "";
        return allowedIds.contains(docId);
    }

    public String normalizeFeegowAppointmentId(String feegowAppointmentId) {
        if (feegowAppointmentId == null) {
            return "";
        }
        String normalized = feegowAppointmentId.trim();
        if (normalized.matches("^\\d+\\.0+$")) {
            return normalized.substring(0, normalized.indexOf('.'));
        }
        return normalized;
    }

    public String normalizePhoneNumberForBlip(String originalPhone) {
        if (originalPhone == null || originalPhone.isBlank()) {
            return "";
        }
        String purified = purificarTelefoneParaGrupo(originalPhone);
        if (purified.isEmpty()) {
            return "";
        }
        return "55" + purified;
    }

    public String purificarTelefoneParaGrupo(String originalPhone) {
        if (originalPhone == null || originalPhone.isBlank()) {
            return "";
        }
        String digitsOnly = originalPhone.replaceAll("\\D", "");
        if (digitsOnly.startsWith("55")) {
            digitsOnly = digitsOnly.substring(2);
        }
        if (digitsOnly.startsWith("0")) {
            digitsOnly = digitsOnly.substring(1);
        }
        return digitsOnly;
    }
}
