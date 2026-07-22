package br.dev.ctrls.inovareti.modules.appointment.application.service;

import io.micrometer.observation.annotation.Observed;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Value;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentExternalPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.FeegowAppointment;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.AppointmentMotorProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Componente responsável por consultar agendamentos marcados na API do Feegow,
 * gerenciando o paralelismo controlado via Virtual Threads e controle de taxa por Semaphore.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Observed
public class FeegowAppointmentSearcher {

    private static final int FEEGOW_STATUS_AGENDADO = 1;

    private final AppointmentMotorProperties appointmentMotorProperties;
    private final AppointmentExternalPort appointmentExternalPort;

    @Value("${APP_APPOINTMENT_FEEGOW_SEARCH_CONCURRENCY:5}")
    private int feegowSearchConcurrency;

    @Value("${APP_APPOINTMENT_FEEGOW_PACE_DELAY_MS:50}")
    private long feegowPacingDelayMs;

    private Semaphore feegowSemaphore;

    @PostConstruct
    public void init() {
        this.feegowSemaphore = new Semaphore(feegowSearchConcurrency, true);
        log.info("[FEEGOW-RATE-LIMIT] Inicializado controle de requisições da Feegow: concorrência máxima={}, pacingDelay={}ms",
                feegowSearchConcurrency, feegowPacingDelayMs);
    }

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

    /**
     * Consulta e consolida os agendamentos da Feegow em lote controlado para os médicos fornecidos.
     */
    public List<FeegowAppointment> searchAppointments(LocalDate targetDate, List<String> doctorIds) {
        if (doctorIds == null || doctorIds.isEmpty()) {
            return searchAppointments(targetDate);
        }

        log.info("[FEEGOW-SEARCH] Buscando agendamentos em lote controlado (concorrência max={}) para {} médico(s)",
                feegowSearchConcurrency, doctorIds.size());

        List<FeegowAppointment> threadSafeAppointments = Collections.synchronizedList(new ArrayList<>());
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (String docId : doctorIds) {
                String trimmedDocId = docId.trim();
                if (!trimmedDocId.isEmpty() && trimmedDocId.matches("\\d+")) {
                    futures.add(runAsyncSearch(LocalDate.now(), trimmedDocId, threadSafeAppointments, executor));
                    futures.add(runAsyncSearch(targetDate, trimmedDocId, threadSafeAppointments, executor));
                }
            }
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        }
        return new ArrayList<>(threadSafeAppointments);
    }

    private List<FeegowAppointment> searchTestModeAppointments(LocalDate targetDate) {
        String testDoctorIds = appointmentMotorProperties.getTestModeDoctorIds();
        
        if (testDoctorIds != null && (testDoctorIds.contains("${") || testDoctorIds.contains("}"))) {
            log.warn("[FEEGOW-SEARCH] Detectada sintaxe de placeholder cru de ambiente na configuração: '{}'. Nenhuma pauta médica configurada.", testDoctorIds);
            testDoctorIds = null;
        }
        
        if (testDoctorIds == null || testDoctorIds.isBlank()) {
            testDoctorIds = appointmentMotorProperties.getTestDoctorId();
        }
        
        if (testDoctorIds == null || testDoctorIds.isBlank()) {
            log.info("[MODO-TESTE] Busca abortada. Nenhuma pauta médica configurada no ambiente.");
            return Collections.emptyList();
        }
        
        log.info("[MODO TESTE] Buscando agendamentos apenas para os médicos de teste IDs: {}", testDoctorIds);
        
        List<FeegowAppointment> threadSafeAppointments = Collections.synchronizedList(new ArrayList<>());
        if (testDoctorIds != null && !testDoctorIds.isBlank()) {
            String[] doctorIds = testDoctorIds.split(",");
            
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                List<CompletableFuture<Void>> futures = new ArrayList<>();
                for (String docId : doctorIds) {
                    String trimmedDocId = docId.trim();
                    if (!trimmedDocId.isEmpty() && trimmedDocId.matches("\\d+")) {
                        futures.add(runAsyncSearch(LocalDate.now(), trimmedDocId, threadSafeAppointments, executor));
                        futures.add(runAsyncSearch(targetDate, trimmedDocId, threadSafeAppointments, executor));
                    } else if (!trimmedDocId.isEmpty()) {
                        log.warn("[FEEGOW-SEARCH] ID de médico de teste '{}' inválido (deve ser estritamente numérico). Ignorando.", trimmedDocId);
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
                feegowSemaphore.acquire();
                try {
                    if (feegowPacingDelayMs > 0) {
                        Thread.sleep(feegowPacingDelayMs);
                    }
                    List<FeegowAppointment> res = appointmentExternalPort.searchAppointments(
                        date,
                        FEEGOW_STATUS_AGENDADO,
                        docId
                    );
                    if (res != null) {
                        list.addAll(res);
                    }
                } finally {
                    feegowSemaphore.release();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("[FEEGOW-SEARCH] Busca interrompida para data {}, médico ID: {}", date, docId);
            } catch (Exception e) {
                log.error("[FEEGOW-SEARCH] Erro ao buscar consultas na Feegow para {}, médico ID: {}", date, docId, e);
            }
        }, executor);
    }
}


