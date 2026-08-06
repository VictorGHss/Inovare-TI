package br.dev.ctrls.inovareti.modules.appointment.application.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;

import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentSessionRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentTemplateMappingRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.client.BlipLIMEClient;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.AppointmentMotorProperties;

public class BlipNotificationServiceTest {

    private BlipNotificationService service;
    private AppointmentMotorProperties properties;

    @BeforeEach
    public void setUp() {
        BlipLIMEClient limeClient = mock(BlipLIMEClient.class);
        AppointmentTemplateMappingRepositoryPort templateMappingRepository = mock(AppointmentTemplateMappingRepositoryPort.class);
        properties = new AppointmentMotorProperties();
        BlipPayloadBuilder payloadBuilder = mock(BlipPayloadBuilder.class);
        BlipContextService contextService = mock(BlipContextService.class);
        AppointmentSessionRepositoryPort sessionRepository = mock(AppointmentSessionRepositoryPort.class);
        BlipAppointmentFormatter formatter = mock(BlipAppointmentFormatter.class);

        service = new BlipNotificationService(
                limeClient,
                templateMappingRepository,
                properties,
                payloadBuilder,
                contextService,
                sessionRepository,
                formatter
        );
    }

    @Test
    public void testDoctorInBlocklistReturnsFalse() {
        service.setRawBlockedDoctorIds("46");

        assertFalse(service.isDoctorAllowed("46"), "Médico 46 presente na lista de bloqueio deve retornar false");
        assertFalse(service.isDoctorAllowed(" 46 "), "Médico 46 com espaços deve retornar false");
    }

    @Test
    public void testDoctorOutsideBlocklistWithAllowlistReturnsTrue() {
        service.setRawBlockedDoctorIds("46");
        properties.setTestDoctorId("1,70");

        assertTrue(service.isDoctorAllowed("1"), "Médico 1 na allowlist de teste deve retornar true");
        assertTrue(service.isDoctorAllowed("70"), "Médico 70 na allowlist de teste deve retornar true");
        assertFalse(service.isDoctorAllowed("46"), "Médico 46 bloqueado deve retornar false mesmo se estivesse em allowlist");
    }

    @Test
    public void testEmptyBlocklistFailOpenDefault() {
        service.setRawBlockedDoctorIds("");

        assertTrue(service.isDoctorAllowed("99"), "Com lista de bloqueio e allowlist vazias, o comportamento padrão deve ser fail-open (true)");
        assertTrue(service.isDoctorAllowed("46"), "Sem bloqueio configurado e sem allowlist, o médico 46 deve ser permitido");
    }
}
