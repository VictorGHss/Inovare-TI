package br.dev.ctrls.inovareti.modules.report.application.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import br.dev.ctrls.inovareti.modules.notification.application.service.ReportDeliveryService;
import br.dev.ctrls.inovareti.modules.notification.infrastructure.adapter.output.discord.DiscordWebhookService;
import br.dev.ctrls.inovareti.modules.notification.infrastructure.adapter.output.discord.bot.DiscordDirectMessageService;
import br.dev.ctrls.inovareti.modules.report.domain.model.ReportSchedule;
import br.dev.ctrls.inovareti.modules.report.domain.port.output.ReportScheduleRepositoryPort;
import br.dev.ctrls.inovareti.modules.user.domain.port.output.UserRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço de automação e agendamento de relatórios.
 * Executa tarefas assíncronas utilizando Virtual Threads do Java 21.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportAutomationService {

    private final ReportScheduleRepositoryPort scheduleRepository;
    private final ReportService reportService;
    private final UserRepositoryPort userRepository;
    private final ReportDeliveryService reportDeliveryService;
    private final DiscordWebhookService discordWebhookService;
    private final DiscordDirectMessageService discordDirectMessageService;

    /**
     * Executa no dia configurado (ex.: dia 12) às 08:00 todo mês.
     * Utiliza Virtual Threads para processamento concorrente e não-bloqueante das rotinas.
     */
    @Scheduled(cron = "0 0 8 12 * *")
    public void runScheduledReports() {
        log.info("Iniciando rotina agendada de relatórios utilizando Virtual Threads");

        List<ReportSchedule> schedules = scheduleRepository.findByIsActiveTrue();
        LocalDate today = LocalDate.now();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (ReportSchedule schedule : schedules) {
                executor.submit(() -> {
                    try {
                        processSchedule(schedule, today);
                    } catch (Exception ex) {
                        log.error("Erro ao processar agendamento {} na Virtual Thread", schedule.getId(), ex);
                    }
                });
            }
        }

        log.info("Todas as tarefas de relatórios agendados foram submetidas");
    }

    /**
     * Executa o processamento individual de um agendamento.
     */
    private void processSchedule(ReportSchedule schedule, LocalDate today) {
        try {
            int day = Math.max(1, schedule.getScheduleDay());

            LocalDate startDate;
            if (today.getDayOfMonth() >= day) {
                startDate = LocalDate.of(today.getYear(), today.getMonth(), day);
            } else {
                LocalDate prev = today.minusMonths(1);
                startDate = LocalDate.of(prev.getYear(), prev.getMonth(), day);
            }

            LocalDate endDate = today;
            LocalDateTime start = startDate.atStartOfDay();
            LocalDateTime end = endDate.atTime(LocalTime.MAX);

            log.info("Processando agendamento {} para reportType={} período={} -> {}",
                    schedule.getId(), schedule.getReportType(), startDate, endDate);

            if ("exits".equalsIgnoreCase(schedule.getReportType())) {
                byte[] bytes = reportService.exportInventoryExitsToPdf(start, end);
                String filename = String.format("saidas_estoque_%s_to_%s.pdf", startDate.toString(), endDate.toString());

                if (schedule.isSendEmail() && schedule.getTargetUserId() != null) {
                    userRepository.findById(schedule.getTargetUserId()).ifPresentOrElse(user -> {
                        String subject = "Relatório Mensal de Saídas de Estoque";
                        String body = String.format("Olá %s,\n\nEm anexo o relatório automático de saídas de estoque referente ao período %s até %s.\n\nAtt,\nInovare TI",
                                user.getName(), startDate.toString(), endDate.toString());

                        try {
                            reportDeliveryService.sendReportEmail(user.getName(), user.getEmail(), subject, body, bytes, filename,
                                    "application/pdf");
                        } catch (Exception ex) {
                            log.error("Falha ao enviar e-mail de relatório agendado para {} no agendamento {}", user.getEmail(), schedule.getId(), ex);
                        }
                    }, () -> log.warn("Usuário destino {} não encontrado para agendamento {}", schedule.getTargetUserId(), schedule.getId()));
                }

                if (schedule.isSendDiscord()) {
                    String title = "Relatório Mensal de Saídas Gerado";
                    String message = String.format("Relatório automático de saídas gerado para período %s → %s.",
                            startDate.toString(), endDate.toString());

                    if (schedule.getTargetUserId() != null) {
                        userRepository.findById(schedule.getTargetUserId()).ifPresentOrElse(user -> {
                            if (user.getDiscordUserId() != null && !user.getDiscordUserId().isBlank()) {
                                discordDirectMessageService.sendReportPdfDMToUser(
                                        user.getDiscordUserId(),
                                        bytes,
                                        filename,
                                        String.format("Olá %s, segue o relatório automático de saídas referente ao período %s → %s.",
                                                user.getName(), startDate.toString(), endDate.toString()),
                                        () -> discordWebhookService.sendOperationalAlert(title, message + " Arquivo: " + filename)
                                );
                            } else {
                                discordWebhookService.sendOperationalAlert(title, message + " Arquivo: " + filename);
                            }
                        }, () -> discordWebhookService.sendOperationalAlert(title, message + " Arquivo: " + filename));
                    } else {
                        try {
                            discordWebhookService.sendOperationalAlert(title, message + " Arquivo: " + filename);
                        } catch (Exception ex) {
                            log.error("Falha ao notificar o Discord no agendamento {}", schedule.getId(), ex);
                        }
                    }
                }

            } else {
                log.warn("Tipo de relatório desconhecido '{}' no agendamento {}", schedule.getReportType(), schedule.getId());
            }

        } catch (Exception ex) {
            log.error("Erro no processamento interno do agendamento {}", schedule.getId(), ex);
        }
    }

    /**
     * Gera e envia imediatamente um relatório (ignora o dia do mês configurado).
     * Usado para disparos manuais de teste.
     */
    public void triggerTestReport(UUID scheduleId) {
        scheduleRepository.findById(scheduleId).ifPresentOrElse(schedule -> {
            try {
                if (!"exits".equalsIgnoreCase(schedule.getReportType())) {
                    log.warn("Disparo manual não suportado para reportType '{}' no agendamento {}", schedule.getReportType(), schedule.getId());
                    return;
                }

                if (schedule.getTargetUserId() == null) {
                    log.warn("Disparo manual solicitado mas agendamento {} não possui usuário destino", schedule.getId());
                    return;
                }

                userRepository.findById(schedule.getTargetUserId()).ifPresentOrElse(user -> {
                    log.info("Iniciando disparo manual de relatório para o usuário {}", user.getEmail());

                    LocalDate today = LocalDate.now();
                    LocalDate startDate = today.minusMonths(1);
                    LocalDate endDate = today;
                    LocalDateTime start = startDate.atStartOfDay();
                    LocalDateTime end = endDate.atTime(LocalTime.MAX);

                    try {
                        byte[] bytes = reportService.exportInventoryExitsToPdf(start, end);
                        String filename = String.format("saidas_estoque_%s_to_%s.pdf", startDate.toString(), endDate.toString());

                        if (schedule.isSendEmail()) {
                            String subject = "Relatório de Teste - Saídas de Estoque";
                            String body = String.format("Olá %s,\n\nSegue o relatório de teste de saídas de estoque referente ao período %s até %s.\n\nAtt,\nInovare TI",
                                    user.getName(), startDate.toString(), endDate.toString());

                            try {
                                reportDeliveryService.sendReportEmail(user.getName(), user.getEmail(), subject, body, bytes, filename,
                                        "application/pdf");
                            } catch (Exception ex) {
                                log.error("Falha ao enviar e-mail de teste para {} no agendamento {}", user.getEmail(), schedule.getId(), ex);
                            }
                        }

                        if (schedule.isSendDiscord()) {
                            String title = "Relatório de Teste de Saídas Gerado";
                            String message = String.format("Relatório manual de saídas gerado para período %s → %s.", startDate.toString(), endDate.toString());

                            if (user.getDiscordUserId() != null && !user.getDiscordUserId().isBlank()) {
                                discordDirectMessageService.sendReportPdfDMToUser(
                                        user.getDiscordUserId(),
                                        bytes,
                                        filename,
                                        String.format("Olá %s, segue o relatório de teste de saídas referente ao período %s → %s.",
                                                user.getName(), startDate.toString(), endDate.toString()),
                                        () -> discordWebhookService.sendOperationalAlert(title, message + " Arquivo: " + filename)
                                );
                            } else {
                                discordWebhookService.sendOperationalAlert(title, message + " Arquivo: " + filename);
                            }
                        }

                    } catch (Exception ex) {
                        log.error("Erro ao gerar relatório manual de teste para agendamento {}", schedule.getId(), ex);
                    }
                }, () -> log.warn("Usuário destino {} não encontrado para agendamento {}", schedule.getTargetUserId(), schedule.getId()));

            } catch (Exception ex) {
                log.error("Erro no processamento do disparo manual do agendamento {}", schedule.getId(), ex);
            }
        }, () -> log.warn("Agendamento não encontrado {}", scheduleId));
    }
}
