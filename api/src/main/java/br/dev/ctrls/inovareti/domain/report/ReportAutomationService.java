package br.dev.ctrls.inovareti.domain.report;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import br.dev.ctrls.inovareti.modules.notification.application.service.ReportDeliveryService;
import br.dev.ctrls.inovareti.modules.notification.infrastructure.adapter.output.discord.DiscordWebhookService;
import br.dev.ctrls.inovareti.modules.notification.infrastructure.adapter.output.discord.bot.DiscordDirectMessageService;
import br.dev.ctrls.inovareti.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportAutomationService {

    private final ReportScheduleRepository scheduleRepository;
    private final ReportService reportService;
    private final UserRepository userRepository;
    private final ReportDeliveryService reportDeliveryService;
    private final DiscordWebhookService discordWebhookService;
    private final DiscordDirectMessageService discordDirectMessageService;

    /**
     * Executa no dia configurado (ex.: dia 12) às 08:00 todo mês.
     * A expressão cron pode ser ajustada conforme necessidade.
     */
    @Scheduled(cron = "0 0 8 12 * *")
    public void runScheduledReports() {
        log.info("Starting scheduled reports job");

        List<ReportSchedule> schedules = scheduleRepository.findByIsActiveTrue();
        LocalDate today = LocalDate.now();

        for (ReportSchedule schedule : schedules) {
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

                log.info("Processing schedule {} for reportType={} period={} -> {}",
                        schedule.getId(), schedule.getReportType(), startDate, endDate);

                if ("exits".equalsIgnoreCase(schedule.getReportType())) {
                    // generate PDF bytes
                    ByteArrayInputStream stream = reportService.exportInventoryExitsToPdf(start, end);
                    byte[] bytes = stream.readAllBytes();

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
                                log.error("Failed to send scheduled report email to {} for schedule {}", user.getEmail(), schedule.getId(), ex);
                            }
                        }, () -> log.warn("Target user {} not found for schedule {}", schedule.getTargetUserId(), schedule.getId()));
                    }

                    if (schedule.isSendDiscord()) {
                        String title = "Relatório Mensal de Saídas Gerado";
                        String message = String.format("Relatório automático de saídas gerado para período %s → %s.",
                                startDate.toString(), endDate.toString());

                        if (schedule.getTargetUserId() != null) {
                            userRepository.findById(schedule.getTargetUserId()).ifPresentOrElse(user -> {
                                if (user.getDiscordUserId() != null && !user.getDiscordUserId().isBlank()) {
                                    // Envia via canal direto (DM) com contingência automática para o webhook em caso de falha assíncrona
                                    discordDirectMessageService.sendReportPdfDMToUser(
                                            user.getDiscordUserId(),
                                            bytes,
                                            filename,
                                            String.format("Olá %s, segue o relatório automático de saídas referente ao período %s → %s.",
                                                    user.getName(), startDate.toString(), endDate.toString()),
                                            () -> discordWebhookService.sendOperationalAlert(title, message + " Arquivo: " + filename)
                                    );
                                } else {
                                    // Fallback para o canal operacional de webhook se o usuário não possui ID do Discord vinculado
                                    discordWebhookService.sendOperationalAlert(title, message + " Arquivo: " + filename);
                                }
                            }, () -> {
                                // Usuário não localizado: fallback para canal operacional
                                discordWebhookService.sendOperationalAlert(title, message + " Arquivo: " + filename);
                            });
                        } else {
                            // no specific user, send to operational webhook
                            try {
                                discordWebhookService.sendOperationalAlert(title, message + " Arquivo: " + filename);
                            } catch (Exception ex) {
                                log.error("Failed to notify Discord for schedule {}", schedule.getId(), ex);
                            }
                        }
                    }

                } else {
                    log.warn("Unknown report type '{}' in schedule {}", schedule.getReportType(), schedule.getId());
                }

            } catch (Exception ex) {
                log.error("Error processing schedule {}", schedule.getId(), ex);
            }
        }

        log.info("Scheduled reports job finished");
    }

    /**
     * Gera e envia imediatamente um relatório (ignora o dia do mês configurado).
     * Usado para disparos manuais de teste.
     */
    public void triggerTestReport(UUID scheduleId) {
        scheduleRepository.findById(scheduleId).ifPresentOrElse(schedule -> {
            try {
                if (!"exits".equalsIgnoreCase(schedule.getReportType())) {
                    log.warn("Manual trigger for unsupported reportType '{}' schedule {}", schedule.getReportType(), schedule.getId());
                    return;
                }

                if (schedule.getTargetUserId() == null) {
                    log.warn("Manual trigger requested but schedule {} has no target user", schedule.getId());
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
                        ByteArrayInputStream stream = reportService.exportInventoryExitsToPdf(start, end);
                        byte[] bytes = stream.readAllBytes();
                        String filename = String.format("saidas_estoque_%s_to_%s.pdf", startDate.toString(), endDate.toString());

                        if (schedule.isSendEmail()) {
                            String subject = "Relatório de Teste - Saídas de Estoque";
                            String body = String.format("Olá %s,\n\nSegue o relatório de teste de saídas de estoque referente ao período %s até %s.\n\nAtt,\nInovare TI",
                                    user.getName(), startDate.toString(), endDate.toString());

                            try {
                                reportDeliveryService.sendReportEmail(user.getName(), user.getEmail(), subject, body, bytes, filename,
                                        "application/pdf");
                            } catch (Exception ex) {
                                log.error("Failed to send test report email to {} for schedule {}", user.getEmail(), schedule.getId(), ex);
                            }
                        }

                        if (schedule.isSendDiscord()) {
                            String title = "Relatório de Teste de Saídas Gerado";
                            String message = String.format("Relatório manual de saídas gerado para período %s → %s.", startDate.toString(), endDate.toString());

                            if (user.getDiscordUserId() != null && !user.getDiscordUserId().isBlank()) {
                                // Envia via canal direto (DM) com contingência automática para o webhook em caso de falha assíncrona
                                discordDirectMessageService.sendReportPdfDMToUser(
                                        user.getDiscordUserId(),
                                        bytes,
                                        filename,
                                        String.format("Olá %s, segue o relatório de teste de saídas referente ao período %s → %s.",
                                                user.getName(), startDate.toString(), endDate.toString()),
                                        () -> discordWebhookService.sendOperationalAlert(title, message + " Arquivo: " + filename)
                                );
                            } else {
                                // Fallback para o canal operacional de webhook se o usuário não possui ID do Discord vinculado
                                discordWebhookService.sendOperationalAlert(title, message + " Arquivo: " + filename);
                            }
                        }

                    } catch (Exception ex) {
                        log.error("Error triggering manual report for schedule {}", schedule.getId(), ex);
                    }
                }, () -> log.warn("Target user {} not found for schedule {}", schedule.getTargetUserId(), schedule.getId()));

            } catch (Exception ex) {
                log.error("Error processing manual trigger for schedule {}", schedule.getId(), ex);
            }
        }, () -> log.warn("Schedule not found {}", scheduleId));
    }
}
