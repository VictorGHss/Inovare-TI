package br.dev.ctrls.inovareti.modules.appointment.application.service;

import io.micrometer.observation.annotation.Observed;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSession;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.PatientExternalPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.FeegowPatient;
import lombok.RequiredArgsConstructor;

/**
 * Componente especialista na formatação do layout visual de listagem de consultas
 * múltiplas (lista_detalhada) de acordo com o padrão corporativo brasileiro.
 */
@Component
@RequiredArgsConstructor
@Observed
public class BlipAppointmentFormatter {

    private final PatientExternalPort patientExternalPort;

    /**
     * Constrói a string formatada da lista de agendamentos (lista_detalhada) de acordo
     * com o padrão visual corporativo rigoroso para agendamentos múltiplos.
     *
     * @param groupedSessions lista de sessões de agendamento do grupo
     * @return string formatada contendo data, horário e profissional
     */
    public String buildListaDetalhada(List<AppointmentSession> groupedSessions) {
        if (groupedSessions == null || groupedSessions.isEmpty()) {
            return "";
        }

        List<String> details = new ArrayList<>();

        for (AppointmentSession s : groupedSessions) {
            if (s.getAppointmentAt() == null) {
                continue;
            }
            String timeStr = s.getAppointmentAt().toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"));

            String patientName = "Paciente";
            try {
                FeegowPatient patient = patientExternalPort.patientInfo(s.getPatientId());
                if (patient != null && patient.name() != null && !patient.name().isBlank()) {
                    patientName = patient.name().trim();
                }
            } catch (Exception e) {
                // fallback se falhar
            }

            // Comentário em Português:
            // Formatação minimalista estrita contendo o emoji, o nome do paciente e o horário (HH:mm).
            String formatted = String.format("\uD83E\uDE7A %s - %s", patientName, timeStr);
            details.add(formatted);
        }

        String resultStr = String.join("\n", details);
        return java.text.Normalizer.normalize(resultStr, java.text.Normalizer.Form.NFC);
    }
}

