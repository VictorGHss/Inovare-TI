package br.dev.ctrls.inovareti.modules.appointment.application.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import org.springframework.stereotype.Component;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentExternalPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.FeegowAppointment;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.AppointmentMotorProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Componente responsável por consultar agendamentos marcados na API do Feegow,
 * gerenciando o paralelismo via Virtual Threads quando em modo de testes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeegowAppointmentSearcher {

    private static final int FEEGOW_STATUS_AGENDADO = 1;

    private final AppointmentMotorProperties appointmentMotorProperties;
    private final AppointmentExternalPort appointmentExternalPort;

    /**
     * Consulta e consolida os agendamentos da Feegow para o dia alvo.
     */
    public List<FeegowAppointment> searchAppointments(LocalDate targetDate) {
        if (appointmentMotorProperties.isTestMode()) {
            return searchTestModeAppointments(targetDate);
        }

        log.info("Consultando Feegow para ingestão de agendamentos com status Marcado (ID={})", FEEGOW_STATUS_AGENDADO);
        return appointmentExternalPort.searchAppointments(
            targetDate,
            FEEGOW_STATUS_AGENDADO,
            null
        );
    }

    private List<FeegowAppointment> searchTestModeAppointments(LocalDate targetDate) {
        String testDoctorId = appointmentMotorProperties.getTestDoctorId();
        log.info("[TEST MODE] Buscando agendamentos apenas para os médicos de teste ID: {}", testDoctorId);
        
        List<FeegowAppointment> threadSafeAppointments = Collections.synchronizedList(new ArrayList<>());
        if (testDoctorId != null && !testDoctorId.isBlank()) {
            String[] doctorIds = testDoctorId.split(",");
            
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                List<CompletableFuture<Void>> futures = new ArrayList<>();
                for (String docId : doctorIds) {
                    String trimmedDocId = docId.trim();
                    if (!trimmedDocId.isEmpty()) {
                        futures.add(runAsyncSearch(LocalDate.now(), trimmedDocId, threadSafeAppointments, executor));
                        futures.add(runAsyncSearch(targetDate, trimmedDocId, threadSafeAppointments, executor));
                    }
                }
                CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
            }
        }
        return new ArrayList<>(threadSafeAppointments);
    }

    private CompletableFuture<Void> runAsyncSearch(LocalDate date, String docId, List<FeegowAppointment> list, java.util.concurrent.Executor executor) {
        return CompletableFuture.runAsync(() -> {
            try {
                List<FeegowAppointment> res = appointmentExternalPort.searchAppointments(
                    date,
                    FEEGOW_STATUS_AGENDADO,
                    docId
                );
                if (res != null) {
                    list.addAll(res);
                }
            } catch (Exception e) {
                log.error("[VIRTUAL-THREADS] Erro ao buscar consultas para {}, médico ID: {}", date, docId, e);
            }
        }, executor);
    }
}
