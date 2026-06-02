package br.dev.ctrls.inovareti.modules.appointment.application.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import org.springframework.stereotype.Component;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.FeegowPatient;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.PatientExternalPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Componente encarregado de carregar as informações detalhadas dos pacientes em paralelo,
 * acelerando de forma otimizada os tempos de processamento da ingestão de agendamentos.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeegowPatientDetailsFetcher {

    private final PatientExternalPort patientExternalPort;

    /**
     * Busca os detalhes dos pacientes informados em lote paralelo utilizando Virtual Threads.
     */
    public ConcurrentHashMap<String, FeegowPatient> fetchPatientDetailsInParallel(Set<String> patientIds) {
        ConcurrentHashMap<String, FeegowPatient> patientDetailsCache = new ConcurrentHashMap<>();
        if (patientIds == null || patientIds.isEmpty()) {
            return patientDetailsCache;
        }

        log.info("[VIRTUAL-THREADS] Iniciando busca assíncrona de detalhes para {} pacientes em paralelo.", patientIds.size());
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<Void>> patientFutures = new ArrayList<>();
            for (String patientId : patientIds) {
                patientFutures.add(CompletableFuture.runAsync(() -> {
                    try {
                        FeegowPatient details = patientExternalPort.patientInfo(patientId);
                        if (details != null) {
                            patientDetailsCache.put(patientId, details);
                        }
                    } catch (Exception e) {
                        log.error("[VIRTUAL-THREADS] Falha ao obter informações do paciente ID: {}", patientId, e);
                    }
                }, executor));
            }
            CompletableFuture.allOf(patientFutures.toArray(CompletableFuture[]::new)).join();
        }
        log.info("[VIRTUAL-THREADS] Busca em lote de pacientes concluída com sucesso. {} registros em cache.", patientDetailsCache.size());
        return patientDetailsCache;
    }
}
