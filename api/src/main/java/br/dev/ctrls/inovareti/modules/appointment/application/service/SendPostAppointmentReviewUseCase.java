package br.dev.ctrls.inovareti.modules.appointment.application.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.modules.appointment.application.util.GoogleReviewUrlUtils;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSession;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.DoctorConfiguration;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentExternalPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentSessionRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.DoctorConfigurationRepository;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.FeegowAppointment;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.PatientExternalPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.FeegowPatient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Caso de Uso responsável pelo envio da mensagem de avaliação do Google Review
 * para pacientes cujas consultas estejam com status 3 (Atendido) no Feegow ERP.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SendPostAppointmentReviewUseCase {

    private static final String TEMPLATE_REVIEW_GOOGLE = "pesquisa_avaliacao_google_v1_copia";
    private static final int FEEGOW_STATUS_ATENDIDO = 3;

    private final AppointmentExternalPort appointmentExternalPort;
    private final AppointmentSessionRepositoryPort appointmentSessionRepository;
    private final DoctorConfigurationRepository doctorConfigurationRepository;
    private final PatientExternalPort patientExternalPort;
    private final BlipNotificationService blipNotificationService;

    @Transactional
    public int execute() {
        LocalDate today = LocalDate.now();
        log.info("[GOOGLE-REVIEW] Iniciando verificação de consultas atendidas (StatusID=3) na Feegow para a data {}", today);

        List<FeegowAppointment> attendedAppointments;
        try {
            attendedAppointments = appointmentExternalPort.searchAppointments(today, FEEGOW_STATUS_ATENDIDO);
        } catch (Exception ex) {
            log.error("[GOOGLE-REVIEW] Falha ao consultar agendamentos atendidos no Feegow: {}", ex.getMessage(), ex);
            return 0;
        }

        if (attendedAppointments == null || attendedAppointments.isEmpty()) {
            log.info("[GOOGLE-REVIEW] Nenhum agendamento com status 'Atendido' (StatusID=3) encontrado para a data {}.", today);
            return 0;
        }

        log.info("[GOOGLE-REVIEW] Encontrados {} agendamentos com status 'Atendido' (StatusID=3) para a data {}.", attendedAppointments.size(), today);

        int countSent = 0;

        for (FeegowAppointment appt : attendedAppointments) {
            if (appt == null || appt.id() == null || appt.id().isBlank()) {
                continue;
            }

            String feegowAppointmentId = appt.id().trim();
            if (feegowAppointmentId.contains(".")) {
                feegowAppointmentId = feegowAppointmentId.substring(0, feegowAppointmentId.indexOf('.'));
            }

            try {
                Optional<AppointmentSession> sessionOpt = appointmentSessionRepository.findByFeegowAppointmentId(feegowAppointmentId);
                AppointmentSession session;

                if (sessionOpt.isPresent()) {
                    session = sessionOpt.get();
                    if (session.getReviewRequestedAt() != null) {
                        log.debug("[GOOGLE-REVIEW] Pesquisa de avaliação já solicitada em {} para o agendamento ID {}. Pulando.",
                                session.getReviewRequestedAt(), feegowAppointmentId);
                        continue;
                    }
                } else {
                    session = AppointmentSession.builder()
                            .feegowAppointmentId(feegowAppointmentId)
                            .patientId(appt.patientId())
                            .doctorProfissionalId(appt.doctorId())
                            .appointmentAt(appt.startAt() != null ? appt.startAt() : LocalDateTime.now())
                            .status(br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSessionStatus.CONFIRMED)
                            .lastInteractionAt(LocalDateTime.now())
                            .build();
                }

                String phone = session.getPhoneNumber();
                String patientName = "Paciente";

                if (appt.patientId() != null && !appt.patientId().isBlank()) {
                    try {
                        FeegowPatient patient = patientExternalPort.patientInfo(appt.patientId());
                        if (patient != null) {
                            if (phone == null || phone.isBlank()) {
                                phone = patient.phone();
                            }
                            if (patient.name() != null && !patient.name().isBlank()) {
                                patientName = patient.name().trim();
                            }
                        }
                    } catch (Exception pEx) {
                        log.warn("[GOOGLE-REVIEW] Falha ao obter dados do paciente ID {}: {}", appt.patientId(), pEx.getMessage());
                    }
                }

                if (phone == null || phone.isBlank()) {
                    log.warn("[GOOGLE-REVIEW] Telefone não encontrado para o agendamento ID {}. Impossível enviar pesquisa de avaliação.", feegowAppointmentId);
                    continue;
                }

                String googleReviewUrl = null;
                String doctorName = "Clínica Inovare";

                if (appt.doctorId() != null && !appt.doctorId().isBlank()) {
                    try {
                        Long docId = Long.parseLong(appt.doctorId().trim());
                        Optional<DoctorConfiguration> docConfigOpt = doctorConfigurationRepository.findById(docId);
                        if (docConfigOpt.isPresent()) {
                            DoctorConfiguration docCfg = docConfigOpt.get();
                            if (docCfg.getDoctorName() != null && !docCfg.getDoctorName().isBlank()) {
                                doctorName = docCfg.getDoctorName().trim();
                            }
                            if (docCfg.getGoogleReviewUrl() != null && !docCfg.getGoogleReviewUrl().isBlank()) {
                                googleReviewUrl = docCfg.getGoogleReviewUrl();
                            }
                        }
                    } catch (Exception dEx) {
                        log.warn("[GOOGLE-REVIEW] Falha ao buscar configuração do médico ID {}: {}", appt.doctorId(), dEx.getMessage());
                    }
                }

                if ("Clínica Inovare".equalsIgnoreCase(doctorName) && appt.doctorName() != null && !appt.doctorName().isBlank()) {
                    doctorName = appt.doctorName().trim();
                }

                String rawHash = GoogleReviewUrlUtils.extractHash(googleReviewUrl);
                String reviewHash = (rawHash != null && !rawHash.isBlank()) ? rawHash.trim().replaceAll("\\s+", "") : "jrskH337hFK5Mn3WP";

                log.info("[GOOGLE-REVIEW] Enviando template '{}' para agendamento ID {} (Paciente: {}, Médico: {}, Telefone: {}, Hash: {})",
                        TEMPLATE_REVIEW_GOOGLE, feegowAppointmentId, patientName, doctorName, phone, reviewHash);

                blipNotificationService.sendReviewTemplateMessage(phone, TEMPLATE_REVIEW_GOOGLE, patientName, doctorName, reviewHash);

                session.setReviewRequestedAt(LocalDateTime.now());
                if (session.getPhoneNumber() == null || session.getPhoneNumber().isBlank()) {
                    session.setPhoneNumber(phone);
                }
                appointmentSessionRepository.save(session);
                countSent++;

            } catch (Exception ex) {
                log.error("[GOOGLE-REVIEW] Erro ao processar envio de avaliação para o agendamento ID {}: {}", feegowAppointmentId, ex.getMessage(), ex);
            }
        }

        log.info("[GOOGLE-REVIEW] Processamento concluído. Total de avaliações enviadas: {}", countSent);
        return countSent;
    }
}
