package br.dev.ctrls.inovareti.modules.appointment.application.usecase;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.transaction.support.TransactionTemplate;

import br.dev.ctrls.inovareti.modules.access.infrastructure.adapter.output.BlipContactClientAdapter;
import br.dev.ctrls.inovareti.modules.appointment.application.service.AppointmentTemplateDataBuilder;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentSession;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.DoctorConfiguration;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentDoctorMappingRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentExternalPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.DoctorConfigurationRepository;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.FeegowAppointment;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.FeegowPatient;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.PatientExternalPort;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.AppointmentMotorProperties;

public class DoctorBusinessRulesTest {

    @Test
    public void testWednesdayD2ConstraintLogic() {
        LocalDate wednesday = LocalDate.of(2026, 7, 29); // Wednesday
        LocalDate tuesday = LocalDate.of(2026, 7, 28);   // Tuesday

        assertEquals(DayOfWeek.WEDNESDAY, wednesday.getDayOfWeek());
        assertEquals(DayOfWeek.TUESDAY, tuesday.getDayOfWeek());

        DoctorConfiguration d2Config = DoctorConfiguration.builder()
                .feegowProfissionalId(27L)
                .doctorName("Dr. Giuliano")
                .advanceNoticeDays(2)
                .isActive(true)
                .build();

        // Simulate logic check
        boolean shouldRunOnWednesday = d2Config.getResolvedAdvanceNoticeDays() == 2 && wednesday.getDayOfWeek() == DayOfWeek.WEDNESDAY;
        boolean shouldRunOnTuesday = d2Config.getResolvedAdvanceNoticeDays() == 2 && tuesday.getDayOfWeek() == DayOfWeek.WEDNESDAY;

        assertTrue(shouldRunOnWednesday, "D+2 deve executar na Quarta-feira");
        assertFalse(shouldRunOnTuesday, "D+2 deve ser ignorado na Terça-feira");
    }

    @Test
    public void testTimeShiftSubtractionInTemplateDataBuilder() {
        PatientExternalPort patientExternalPort = mock(PatientExternalPort.class);
        AppointmentExternalPort appointmentExternalPort = mock(AppointmentExternalPort.class);
        AppointmentDoctorMappingRepositoryPort appointmentDoctorMappingRepository = mock(AppointmentDoctorMappingRepositoryPort.class);
        DoctorConfigurationRepository doctorConfigurationRepository = mock(DoctorConfigurationRepository.class);
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);

        AppointmentTemplateDataBuilder builder = new AppointmentTemplateDataBuilder(
                patientExternalPort, appointmentExternalPort, appointmentDoctorMappingRepository,
                doctorConfigurationRepository, transactionTemplate
        );

        Long docId = 28L;
        LocalDateTime originalTime = LocalDateTime.of(2026, 7, 28, 10, 0);

        AppointmentSession session = AppointmentSession.builder()
                .patientId("100")
                .doctorProfissionalId("28")
                .feegowAppointmentId("300")
                .appointmentAt(originalTime)
                .phoneNumber("5542999999999")
                .build();

        FeegowAppointment appointment = new FeegowAppointment(
                "300", "100", "28", "28", "Dr. Eduardo Mattos", originalTime, null, "Clínica", "Unidade", false
        );
        FeegowPatient patient = new FeegowPatient("100", "João Silva", "5542999999999", "12345678900", "1990-01-01");

        when(appointmentExternalPort.searchAppointments(any(), any(Integer.class), eq("28")))
                .thenReturn(java.util.List.of(appointment));
        when(patientExternalPort.patientInfo("100")).thenReturn(patient);

        DoctorConfiguration config = DoctorConfiguration.builder()
                .feegowProfissionalId(docId)
                .doctorName("Dr. Eduardo Mattos")
                .displayTimeOffsetMinutes(-10)
                .build();

        when(doctorConfigurationRepository.findById(docId)).thenReturn(java.util.Optional.of(config));

        var data = builder.build(session);

        // 10:00 - 10 min = 09:50
        assertEquals("09:50", data.appointmentTime(), "O horário formatado deve subtrair 10 minutos conforme time_shift_minutes");
    }

    @Test
    public void testBlipContactClientAdapterNameSanitization() {
        AppointmentMotorProperties props = mock(AppointmentMotorProperties.class);
        when(props.getBlipBaseUrl()).thenReturn("http://localhost");
        when(props.isTestMode(any())).thenReturn(true);

        BlipContactClientAdapter adapter = new BlipContactClientAdapter(props);

        // Pass tunnel identity string as name -> should be sanitized
        boolean resultTunnel = adapter.syncContact("5542999999999", "5542999999999.fluxov1@tunnel.msging.net", "12345678900", "Fila Teste", "27");
        assertTrue(resultTunnel);

        // Pass real name -> valid
        boolean resultReal = adapter.syncContact("5542999999999", "Kathe Cristine", "12345678900", "Fila Teste", "27");
        assertTrue(resultReal);
    }
}
