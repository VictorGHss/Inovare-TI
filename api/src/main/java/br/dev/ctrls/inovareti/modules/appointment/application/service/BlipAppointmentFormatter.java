package br.dev.ctrls.inovareti.modules.appointment.application.service;

import io.micrometer.observation.annotation.Observed;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSession;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentDoctorMappingRepositoryPort;
import lombok.RequiredArgsConstructor;

/**
 * Componente especialista na formatação do layout visual de listagem de consultas
 * múltiplas (lista_detalhada) de acordo com o padrão corporativo brasileiro.
 */
@Component
@RequiredArgsConstructor
@Observed
public class BlipAppointmentFormatter {

    private final AppointmentDoctorMappingRepositoryPort appointmentDoctorMappingRepository;
    private final BlipTextSanitizer blipTextSanitizer;

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
            String dateStr = s.getAppointmentAt().toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM"));
            String timeStr = s.getAppointmentAt().toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"));

            String doctorName = "Clínica Inovare";
            var mappingOpt = appointmentDoctorMappingRepository.findByProfissionalId(s.getDoctorProfissionalId());
            if (mappingOpt.isPresent()) {
                var mapping = mappingOpt.get();
                String docName = mapping.getProfissionalNome();
                if (docName != null && !docName.isBlank() && !"null".equalsIgnoreCase(docName.trim())) {
                    doctorName = docName.trim();
                }
            }
            doctorName = blipTextSanitizer.sanitizeDoctorName(doctorName);

            // Comentário em Português:
            // Formatação minimalista estrita contendo o emoji, o nome do profissional higienizado, a data (dd/MM) e o horário (HH:mm).
            String formatted = String.format("\uD83E\uDE7A %s - %s às %s", doctorName, dateStr, timeStr);
            details.add(formatted);
        }

        String resultStr = String.join("\n", details);
        return java.text.Normalizer.normalize(resultStr, java.text.Normalizer.Form.NFC);
    }
}
