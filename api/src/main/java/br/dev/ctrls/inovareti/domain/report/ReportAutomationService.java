package br.dev.ctrls.inovareti.domain.report;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import br.dev.ctrls.inovareti.domain.notification.ReportDeliveryService;
import br.dev.ctrls.inovareti.domain.notification.discord.DiscordWebhookService;
import br.dev.ctrls.inovareti.domain.ticket.Ticket;
import br.dev.ctrls.inovareti.domain.ticket.TicketRepository;
import br.dev.ctrls.inovareti.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportAutomationService {

    private final ReportScheduleRepository scheduleRepository;
    private final ReportService reportService;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final ReportDeliveryService reportDeliveryService;
    private final DiscordWebhookService discordWebhookService;

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
                    List<Ticket> tickets = ticketRepository.findAllWithRelations().stream()
                            .filter(t -> t.getClosedAt() != null && (t.getClosedAt().isEqual(start) || t.getClosedAt().isAfter(start)) && (t.getClosedAt().isBefore(end) || t.getClosedAt().isEqual(end)))
                            .toList();

                    ByteArrayInputStream stream = reportService.exportInventoryExitsToExcel(tickets);
                    byte[] bytes;
                    try {
                        bytes = stream.readAllBytes();
                    } catch (IOException ioe) {
                        log.error("Failed to read report stream for schedule {}", schedule.getId(), ioe);
                        continue;
                    }

                    String filename = String.format("saidas_estoque_%s_to_%s.xlsx", startDate.toString(), endDate.toString());

                    if (schedule.isSendEmail() && schedule.getTargetUserId() != null) {
                        userRepository.findById(schedule.getTargetUserId()).ifPresentOrElse(user -> {
                            String subject = "Relatório Mensal de Saídas de Estoque";
                            String body = String.format("Olá %s,\n\nEm anexo o relatório automático de saídas de estoque referente ao período %s até %s.\n\nAtt,\nInovare TI",
                                    user.getName(), startDate.toString(), endDate.toString());

                            try {
                                reportDeliveryService.sendReportEmail(user.getName(), user.getEmail(), subject, body, bytes, filename,
                                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                            } catch (Exception ex) {
                                log.error("Failed to send scheduled report email to {} for schedule {}", user.getEmail(), schedule.getId(), ex);
                            }
                        }, () -> log.warn("Target user {} not found for schedule {}", schedule.getTargetUserId(), schedule.getId()));
                    }

                    if (schedule.isSendDiscord()) {
                        String title = "Relatório Mensal de Saídas Gerado";
                        String message = String.format("Relatório automático de saídas gerado para período %s → %s. Arquivo: %s",
                                startDate.toString(), endDate.toString(), filename);
                        try {
                            discordWebhookService.sendOperationalAlert(title, message);
                        } catch (Exception ex) {
                            log.error("Failed to notify Discord for schedule {}", schedule.getId(), ex);
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
}
