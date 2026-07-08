package br.dev.ctrls.inovareti.modules.access.domain.service;

import br.dev.ctrls.inovareti.modules.access.domain.model.AccessCredential;
import br.dev.ctrls.inovareti.modules.access.domain.model.FeegowPatientAccessInfo;
import br.dev.ctrls.inovareti.modules.access.domain.model.UserType;
import br.dev.ctrls.inovareti.modules.access.domain.port.output.AccessCredentialRepositoryPort;
import br.dev.ctrls.inovareti.modules.access.domain.port.output.FeegowClientPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentExternalPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.FeegowAppointment;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.FeegowPatient;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.PatientExternalPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Serviço de domínio AccessService.
 * Gerencia a inteligência de agrupamento de agendamentos por CPF/telefone, cálculo de janelas de tempo,
 * unificação de códigos de acesso e contingência de CPF ausente.
 * Comentários em PT-BR conforme as Regras de Ouro.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccessService {

    private final FeegowClientPort feegowClientPort;
    private final AppointmentExternalPort appointmentExternalPort;
    private final PatientExternalPort patientExternalPort;
    private final AccessCredentialRepositoryPort accessCredentialRepositoryPort;

    /**
     * Processa a requisição de acesso para um determinado agendamento. Realiza agrupamento de consultas,
     * cálculo de janela de abertura de catracas e persistência de credenciais unificadas.
     *
     * @param appointmentId Identificador do agendamento vindo do Blip ou frontend.
     * @param requestCpf CPF opcional passado pela requisição.
     * @return AccessValidationResult contendo os dados de liberação e flags de contingência.
     */
    public AccessValidationResult processAccessRequest(String appointmentId, String requestCpf) {
        log.info("[AccessService] Processando solicitação de acesso físico. Agendamento: {}", appointmentId);

        // 1. Busca os dados cadastrais do agendamento no Feegow
        Optional<FeegowPatientAccessInfo> accessInfoOpt = feegowClientPort.fetchPatientAccessInfo(appointmentId);
        if (accessInfoOpt.isEmpty()) {
            log.warn("[AccessService] Não foi possível obter dados do agendamento {} na API Feegow.", appointmentId);
            return new AccessValidationResult(false, null, null, false, "Agendamento não encontrado.");
        }

        FeegowPatientAccessInfo accessInfo = accessInfoOpt.get();

        // 2. Resolve o CPF (priorizando Feegow, com fallback para o parâmetro recebido)
        String resolvedCpf = accessInfo.cpf();
        if (resolvedCpf == null || resolvedCpf.isBlank()) {
            resolvedCpf = requestCpf;
        }

        // Limpa formatação do CPF para padronizar
        if (resolvedCpf != null) {
            resolvedCpf = resolvedCpf.replaceAll("\\D", "");
        }

        // 3. Contingência de CPF Nulo: se não existir CPF, sinaliza fallback do Blip (requiresCpfFallback = true)
        if (resolvedCpf == null || resolvedCpf.isBlank()) {
            log.warn("[AccessService] Prontuário Feegow e metadados do Blip sem CPF para o paciente ID: {}. Ativando flag 'requiresCpfFallback'.", accessInfo.patientId());
            return new AccessValidationResult(false, null, null, true, "CPF ausente. Necessita redirecionar fluxo Blip.");
        }

        LocalDate resolvedAppointmentDate = accessInfo.appointmentDate();
        if (resolvedAppointmentDate == null) {
            resolvedAppointmentDate = LocalDate.now();
        }
        final LocalDate appointmentDate = resolvedAppointmentDate;

        // Busca prontuário para recuperar o telefone e suportar agrupamentos familiares
        FeegowPatient mainPatient = patientExternalPort.patientInfo(accessInfo.patientId());
        String targetPhone = mainPatient != null ? mainPatient.phone() : null;

        // 4. Busca todos os agendamentos marcados daquela data para agrupamento
        log.info("[AccessService] Buscando pauta do dia {} no Feegow para agrupamento de consultas...", appointmentDate);
        List<FeegowAppointment> dailyAppointments = appointmentExternalPort.searchAppointments(appointmentDate, 1);

        List<FeegowAppointment> matchingAppointments = new ArrayList<>();
        String finalCpf = resolvedCpf;

        // Lógica de Agrupamento: Mesmo CPF ou mesmo grupo familiar mapeado pelo telefone
        for (FeegowAppointment app : dailyAppointments) {
            if (app.patientId().equals(accessInfo.patientId())) {
                matchingAppointments.add(app);
            } else {
                FeegowPatient otherPatient = patientExternalPort.patientInfo(app.patientId());
                if (otherPatient != null) {
                    String otherCpf = otherPatient.cpf() != null ? otherPatient.cpf().replaceAll("\\D", "") : "";
                    boolean cpfMatch = !finalCpf.isBlank() && finalCpf.equals(otherCpf);
                    boolean phoneMatch = targetPhone != null && !targetPhone.isBlank() && targetPhone.equals(otherPatient.phone());
                    
                    if (cpfMatch || phoneMatch) {
                        matchingAppointments.add(app);
                    }
                }
            }
        }

        // Garante que o agendamento atual esteja na lista mesmo em caso de falha de agrupamento
        if (matchingAppointments.isEmpty()) {
            matchingAppointments.add(new FeegowAppointment(
                accessInfo.appointmentId(),
                accessInfo.patientId(),
                accessInfo.doctorId(),
                accessInfo.doctorName(),
                "",
                LocalDateTime.of(appointmentDate, accessInfo.appointmentTime() != null ? accessInfo.appointmentTime() : LocalTime.of(12, 0)),
                "1",
                "",
                "",
                false
            ));
        }

        // 5. Identifica o menor horário de consulta (a primeira do dia)
        LocalTime earliestTime = matchingAppointments.stream()
            .map(app -> app.startAt().toLocalTime())
            .min(java.util.Comparator.naturalOrder())
            .orElse(accessInfo.appointmentTime() != null ? accessInfo.appointmentTime() : LocalTime.of(12, 0));

        // 6. Janela de Abertura: exatamente 2 horas antes da primeira consulta
        LocalTime openingTime = earliestTime.minusHours(2);
        // Janela de Fechamento: fixo rigidamente às 21:00 do mesmo dia
        LocalTime closingTime = LocalTime.of(21, 0);

        log.info("[AccessService] Primeira consulta do dia agendada para: {}. Abertura da catraca: {}. Fechamento: {}.", earliestTime, openingTime, closingTime);

        // 7. Unificação de Credencial: verifica se já gerou uma credencial para este paciente/CPF hoje
        List<AccessCredential> existingCredentials = accessCredentialRepositoryPort.findByCpf(finalCpf);
        Optional<AccessCredential> activeCredOpt = existingCredentials.stream()
            .filter(c -> c.getCreatedAt().toLocalDate().equals(appointmentDate))
            .findFirst();

        String token;
        String locator;

        if (activeCredOpt.isPresent()) {
            // Reutiliza o token e localizador existente para o dia todo
            AccessCredential existing = activeCredOpt.get();
            token = existing.getAccessCredential();
            locator = existing.getLocator();
            log.info("[AccessService] Reutilizando credencial ativa existente para o CPF {}: {}", finalCpf, token);
        } else {
            // Gera um novo código único e localizador
            token = "CRED-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            locator = "LOC-" + System.currentTimeMillis();
            log.info("[AccessService] Gerada nova credencial de acesso unificada: {}", token);
        }

        // 8. Salva o registro no banco mapeando para cada ID de agendamento envolvido
        for (FeegowAppointment app : matchingAppointments) {
            List<AccessCredential> savedList = accessCredentialRepositoryPort.findByAppointmentId(app.id());
            if (savedList.isEmpty()) {
                AccessCredential credential = AccessCredential.builder()
                    .id(UUID.randomUUID())
                    .appointmentId(app.id())
                    .name(accessInfo.name())
                    .cpf(finalCpf)
                    .userType(UserType.PATIENT)
                    .accessCredential(token)
                    .locator(locator)
                    .createdAt(LocalDateTime.now())
                    .build();

                accessCredentialRepositoryPort.save(credential);
                log.info("[AccessService] Credencial associada e salva para o agendamento ID: {}", app.id());
            }
        }

        return new AccessValidationResult(true, accessInfo.name(), token, false, "Credencial resolvida com sucesso.");
    }

    /**
     * Classe de transporte de dados de validação de acesso.
     */
    public record AccessValidationResult(
        boolean authorized,
        String patientName,
        String accessCredential,
        boolean requiresCpfFallback,
        String message
    ) {}
}
