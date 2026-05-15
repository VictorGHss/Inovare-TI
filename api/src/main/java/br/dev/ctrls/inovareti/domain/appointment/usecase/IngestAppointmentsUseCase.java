package br.dev.ctrls.inovareti.domain.appointment.usecase;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.dev.ctrls.inovareti.domain.appointment.AppointmentCategory;
import br.dev.ctrls.inovareti.domain.appointment.AppointmentDoctorMappingRepository;
import br.dev.ctrls.inovareti.domain.appointment.AppointmentMotorProperties;
import br.dev.ctrls.inovareti.domain.appointment.AppointmentSendIdempotencyService;
import br.dev.ctrls.inovareti.domain.appointment.AppointmentSession;
import br.dev.ctrls.inovareti.domain.appointment.AppointmentSessionRepository;
import br.dev.ctrls.inovareti.domain.appointment.AppointmentSessionStatus;
import br.dev.ctrls.inovareti.domain.appointment.FeegowClient;
import br.dev.ctrls.inovareti.domain.appointment.NoopAppointmentSendIdempotencyService;
import br.dev.ctrls.inovareti.domain.appointment.service.BlipLIMEClient;
import br.dev.ctrls.inovareti.domain.appointment.dto.FeegowPatientDetailsDto;
import br.dev.ctrls.inovareti.domain.appointment.dto.AppointmentDispatchContext;
import br.dev.ctrls.inovareti.domain.appointment.utils.StringSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class IngestAppointmentsUseCase {

    private static final int FEEGOW_STATUS_AGENDADO = 1;

    private final AppointmentMotorProperties appointmentMotorProperties;
    private final FeegowClient feegowClient;
    private final ObjectMapper objectMapper;
    private final AppointmentDoctorMappingRepository appointmentDoctorMappingRepository;
    private final AppointmentSessionRepository appointmentSessionRepository;
    private final BlipLIMEClient blipLIMEClient;
    private final SendAppointmentTemplateUseCase sendAppointmentTemplateUseCase;
    private final Optional<AppointmentSendIdempotencyService> appointmentSendIdempotencyService;
    private final Optional<NoopAppointmentSendIdempotencyService> noopAppointmentSendIdempotencyService;
    private final jakarta.persistence.EntityManager entityManager;

    @Transactional
    public IngestionSummary execute() {
        LocalDate targetDate = LocalDate.now().plusDays(1);
        log.info("Iniciando ingestão de agendamentos para a data: {}", targetDate);

        List<FeegowClient.FeegowAppointment> appointments;
        
        if (appointmentMotorProperties.isTestMode()) {
            String testDoctorId = appointmentMotorProperties.getTestDoctorId();
            log.info("[TEST MODE] Buscando agendamentos apenas para o médico de teste ID: {}", testDoctorId);
            
            // Busca agendamentos de hoje e amanhã no modo teste para facilitar validação
            appointments = new ArrayList<>();
            appointments.addAll(feegowClient.searchAppointments(
                LocalDate.now(),
                FEEGOW_STATUS_AGENDADO,
                testDoctorId));
            
            appointments.addAll(feegowClient.searchAppointments(
                targetDate,
                FEEGOW_STATUS_AGENDADO,
                testDoctorId));
        } else {
            log.info("Consultando Feegow para ingestão de agendamentos com status Marcado (ID={})", FEEGOW_STATUS_AGENDADO);
            appointments = feegowClient.searchAppointments(
                targetDate,
                FEEGOW_STATUS_AGENDADO,
                null);
        }

        int totalReceived = appointments.size();
        int filteredReceived = appointments.size();

        int created = 0;
        int messagesSent = 0;
        for (FeegowClient.FeegowAppointment appointment : appointments) {
            log.info("Processando agendamento Feegow ID: {} - Status: {}", appointment.id(), appointment.statusId());

            if ("12".equals(appointment.statusId())) {
                log.info("Agendamento ignorado pois é um ENCAIXE (status_id=12). appointmentId={}", appointment.id());
                filteredReceived--;
                continue;
            }

            if (!hasMappedDoctor(appointment.doctorId())) {
                log.info("Agendamento ignorado por ausência de mapeamento do médico. appointmentId={}, profissional_id={}",
                        appointment.id(),
                        appointment.doctorId());
                continue;
            }

            String feegowAppointmentId = normalizeFeegowAppointmentId(appointment.id());
            if (feegowAppointmentId.isBlank()) {
                log.warn("ID Feegow vazio. Conteúdo recebido: {} | rawId={}",
                        serializeAppointmentForLog(appointment),
                        appointment.id());
                continue;
            }

            Optional<AppointmentSession> existingSessionOpt = appointmentSessionRepository.findByFeegowAppointmentId(feegowAppointmentId);
            if (existingSessionOpt.isPresent()) {
                AppointmentSessionStatus status = existingSessionOpt.get().getStatus();
                if (status == AppointmentSessionStatus.PENDING ||
                    status == AppointmentSessionStatus.NUDGE_1_SENT ||
                    status == AppointmentSessionStatus.NUDGE_FINAL_SENT ||
                    status == AppointmentSessionStatus.CONFIRMED) {
                    log.info("Agendamento ignorado: já existe uma AppointmentSession ativa (status {}) para feegowAppointmentId={}", status, feegowAppointmentId);
                    continue;
                }
            }

            boolean canSend = appointmentSendIdempotencyService
                    .map(service -> service.registerIfFirstSend(feegowAppointmentId))
                    .orElseGet(() -> noopAppointmentSendIdempotencyService
                            .map(service -> service.registerIfFirstSend(feegowAppointmentId))
                            .orElse(true));

            if (!canSend) {
                log.info("Envio ignorado por idempotência Redis. appointmentId={}", feegowAppointmentId);
                continue;
            }

            String patientId = appointment.patientId();
            FeegowPatientDetailsDto.PatientItem patientDetails;

            // Lookup mapping early to obtain destination queue and any stored professional name.
            // IMUTABILIDADE: extraímos apenas Strings finais ANTES de entrar nas Virtual Threads.
            // Isso impede que o contexto Hibernate vaze entre threads e troque o nome do médico.
            var mappingOpt = appointmentDoctorMappingRepository.findByProfissionalIdLocked(appointment.doctorId());
            final String mappingQueue = mappingOpt.map(m -> m.getBlipQueueId()).orElse(null);
            final String mappingProfessionalNameLocal = mappingOpt.map(m -> m.getProfissionalNome()).orElse(null);
            final String appointmentDoctorId = appointment.doctorId() != null ? appointment.doctorId().trim() : null;

            log.info("[MAPEAMENTO MÉDICO] profissional_id={} | mapeamento encontrado={} | nome_mapeado={} | fila_mapeada={}",
                appointmentDoctorId, mappingOpt.isPresent(), mappingProfessionalNameLocal, mappingQueue);

            // resolvedProfessionalName é uma String final simples — nunca um proxy Hibernate
            String resolvedProfessionalName;
            if (mappingProfessionalNameLocal != null
                && !mappingProfessionalNameLocal.isBlank()
                && !"null".equalsIgnoreCase(mappingProfessionalNameLocal.trim())) {
                resolvedProfessionalName = mappingProfessionalNameLocal.trim();
            } else {
                resolvedProfessionalName = "Clínica Inovare";
            }

            // Use virtual threads APENAS para I/O puro (dados do paciente)
            // O nome do profissional já está resolvido e imutável acima — NÃO reutilize mappingOpt dentro da lambda
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                var patientFuture = CompletableFuture.supplyAsync(() ->
                    feegowClient.getPatientDetails(patientId), executor);

                // Tenta enriquecer o nome via Feegow apenas se o mapeamento não encontrou um nome
                final boolean needFeegowName = "Clínica Inovare".equals(resolvedProfessionalName);
                final String currentResolvedName = resolvedProfessionalName; // captura para lambda

                var professionalFuture = CompletableFuture.supplyAsync(() -> {
                    if (!needFeegowName) return currentResolvedName; // já temos o nome do banco, não consulta
                    try {
                        String feegowName = feegowClient.getProfessionalName(appointmentDoctorId);
                        if (feegowName != null && !feegowName.isBlank() && !"null".equalsIgnoreCase(feegowName.trim())) {
                            return feegowName.trim();
                        }
                    } catch (Exception e) {
                        log.warn("Falha ao recuperar nome do profissional via Feegow para id {}: {}", appointmentDoctorId, e.getMessage());
                    }
                    return currentResolvedName;
                }, executor);

                try {
                    patientDetails = patientFuture.join();
                } catch (Exception ex) {
                    RestClientResponseException rcre = null;
                    if (ex instanceof RestClientResponseException e) {
                        rcre = e;
                    } else if (ex instanceof java.util.concurrent.CompletionException && ex.getCause() instanceof RestClientResponseException e) {
                        rcre = e;
                    }

                    if (rcre != null) {
                        int statusCode = rcre.getStatusCode().value();
                        if (statusCode == 404 || statusCode == 500) {
                            log.error("Falha ao recuperar contato do paciente ID {}. Pulando agendamento.", patientId, rcre);
                        } else {
                            log.error("Erro ao recuperar contato do paciente ID {}. statusCode={}. Pulando agendamento.", patientId,
                                    statusCode,
                                    rcre);
                        }
                    } else {
                        log.error("Falha ao recuperar contato do paciente ID {}. Pulando agendamento.", patientId, ex);
                    }
                    continue;
                }

                // Get professional name, best-effort
                try {
                    String fetchedName = professionalFuture.join();
                    if (fetchedName != null && !fetchedName.isBlank()) {
                        resolvedProfessionalName = fetchedName;
                    }
                } catch (Exception ignored) {
                    // fall back to mappingProfessionalNameLocal which is already set
                }
            }

            String phoneSourceField = "celulares";
            String patientPhone = firstNonBlank(patientDetails != null ? patientDetails.getCelulares() : null);
            if (patientPhone == null || patientPhone.isBlank()) {
                patientPhone = firstNonBlank(patientDetails != null ? patientDetails.getTelefones() : null);
                phoneSourceField = "telefones";
            }

                String phoneNumber = normalizePhoneNumberForBlip(patientPhone);
                if (patientPhone == null || patientPhone.isBlank() || phoneNumber.isBlank()) {
                log.error("Falha ao recuperar contato do paciente ID {}. Pulando agendamento.", patientId);
                continue;
                }

                // Trava de segurança física: só permite envio para o número de teste em não-produção
                String appMode = System.getenv("APP_MODE");
                if (appMode == null) appMode = "";
                if (!"PRODUCTION".equalsIgnoreCase(appMode) && !"+5542991617187".equals(phoneNumber)) {
                blipLIMEClient.logSecurityBlock(phoneNumber, appMode);
                continue;
                }

                log.info("Dados do paciente recuperados: ID={}, Telefone={}, CampoUtilizado={}",
                    patientId,
                    patientPhone,
                    phoneSourceField);

                log.info("Agendamento Ingerido: Paciente={}, Telefone Feegow={}, Telefone Normalizado para Blip={}",
                    patientDetails != null ? patientDetails.getNome() : null,
                    patientPhone,
                    phoneNumber);

            AppointmentSession session = existingSessionOpt.orElseGet(AppointmentSession::new);

            session.setFeegowAppointmentId(feegowAppointmentId);
            session.setPatientId(appointment.patientId());
            session.setPhoneNumber(phoneNumber);
            session.setDoctorProfissionalId(appointment.doctorId());
            session.setAppointmentAt(appointment.startAt());
            session.setStatus(AppointmentSessionStatus.PENDING);
            session.setLastInteractionAt(LocalDateTime.now());
            session.setClosedAt(null);
            session.setStatusDetails(null);

            AppointmentSession saved = appointmentSessionRepository.save(session);
            entityManager.flush(); // Garante que a inserção/atualização vá pro banco antes do findByIdLocked na próxima etapa
            log.info("Agendamento salvo localmente: ID Feegow = {}", saved.getFeegowAppointmentId());

            // Sanitiza profissional_nome: nunca enviar null ou string vazia
            String safeProfissionalNome = (resolvedProfessionalName != null
                && !resolvedProfessionalName.isBlank()
                && !"null".equalsIgnoreCase(resolvedProfessionalName.trim()))
                ? resolvedProfessionalName.trim()
                : "Clínica Inovare";

            // Sanitiza fila_destino: nunca enviar null ou string vazia
            String safeFilaDestino = (mappingQueue != null
                && !mappingQueue.isBlank()
                && !"null".equalsIgnoreCase(mappingQueue.trim()))
                ? mappingQueue.trim()
                : StringSanitizer.UNICODE_LTR_MARK;

            log.info("[EXTRAS CONTATO] profissional_nome='{}' | fila_destino='{}'", safeProfissionalNome, safeFilaDestino);

            // Monta contexto imutável com todos os dados já resolvidos
            // Nenhuma entidade JPA é passada — apenas Strings finais
            final String finalPatientName = (patientDetails != null && patientDetails.getNome() != null)
                ? patientDetails.getNome().trim() : "Paciente";
            final String finalPatientPhone = (patientDetails != null)
                ? firstNonBlank(patientDetails.getCelulares() != null ? patientDetails.getCelulares() : patientDetails.getTelefones()) : null;
            final String finalDate = saved.getAppointmentAt() != null
                ? saved.getAppointmentAt().toLocalDate().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "";
            final String finalDateShort = saved.getAppointmentAt() != null
                ? saved.getAppointmentAt().toLocalDate().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM")) : "";
            final String finalTime = saved.getAppointmentAt() != null
                ? saved.getAppointmentAt().toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) : "";

            AppointmentDispatchContext ctx = new AppointmentDispatchContext(
                saved.getId(),
                feegowAppointmentId,
                finalPatientName,
                finalPatientPhone,
                saved.getPatientId(),
                appointmentDoctorId,
                resolvedProfessionalName,  // já resolvido: banco → Feegow → fallback
                safeFilaDestino,
                finalDate,
                finalDateShort,
                finalTime,
                phoneNumber
            );

            boolean templateSent = sendAppointmentTemplateUseCase.execute(ctx, AppointmentCategory.CONFIRMATION);
            created++;
            if (templateSent) {
                messagesSent++;
            }
        }

        String mode = appointmentMotorProperties.isTestMode() ? "TEST" : "PROD";
        log.info("Ingestão de consultas executada. totalRecebido={}, totalAposFiltro={}, sessoesCriadas={}, mensagensEnviadas={}, modo={}",
                totalReceived,
                filteredReceived,
                created,
                messagesSent,
                mode);

        return new IngestionSummary(totalReceived, filteredReceived, created, messagesSent, mode);
    }

    public record IngestionSummary(int totalReceived, int filteredReceived, int sessionsCreated, int messagesSent, String mode) {
    }

    private String normalizeFeegowAppointmentId(String feegowAppointmentId) {
        if (feegowAppointmentId == null) {
            return "";
        }

        String normalized = feegowAppointmentId.trim();
        if (normalized.matches("^\\d+\\.0+$")) {
            return normalized.substring(0, normalized.indexOf('.'));
        }

        return normalized;
    }

    private boolean hasMappedDoctor(String profissionalId) {
        if (profissionalId == null || profissionalId.isBlank()) {
            return false;
        }

        return appointmentDoctorMappingRepository.findByProfissionalIdLocked(profissionalId.trim()).isPresent();
    }

    private String normalizePhoneNumberForBlip(String originalPhone) {
        if (originalPhone == null || originalPhone.isBlank()) {
            return "";
        }

        String trimmed = originalPhone.trim();

        // Alguns cadastros retornam múltiplos contatos no mesmo campo. Mantém apenas o primeiro.
        if (trimmed.contains(",") || trimmed.contains("/") || trimmed.contains(" ")) {
            String[] parts = trimmed.split("[,/\\s]+");
            if (parts.length == 0 || parts[0] == null || parts[0].isBlank()) {
                return "";
            }

            trimmed = parts[0].trim();
        }

        String digitsOnly = trimmed.replaceAll("\\D", "");
        if (digitsOnly.isBlank()) {
            return "";
        }

        if (digitsOnly.startsWith("55")) {
            return "+" + digitsOnly;
        }

        return "+55" + digitsOnly;
    }

    private String serializeAppointmentForLog(FeegowClient.FeegowAppointment appointment) {
        if (appointment == null) {
            return "{}";
        }

        try {
            return objectMapper.writeValueAsString(appointment);
        } catch (JsonProcessingException ex) {
            return String.valueOf(appointment);
        }
    }

    private String firstNonBlank(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }

        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }
}
