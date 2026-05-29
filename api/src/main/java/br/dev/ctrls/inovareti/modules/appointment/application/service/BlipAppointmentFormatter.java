package br.dev.ctrls.inovareti.modules.appointment.application.service;

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
            return "Ops, não encontrei seus agendamentos agora, aguarde um instante.";
        }

        List<String> details = new ArrayList<>();
        details.add("PRÓXIMOS ATENDIMENTOS:");

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

            details.add("🔹 " + doctorName);
            details.add("  Data: " + dateStr);
            details.add("  Horario: " + timeStr);
        }

        if (details.size() <= 1) {
            return "Ops, não encontrei seus agendamentos agora, aguarde um instante.";
        }

        details.add("Por favor, confirme se você comparecerá aos horários listados acima.");
        return String.join("\n", details);
    }
}
