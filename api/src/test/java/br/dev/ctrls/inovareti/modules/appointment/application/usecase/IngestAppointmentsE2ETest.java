package br.dev.ctrls.inovareti.modules.appointment.application.usecase;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.StopWatch;

import br.dev.ctrls.inovareti.modules.appointment.application.service.FeegowAppointmentSearcher;
import br.dev.ctrls.inovareti.modules.appointment.application.service.FeegowPatientDetailsFetcher;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentCategory;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentConfig;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentDoctorMapping;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentTemplateMapping;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentConfigRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentDoctorMappingRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentTemplateMappingRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.FeegowAppointment;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.FeegowPatient;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.client.BlipLIMEClient;

/**
 * Teste de integração ponta a ponta (E2E) para validação do ecossistema pós-refatoração
 * do Pacing de Limites da API do Blip.
 */
@SpringBootTest(properties = {
    "spring.scheduling.enabled=false"
})
public class IngestAppointmentsE2ETest {

    @Autowired
    private IngestAppointmentsUseCase ingestAppointmentsUseCase;

    @Autowired
    private AppointmentDoctorMappingRepositoryPort doctorMappingRepository;

    @Autowired
    private AppointmentConfigRepositoryPort configRepository;

    @Autowired
    private AppointmentTemplateMappingRepositoryPort templateMappingRepository;

    @MockitoBean
    private FeegowAppointmentSearcher feegowAppointmentSearcher;

    @MockitoBean
    private FeegowPatientDetailsFetcher feegowPatientDetailsFetcher;

    @MockitoBean
    private BlipLIMEClient blipLIMEClient;

    @BeforeEach
    public void setUp() {
        // Garante que o template do Blip exista na tabela de configurações para não dar NotFoundException
        if (configRepository.findByCategory(AppointmentCategory.CONFIRMATION).isEmpty()) {
            AppointmentConfig config = AppointmentConfig.builder()
                    .category(AppointmentCategory.CONFIRMATION)
                    .templateId("confirmacao_consulta_v6_itsm")
                    .timingHours(24)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            configRepository.save(config);
        }

        // Garante que o template de grupo do Blip exista
        if (configRepository.findByCategory(AppointmentCategory.GROUP_NOTIFICATION).isEmpty()) {
            AppointmentConfig groupConfig = AppointmentConfig.builder()
                    .category(AppointmentCategory.GROUP_NOTIFICATION)
                    .templateId("aviso_agendamento_grupo")
                    .timingHours(24)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            configRepository.save(groupConfig);
        }

        // Cria o mapeamento de médico associado para que ele seja considerado elegível e não ignorado
        if (doctorMappingRepository.findByProfissionalId("123").isEmpty()) {
            AppointmentDoctorMapping doctorMapping = AppointmentDoctorMapping.builder()
                    .profissionalId("123")
                    .profissionalNome("Dr. Teste Inovare")
                    .blipQueueId("fila-teste")
                    .ignoreAutoSchedule(false)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            doctorMappingRepository.save(doctorMapping);
        }

        // Garante que existam mapeamentos de placeholders para o template "confirmacao_consulta_v6_itsm" para evitar abort por parâmetros vazios
        if (templateMappingRepository.findByTemplateNameIgnoreCaseOrderByPlaceholderIndexAsc("confirmacao_consulta_v6_itsm").isEmpty()) {
            List<AppointmentTemplateMapping> mappings = List.of(
                AppointmentTemplateMapping.builder()
                        .templateName("confirmacao_consulta_v6_itsm")
                        .placeholderIndex(0)
                        .feegowFieldName("patient_name")
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build(),
                AppointmentTemplateMapping.builder()
                        .templateName("confirmacao_consulta_v6_itsm")
                        .placeholderIndex(1)
                        .feegowFieldName("appointment_date_short")
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build(),
                AppointmentTemplateMapping.builder()
                        .templateName("confirmacao_consulta_v6_itsm")
                        .placeholderIndex(2)
                        .feegowFieldName("appointment_time")
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build(),
                AppointmentTemplateMapping.builder()
                        .templateName("confirmacao_consulta_v6_itsm")
                        .placeholderIndex(3)
                        .feegowFieldName("doctor_name")
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build()
            );
            templateMappingRepository.saveAll(mappings);
        }
    }

