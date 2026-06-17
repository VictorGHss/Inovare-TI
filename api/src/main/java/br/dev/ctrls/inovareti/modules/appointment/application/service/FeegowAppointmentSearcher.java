package br.dev.ctrls.inovareti.modules.appointment.application.service;

import io.micrometer.observation.annotation.Observed;

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
@Observed
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
        String testDoctorIds = appointmentMotorProperties.getTestModeDoctorIds();
        
        // HIGIENIZAÇÃO / VALIDAÇÃO DE PLACEHOLDER VAZADO:
        // Caso a propriedade venha nula, vazia ou contendo a sintaxe de placeholder cru do Spring (ex: "${TEST_MODE_DOCTOR_IDS}"),
        // aplica-se um fallback de segurança com as IDs de médicos padrão para evitar requisições com strings inválidas.
        if (testDoctorIds != null && (testDoctorIds.contains("${") || testDoctorIds.contains("}"))) {
            log.warn("[FEEGOW-SEARCH] Alerta de segurança: detectado placeholder cru ou vazamento de configuração '{}'. Aplicando fallback de IDs padrão (8,6,7,13,14,12).", testDoctorIds);
            testDoctorIds = "8,6,7,13,14,12";
        }
        
        if (testDoctorIds == null || testDoctorIds.isBlank()) {
            testDoctorIds = appointmentMotorProperties.getTestDoctorId();
        }
        
        if (testDoctorIds == null || testDoctorIds.isBlank()) {
            log.warn("[FEEGOW-SEARCH] Nenhuma ID de médico de teste configurada. Utilizando IDs padrão de emergência (8,6,7,13,14,12).");
            testDoctorIds = "8,6,7,13,14,12";
        }
        
        log.info("[MODO TESTE] Buscando agendamentos apenas para os médicos de teste IDs: {}", testDoctorIds);
        
        List<FeegowAppointment> threadSafeAppointments = Collections.synchronizedList(new ArrayList<>());
        if (testDoctorIds != null && !testDoctorIds.isBlank()) {
            String[] doctorIds = testDoctorIds.split(",");
            
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                List<CompletableFuture<Void>> futures = new ArrayList<>();
                for (String docId : doctorIds) {
                    String trimmedDocId = docId.trim();
                    // Valida se o ID do médico contém apenas dígitos numéricos para barrar injeções de texto inválidas
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


