package br.dev.ctrls.inovareti.modules.access.domain.service;

import br.dev.ctrls.inovareti.modules.access.domain.model.InvalidChallengeException;
import br.dev.ctrls.inovareti.modules.access.domain.model.AccessCredential;
import br.dev.ctrls.inovareti.modules.access.domain.model.FeegowPatientAccessInfo;
import br.dev.ctrls.inovareti.modules.access.domain.model.UserType;
import br.dev.ctrls.inovareti.modules.access.domain.model.GerAcessoRequest;
import br.dev.ctrls.inovareti.modules.access.domain.model.GerAcessoResponse;
import br.dev.ctrls.inovareti.modules.access.domain.port.output.AccessCredentialRepositoryPort;
import br.dev.ctrls.inovareti.modules.access.domain.port.output.FeegowClientPort;
import br.dev.ctrls.inovareti.modules.access.domain.port.output.GerAcessoClientPort;
import br.dev.ctrls.inovareti.modules.access.domain.model.CompanionAccessInfo;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Serviço de domínio AccessService.
 * Gerencia a inteligência de agrupamento de agendamentos por CPF/telefone, cálculo de janelas de tempo,
 * cadastro de liberação física de acesso na GerAcesso API e orquestração assíncrona de acompanhantes.
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
    private final GerAcessoClientPort gerAcessoClientPort;

    /**
     * Processa a requisição de acesso para um determinado agendamento. Realiza agrupamento de consultas,
     * cálculo de janela de abertura de catracas, cadastro do Paciente Titular na GerAcesso, persistência
     * local e orquestração paralela (Virtual Threads) e resiliente dos acompanhantes.
     *
     * @param appointmentId Identificador do agendamento vindo do Blip ou frontend.
     * @param requestCpf CPF opcional passado pela requisição.
     * @param companions Lista opcional de acompanhantes contidos no payload.
     * @return AccessValidationResult contendo os dados de liberação e flags de contingência.
     */
    public AccessValidationResult processAccessRequest(String appointmentId, String requestCpf, List<CompanionAccessInfo> companions) {
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
            .filter(c -> c.getCreatedAt().toLocalDate().equals(appointmentDate) && c.getUserType() == UserType.PATIENT)
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
            // Constrói payload de requisição para registrar o Paciente Titular na GerAcesso
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
            String startVisit = LocalDateTime.of(appointmentDate, openingTime).format(formatter);
            String endVisit = LocalDateTime.of(appointmentDate, closingTime).format(formatter);

            GerAcessoRequest titularRequest = GerAcessoRequest.builder()
                .cpf(finalCpf)
                .status(1)
                .name(accessInfo.name())
                .phone(targetPhone != null ? targetPhone : "")
                .email("")
                .visitType(1) // 1 = PACIENTE / TITULAR
                .startVisit(startVisit)
                .endVisit(endVisit)
                .build();

            // Dispara chamada de cadastro do paciente titular na API física da GerAcesso
            log.info("[AccessService] Enviando cadastro do paciente titular {} para a GerAcesso local...", accessInfo.name());
            Optional<GerAcessoResponse> responseOpt = gerAcessoClientPort.registerAccess(titularRequest);

            if (responseOpt.isPresent() && responseOpt.get().credential() != null) {
                token = responseOpt.get().credential();
                locator = responseOpt.get().locator();
                log.info("[AccessService] Cadastro concluído na GerAcesso. Token={}, Locator={}", token, locator);
            } else {
                // Fallback: se falhar a conexão local, gera credencial interna para contingência e recepção manual
                token = "CRED-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                locator = "LOC-" + System.currentTimeMillis();
                log.warn("[AccessService] Falha na GerAcesso. Utilizando credencial local de contingência.");
            }
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

        // 9. Orquestração de Acompanhantes: loop paralelo via Java 21 Virtual Threads
        if (companions != null && !companions.isEmpty()) {
            log.info("[AccessService] Iniciando cadastro paralelo de {} acompanhante(s) via Virtual Threads...", companions.size());
            final String finalToken = token;
            final String finalLocator = locator;

            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                List<Future<Void>> futures = companions.stream()
                    .map(companion -> executor.submit(() -> {
                        // Isolamento e resiliência (Fail-Safe) por tarefa
                        try {
                            registerCompanionAccess(companion, appointmentDate, openingTime, closingTime, finalToken, finalLocator, accessInfo.appointmentId());
                        } catch (Exception ex) {
                            log.error("[AccessService] Erro fatal no processamento assíncrono do acompanhante '{}': {}", 
                                    companion.name(), ex.getMessage(), ex);
                        }
                        return (Void) null;
                    }))
                    .toList();

                // Aguarda a execução de todas as tarefas de cadastro
                for (Future<Void> future : futures) {
                    try {
                        future.get();
                    } catch (Exception e) {
                        log.error("[AccessService] Falha ao recuperar resultado da Virtual Thread do acompanhante", e);
                    }
                }
            }
            log.info("[AccessService] Processamento assíncrono de acompanhantes finalizado.");
        }

        return new AccessValidationResult(true, accessInfo.name(), token, false, "Credencial resolvida com sucesso.");
    }

    /**
     * Efetua o cadastro individual e isolado (fail-safe) do acompanhante na GerAcesso e no banco local.
     */
    private void registerCompanionAccess(
            CompanionAccessInfo companion,
            LocalDate date,
            LocalTime openingTime,
            LocalTime closingTime,
            String patientToken,
            String patientLocator,
            String appointmentId) {

        String companionCpf = companion.cpf() != null ? companion.cpf().replaceAll("\\D", "") : "";
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        String startVisit = LocalDateTime.of(date, openingTime).format(formatter);
        String endVisit = LocalDateTime.of(date, closingTime).format(formatter);

        GerAcessoRequest request = GerAcessoRequest.builder()
            .cpf(companionCpf)
            .status(1)
            .name(companion.name())
            .phone(companion.phone() != null ? companion.phone() : "")
            .email(companion.email() != null ? companion.email() : "")
            .visitType(2) // 2 = ACOMPANHANTE / COMPANION
            .startVisit(startVisit)
            .endVisit(endVisit)
            .build();

        log.info("[AccessService] Cadastrando acompanhante {} na GerAcesso...", companion.name());
        Optional<GerAcessoResponse> responseOpt = gerAcessoClientPort.registerAccess(request);

        String companionToken;
        String companionLocator;

        if (responseOpt.isPresent() && responseOpt.get().credential() != null) {
            companionToken = responseOpt.get().credential();
            companionLocator = responseOpt.get().locator();
            log.info("[AccessService] Acompanhante {} cadastrado com sucesso. Credencial: {}", companion.name(), companionToken);
        } else {
            // Fallback: gera credencial local para contingência e evita travar fluxo
            companionToken = "CRED-COMP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            companionLocator = "LOC-COMP-" + System.currentTimeMillis();
            log.warn("[AccessService] Falha no cadastro do acompanhante {} na GerAcesso. Gerada credencial local de contingência.", companion.name());
        }

        // Persiste a credencial individual e separada no banco local
        AccessCredential credential = AccessCredential.builder()
            .id(UUID.randomUUID())
            .appointmentId(appointmentId)
            .name(companion.name())
            .cpf(companionCpf.isEmpty() ? null : companionCpf)
            .userType(UserType.COMPANION)
            .accessCredential(companionToken)
            .locator(companionLocator)
            .createdAt(LocalDateTime.now())
            .build();

        accessCredentialRepositoryPort.save(credential);
        log.info("[AccessService] Credencial do acompanhante {} salva no banco local com sucesso.", companion.name());
    }

    /**
     * Valida o desafio dos 4 últimos dígitos do telefone do paciente cadastrado no prontuário.
     * Caso os dígitos não batam, lança InvalidChallengeException.
     * Comentários em PT-BR pelas Regras de Ouro.
     *
     * @param appointmentId Identificador do agendamento.
     * @param phoneDigits Os 4 dígitos enviados para validação.
     */
    public void validatePhoneChallenge(String appointmentId, String phoneDigits) {
        log.info("[AccessService] Validando desafio de telefone para o agendamento ID: {}", appointmentId);
        
        Optional<FeegowPatientAccessInfo> accessInfoOpt = feegowClientPort.fetchPatientAccessInfo(appointmentId);
        if (accessInfoOpt.isEmpty()) {
            log.warn("[AccessService] Agendamento {} não encontrado para validação do desafio.", appointmentId);
            throw new br.dev.ctrls.inovareti.core.shared.domain.model.exception.NotFoundException("Agendamento não encontrado.");
        }

        FeegowPatientAccessInfo accessInfo = accessInfoOpt.get();
        FeegowPatient mainPatient = patientExternalPort.patientInfo(accessInfo.patientId());
        
        String phone = mainPatient != null ? mainPatient.phone() : null;
        if (phone == null || phone.isBlank()) {
            log.warn("[AccessService] Paciente ID {} não possui telefone cadastrado no prontuário.", accessInfo.patientId());
            throw new InvalidChallengeException("Não há telefone cadastrado no prontuário do paciente.");
        }

        // Robustez: Mantém apenas números puros do telefone cadastrado
        String cleanPhone = phone.replaceAll("\\D", "");
        if (cleanPhone.length() < 4) {
            log.warn("[AccessService] Telefone cadastrado '{}' (limpo: '{}') possui menos de 4 dígitos.", phone, cleanPhone);
            throw new InvalidChallengeException("Telefone inválido cadastrado no prontuário.");
        }

        // Extrai os 4 últimos dígitos do telefone limpo
        String lastFourDigits = cleanPhone.substring(cleanPhone.length() - 4);
        
        // Limpa os dígitos enviados pelo usuário para comparação
        String cleanInputDigits = phoneDigits != null ? phoneDigits.replaceAll("\\D", "") : "";

        if (!lastFourDigits.equals(cleanInputDigits)) {
            log.warn("[AccessService] Falha no desafio de segurança para agendamento {}. Esperado: {}, Recebido: {}", 
                    appointmentId, lastFourDigits, cleanInputDigits);
            throw new InvalidChallengeException("Os dígitos informados estão incorretos. Por favor, tente novamente.");
        }
        
        log.info("[AccessService] Desafio de segurança validado com sucesso para agendamento {}", appointmentId);
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