    /**
     * Valida que o processamento do lote diário de agendamentos respeita o pacing
     * seguro de 150ms a 300ms introduzido na thread principal de coordenação de ingestão.
     */
    @Test
    public void deveCadenciarEnviosDeLoteComSucessoRespeitandoPacing() {
        // COMENTÁRIO EM PORTUGUÊS (PT-BR):
        // Criação de 50 agendamentos pendentes com números de telefone distintos.
        // Isso força o caso de uso a processá-los individualmente em um loop coordenador.
        // O pacing inserido deve introduzir um delay de 150ms a 300ms a partir do segundo envio.
        List<FeegowAppointment> appointments = new ArrayList<>();
        java.util.concurrent.ConcurrentHashMap<String, FeegowPatient> patients = new java.util.concurrent.ConcurrentHashMap<>();

        for (int i = 1; i <= 50; i++) {
            String apptId = "100" + i;
            String patientId = "200" + i;
            String phone = "55119999900" + (i < 10 ? "0" + i : i);

            appointments.add(new FeegowAppointment(
                apptId, patientId, "123", "Dr. Teste Inovare", "Unidade Central",
                LocalDateTime.now().plusDays(1).withHour(8).withMinute(0), "1", "Consulta", "1"
            ));

            patients.put(patientId, new FeegowPatient(
                patientId, "Paciente " + i, phone, "12345678900", "1990-01-01"
            ));
        }

        // Mock das buscas da Feegow e chamadas do Blip
        when(feegowAppointmentSearcher.searchAppointments(any())).thenReturn(appointments);
        when(feegowPatientDetailsFetcher.fetchPatientDetailsInParallel(any())).thenReturn(patients);
        when(blipLIMEClient.executeMessage(any(), any())).thenReturn(Map.of("status", "success"));
        when(blipLIMEClient.executeCommand(any(), any())).thenReturn(Map.of("status", "success"));
        when(blipLIMEClient.normalizeUserIdentity(any())).thenAnswer(invocation -> {
            String arg = invocation.getArgument(0);
            return arg == null ? "" : arg + "@wa.gw.msging.net";
        });

        // Medição do tempo de processamento
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        IngestAppointmentsUseCase.IngestionSummary summary = ingestAppointmentsUseCase.execute();

        stopWatch.stop();
        long elapsedMillis = stopWatch.getTotalTimeMillis();

        // COMENTÁRIO EM PORTUGUÊS (PT-BR):
        // São 50 agendamentos. A lógica de delay controlado é aplicada a partir da segunda iteração.
        // Portanto, teremos exatamente 49 delays.
        // O delay mínimo teórico é de 49 * 150ms = 7350ms (7.35 segundos).
        // Validamos que o tempo de execução total foi de pelo menos 7 segundos.
        System.out.println("Tempo total de execução do lote (E2E): " + elapsedMillis + " ms");
        
        // Verifica se todas as sessões e mensagens foram criadas e despachadas
        assertEquals(50, summary.sessionsCreated(), "Deveria criar 50 sessões locais");
        assertEquals(50, summary.messagesSent(), "Deveria enviar 50 mensagens ativas");
        
        // Assert de pacing de tempo (utilizando 7 segundos como margem aceitável e segura)
        assertTrue(elapsedMillis >= 7000, "O pacing falhou. Tempo de processamento (" + elapsedMillis + " ms) abaixo do limite mínimo de pacing (7000 ms)");
        
        // Garante que o Mockito interceptou os despachos de mensagens ao Blip
        verify(blipLIMEClient, atLeastOnce()).executeMessage(any(), any());
    }
}
