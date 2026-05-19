package br.dev.ctrls.inovareti.modules.appointment.application.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import br.dev.ctrls.inovareti.modules.appointment.application.dto.AppointmentEventMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço responsável por publicar eventos de agendamento para o frontend.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentRealtimeNotificationService {

    private static final String APPOINTMENT_EVENTS_TOPIC = "/topic/appointment-events";

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Método de envio usado pelo fluxo de webhook de confirmação.
     */
    public void sendNotification(String patientName, String doctorName, String status) {
        AppointmentEventMessage payload = new AppointmentEventMessage(patientName, doctorName, status);
        messagingTemplate.convertAndSend(APPOINTMENT_EVENTS_TOPIC, payload);
        log.debug("Evento de agendamento publicado. patientName={}, doctorName={}, status={}",
                patientName, doctorName, status);
    }

    public void publish(String patientName, String doctorName, String status) {
        sendNotification(patientName, doctorName, status);
    }
}
