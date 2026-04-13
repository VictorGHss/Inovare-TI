package br.dev.ctrls.inovareti.domain.appointment.usecase;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.dev.ctrls.inovareti.core.exception.NotFoundException;
import br.dev.ctrls.inovareti.domain.appointment.AppointmentCategory;
import br.dev.ctrls.inovareti.domain.appointment.AppointmentConfig;
import br.dev.ctrls.inovareti.domain.appointment.AppointmentConfigRepository;
import br.dev.ctrls.inovareti.domain.appointment.AppointmentSession;
import br.dev.ctrls.inovareti.domain.appointment.AppointmentVariableLog;
import br.dev.ctrls.inovareti.domain.appointment.AppointmentVariableLogRepository;
import br.dev.ctrls.inovareti.domain.appointment.BlipClient;
import br.dev.ctrls.inovareti.domain.appointment.FeegowClient;
import br.dev.ctrls.inovareti.domain.appointment.TemplateVariableMapping;
import br.dev.ctrls.inovareti.domain.appointment.TemplateVariableMappingRepository;
import br.dev.ctrls.inovareti.domain.appointment.VariableResolver;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SendAppointmentTemplateUseCase {

    private final AppointmentConfigRepository appointmentConfigRepository;
    private final TemplateVariableMappingRepository templateVariableMappingRepository;
    private final AppointmentVariableLogRepository appointmentVariableLogRepository;
    private final VariableResolver variableResolver;
    private final FeegowClient feegowClient;
    private final BlipClient blipClient;

    @Transactional
    public void execute(AppointmentSession session, AppointmentCategory category) {
        AppointmentConfig config = appointmentConfigRepository.findByCategory(category)
                .orElseThrow(() -> new NotFoundException("Configuração não encontrada para categoria " + category));

        List<TemplateVariableMapping> mappings = templateVariableMappingRepository
                .findByConfigCategoryOrderByPlaceholderIndexAsc(category);

        FeegowClient.FeegowAppointment appointment = new FeegowClient.FeegowAppointment(
                session.getFeegowAppointmentId(),
                session.getPatientId(),
                session.getDoctorProfissionalId(),
                null,
                null,
                session.getAppointmentAt());
        FeegowClient.FeegowPatient patient = feegowClient.patientInfo(session.getPatientId());

        Map<String, Object> context = variableResolver.buildContext(appointment, patient);
        List<String> values = new ArrayList<>();

        for (TemplateVariableMapping mapping : mappings) {
            String resolved = variableResolver.resolve(mapping.getDictionaryKey(), context);
            values.add(resolved);

            appointmentVariableLogRepository.save(AppointmentVariableLog.builder()
                    .session(session)
                    .category(category)
                    .placeholderIndex(mapping.getPlaceholderIndex())
                    .dictionaryKey(mapping.getDictionaryKey())
                    .resolvedValue(resolved)
                    .build());
        }

        blipClient.sendTemplateMessage(session.getPatientPhone(), config.getTemplateId(), values);
    }
}
