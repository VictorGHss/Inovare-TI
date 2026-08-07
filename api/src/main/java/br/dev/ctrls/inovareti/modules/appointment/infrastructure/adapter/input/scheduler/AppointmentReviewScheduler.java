package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.input.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import br.dev.ctrls.inovareti.modules.appointment.application.service.SendPostAppointmentReviewUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Agendador responsável por disparar periodicamente o UseCase de envio de pesquisas de avaliação
 * do Google Review para pacientes com consultas em status Atendido (StatusID=3) no dia.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AppointmentReviewScheduler {

    private final SendPostAppointmentReviewUseCase sendPostAppointmentReviewUseCase;

    @Scheduled(cron = "${inovareti.review.cron:0 0/30 8-20 * * *}")
    public void runPostAppointmentReviewJob() {
        log.info("[SCHEDULER] Disparando rotina periódica de envio de avaliações Google Review...");
        try {
            int sent = sendPostAppointmentReviewUseCase.execute();
            log.info("[SCHEDULER] Rotina de avaliação Google Review finalizada. Total enviadas: {}", sent);
        } catch (Exception ex) {
            log.error("[SCHEDULER] Erro na execução da rotina de avaliação Google Review: {}", ex.getMessage(), ex);
        }
    }
}
