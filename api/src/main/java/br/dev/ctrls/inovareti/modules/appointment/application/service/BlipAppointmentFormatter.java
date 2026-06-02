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
 * Componente especialista na formataÃ§Ã£o do layout visual de listagem de consultas
 * mÃºltiplas (lista_detalhada) de acordo com o padrÃ£o corporativo brasileiro.
 */
@Component
@RequiredArgsConstructor
@Observed
public class BlipAppointmentFormatter {

    private final AppointmentDoctorMappingRepositoryPort appointmentDoctorMappingRepository;
    private final BlipTextSanitizer blipTextSanitizer;

    /**
     * ConstrÃ³i a string formatada da lista de agendamentos (lista_detalhada) de acordo
     * com o padrÃ£o visual corporativo rigoroso para agendamentos mÃºltiplos.
     *
     * @param groupedSessions lista de sessÃµes de agendamento do grupo
     * @return string formatada contendo data, horÃ¡rio e profissional
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

            String doctorName = "ClÃ­nica Inovare";
            var mappingOpt = appointmentDoctorMappingRepository.findByProfissionalId(s.getDoctorProfissionalId());
            if (mappingOpt.isPresent()) {
                var mapping = mappingOpt.get();
                String docName = mapping.getProfissionalNome();
                if (docName != null && !docName.isBlank() && !"null".equalsIgnoreCase(docName.trim())) {
                    doctorName = docName.trim();
                }
            }
            doctorName = blipTextSanitizer.sanitizeDoctorName(doctorName);

            // ComentÃ¡rio em PortuguÃªs:
            // FormataÃ§Ã£o minimalista estrita contendo o emoji, o nome do profissional higienizado, a data (dd/MM) e o horÃ¡rio (HH:mm).
            details.add("ðŸ”¹ " + doctorName + " - " + dateStr + " Ã s " + timeStr);
        }

        return String.join("\n", details);
    }
}


