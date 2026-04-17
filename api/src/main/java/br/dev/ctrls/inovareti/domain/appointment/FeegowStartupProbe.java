package br.dev.ctrls.inovareti.domain.appointment;

import java.time.LocalDate;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        value = { "app.appointment.motor.enabled", "app.appointment.motor.feegow-startup-probe-enabled" },
        havingValue = "true")
public class FeegowStartupProbe {

    private static final int FEEGOW_STATUS_AGENDADO = 1;
    private static final int MAX_LOGGED_APPOINTMENT_IDS = 5;
    private static final String DEFAULT_TEST_DOCTOR_ID = "70";

    private final FeegowClient feegowClient;
    private final AppointmentMotorProperties appointmentMotorProperties;

    @EventListener(ApplicationReadyEvent.class)
    public void runStartupProbe() {
        LocalDate targetDate = LocalDate.now().plusDays(1);
        String testDoctorId = resolveTestDoctorId();

        log.info(
                "[FEEGOW STARTUP PROBE] Iniciando busca de diagnóstico. date={}, status={}, testMode={}, testDoctorId={}",
                targetDate,
                FEEGOW_STATUS_AGENDADO,
                appointmentMotorProperties.isTestMode(),
                testDoctorId);

        try {
            List<FeegowClient.FeegowAppointment> appointments = feegowClient.searchAppointments(targetDate,
                    FEEGOW_STATUS_AGENDADO);
            int totalReceived = appointments.size();

            if (appointmentMotorProperties.isTestMode()) {
                appointments = appointments.stream()
                        .filter(appointment -> testDoctorId.equals(appointment.doctorId()))
                        .toList();
            }

            List<String> sampleAppointmentIds = appointments.stream()
                    .map(FeegowClient.FeegowAppointment::id)
                    .limit(MAX_LOGGED_APPOINTMENT_IDS)
                    .toList();

            log.info(
                    "[FEEGOW STARTUP PROBE] Resposta válida recebida da Feegow. totalRecebido={}, totalAposFiltro={}, sampleAppointmentIds={}",
                    totalReceived,
                    appointments.size(),
                    sampleAppointmentIds);
        } catch (RestClientResponseException ex) {
            log.error(
                    "[FEEGOW STARTUP PROBE] Erro HTTP na Feegow (possível Auth). status={}, responseBody={}",
                    ex.getStatusCode().value(),
                    abbreviate(ex.getResponseBodyAsString(), 500),
                    ex);
        } catch (ResourceAccessException ex) {
            log.error(
                    "[FEEGOW STARTUP PROBE] Falha de conectividade/TLS com a Feegow (possível Handshake). message={}",
                    ex.getMessage(),
                    ex);
        } catch (RestClientException ex) {
            log.error(
                    "[FEEGOW STARTUP PROBE] Falha REST ao consultar Feegow (possível Auth/JSON). message={}",
                    ex.getMessage(),
                    ex);
        } catch (RuntimeException ex) {
            log.error("[FEEGOW STARTUP PROBE] Erro inesperado durante o diagnóstico da Feegow.", ex);
        }
    }

    private String resolveTestDoctorId() {
        String configuredTestDoctorId = appointmentMotorProperties.getTestDoctorId();
        if (configuredTestDoctorId == null || configuredTestDoctorId.isBlank()) {
            return DEFAULT_TEST_DOCTOR_ID;
        }
        return configuredTestDoctorId.trim();
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "[empty]";
        }

        String normalized = value.trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }

        return normalized.substring(0, maxLength) + "...";
    }
}
