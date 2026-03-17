package br.dev.ctrls.inovareti.domain.admin;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.function.Consumer;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import br.dev.ctrls.inovareti.domain.financeiro.PaymentPollingJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/admin/financeiro")
@RequiredArgsConstructor
@Slf4j
public class ReceiptReprocessController {

    private static final int MAX_RANGE_DAYS = 45;

    private final PaymentPollingJob paymentPollingJob;

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping(value = "/reprocess/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter reprocessStream(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        if (from == null || to == null || !from.isBefore(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Parâmetros inválidos: 'from' deve ser anterior a 'to'.");
        }

        if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Janela máxima permitida é de " + MAX_RANGE_DAYS + " dias.");
        }

        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt = to.atTime(23, 59, 59);

        SseEmitter emitter = new SseEmitter(300_000L);

        Thread.ofVirtual().start(() -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("start")
                        .data("Iniciando reprocessamento de " + from + " até " + to));

                Consumer<String> progressCallback = progressMessage -> {
                    try {
                        emitter.send(SseEmitter.event()
                                .name("progress")
                                .data(progressMessage));
                    } catch (IOException ignored) {
                    }
                };

                PaymentPollingJob.PollingProcessingResult result =
                        paymentPollingJob.reprocessWindow(fromDt, toDt, progressCallback);

                emitter.send(SseEmitter.event()
                        .name("result")
                        .data(Map.of(
                                "totalFetched", result.totalFetched(),
                                "totalProcessed", result.totalProcessed(),
                                "skippedAlreadyProcessed", result.skippedAlreadyProcessed(),
                                "failures", result.failures(),
                                "processedParcelIds", result.processedParcelIds())));

                emitter.send(SseEmitter.event().name("done").data("Concluído."));
                emitter.complete();
            } catch (IllegalStateException ex) {
                sendError(emitter, "ContaAzul não autorizado: " + ex.getMessage());
            } catch (IllegalArgumentException ex) {
                sendError(emitter, "Parâmetros inválidos: " + ex.getMessage());
            } catch (IOException ex) {
                log.error("Erro no reprocessamento SSE.", ex);
                sendError(emitter, "Erro interno: " + ex.getMessage());
            }
        });

        return emitter;
    }

    private void sendError(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event().name("error").data(message));
            emitter.complete();
        } catch (IOException ignored) {
            emitter.completeWithError(new RuntimeException(message));
        }
    }
}
