package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.input.rest;

import io.micrometer.observation.annotation.Observed;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.annotation.JsonProperty;

import br.dev.ctrls.inovareti.modules.appointment.application.service.AppointmentEnrichmentService;
import br.dev.ctrls.inovareti.modules.appointment.application.service.BlipWebhookInboundService;
import br.dev.ctrls.inovareti.modules.appointment.application.usecase.HandleBlipWebhookUseCase;
import br.dev.ctrls.inovareti.modules.appointment.application.usecase.IngestAppointmentsUseCase;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentDoctorMapping;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.client.BlipLIMEClient;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.AppointmentMotorProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Controlador de entrada REST responsÃ¡vel por expor as rotas HTTP do Motor de Agendamentos.
 * Todas as lÃ³gicas de enriquecimento e cruzamento de dados foram delegadas ao {@link AppointmentEnrichmentService}.
 */
@Slf4j
@RestController
@RequestMapping("/v1/appointments")
@RequiredArgsConstructor
@Observed
public class AppointmentMotorController {

    private final BlipWebhookInboundService blipWebhookInboundService;
    private final AppointmentMotorProperties appointmentMotorProperties;
    private final IngestAppointmentsUseCase ingestAppointmentsUseCase;
    private final HandleBlipWebhookUseCase handleBlipWebhookUseCase;
    private final BlipLIMEClient blipLIMEClient;
    private final AppointmentEnrichmentService appointmentEnrichmentService;

    @GetMapping("/motor-config")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> motorConfig() {
        String mode = appointmentMotorProperties.isTestMode() ? "TEST" : "PROD";
        return ResponseEntity.ok(Map.of(
                "enabled", appointmentMotorProperties.isEnabled(),
                "testMode", appointmentMotorProperties.isTestMode(),
                "testDoctorId", appointmentMotorProperties.getTestDoctorId(),
                "mode", mode));
    }

    @PostMapping("/trigger-manual")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> triggerManual() {
        IngestAppointmentsUseCase.IngestionSummary summary = ingestAppointmentsUseCase.execute();

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "messages_sent", summary.messagesSent(),
                "mode", summary.mode()));
    }

    @GetMapping("/admin/mappings")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AppointmentDoctorMapping>> getMappings() {
        try {
            List<AppointmentDoctorMapping> mappings = appointmentEnrichmentService.getMappings();
            return ResponseEntity.ok(mappings);
        } catch (Exception ex) {
            log.error("Erro inesperado ao listar mappings no controlador: {}", ex.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/admin/debug-queues")
    public ResponseEntity<Map<String, Object>> debugQueues() {
        return ResponseEntity.ok(blipLIMEClient.getBlipQueues());
    }

    @GetMapping("/professionals")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Map<String, String>>> professionals() {
        try {
            List<Map<String, String>> out = appointmentEnrichmentService.getProfessionals();
            return ResponseEntity.ok(out);
        } catch (RestClientResponseException ex) {
            return ResponseEntity.status(ex.getStatusCode()).build();
        } catch (Exception ex) {
            log.error("Erro inesperado ao obter profissionais no controlador: {}", ex.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/blip/queues")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Map<String, String>>> blipQueues() {
        List<BlipLIMEClient.BlipQueue> queues = blipLIMEClient.listBlipQueues();
        List<Map<String, String>> out = queues.stream()
                .map(q -> Map.of("id", q.id(), "name", q.name()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(out);
    }

    @PatchMapping("/admin/sync-mappings")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> syncMappings(
            @RequestBody(required = false) List<SyncMappingRequest> payload) {
        log.info("syncMappings chamado no controlador com payload: {}", payload);

        try {
            Map<String, Object> result = appointmentEnrichmentService.syncMappings(payload);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "reason", ex.getMessage()));
        } catch (Exception ex) {
            log.error("Erro inesperado ao sincronizar mappings no controlador: {}", ex.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @PatchMapping("/admin/doctor-mapping")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> upsertDoctorMapping(@RequestBody DoctorMappingUpsert payload) {
        log.info("upsertDoctorMapping chamado no controlador com payload: {}", payload);

        try {
            Map<String, Object> result = appointmentEnrichmentService.upsertDoctorMapping(payload);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "reason", ex.getMessage()));
        } catch (Exception ex) {
            log.error("Erro inesperado ao realizar upsert de mapping no controlador: {}", ex.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping("/mapping/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteMapping(@PathVariable("id") String id) {
        try {
            boolean deleted = appointmentEnrichmentService.deleteMapping(id);
            if (!deleted) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        } catch (Exception ex) {
            log.error("Erro ao deletar mapeamento {} no controlador: {}", id, ex.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping(value = "/blip/webhook", consumes = {
            MediaType.APPLICATION_JSON_VALUE,
            "application/vnd.lime.select+json",
            "application/vnd.lime.reply+json"
    })
    public ResponseEntity<Map<String, Object>> blipWebhook(
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-Inovare-Token", required = false) String inovareToken,
            @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> payload = body != null ? body : Map.of();
        log.debug("RECEIVED IN WEBHOOK: {}", payload);

        if (payload.isEmpty()) {
            log.warn("Webhook Blip recebido sem body em /v1/appointments/blip/webhook.");
            return ResponseEntity.ok(Map.of(
                    "status", "ignored",
                    "reason", "body-empty"));
        }

        BlipWebhookInboundService.ParsedInbound parsed = blipWebhookInboundService.parse(payload);

        Map<String, Object> metadata = extractMetadata(payload);

        handleBlipWebhookUseCase.execute(new HandleBlipWebhookUseCase.BlipWebhookPayload(
                parsed.messageId(),
                parsed.appointmentId(),
                parsed.action(),
                parsed.from(),
                inovareToken,
                parsed.content(),
                metadata));

        return ResponseEntity.ok(Map.of("status", "processed"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractMetadata(Map<String, Object> payload) {
        if (payload == null) {
            return Map.of();
        }
        Object metadataObj = payload.get("metadata");
        if (metadataObj == null) {
            Object messageObj = payload.get("message");
            if (messageObj instanceof Map<?, ?> msgMap) {
                metadataObj = msgMap.get("metadata");
            }
        }
        if (metadataObj == null) {
            Object resourceObj = payload.get("resource");
            if (resourceObj instanceof Map<?, ?> resMap) {
                metadataObj = resMap.get("metadata");
                if (metadataObj == null) {
                    Object innerMsg = resMap.get("message");
                    if (innerMsg instanceof Map<?, ?> innerMsgMap) {
                        metadataObj = innerMsgMap.get("metadata");
                    }
                }
            }
        }
        if (metadataObj instanceof Map<?, ?> metaMap) {
            return (Map<String, Object>) metaMap;
        }
        return Map.of();
    }

    public record SyncMappingRequest(
        @JsonProperty("profissionalId") String profissionalId,
        @JsonProperty("blipQueueId") String blipQueueId,
        @JsonProperty("itsmUserId") String itsmUserId,
        @JsonProperty("externalWaLink") String externalWaLink,
        @JsonProperty("externalLink") String externalLink,
        @JsonProperty("profissionalNome") String profissionalNome,
        @JsonProperty("discordWebhookUrl") String discordWebhookUrl,
        @JsonProperty("isExternal") Boolean isExternal,
        @JsonProperty("ignoreAutoSchedule") Boolean ignoreAutoSchedule) {
    }

    public record DoctorMappingUpsert(
        @JsonProperty("profissionalId") String profissionalId,
        @JsonProperty("profissionalNome") String profissionalNome,
        @JsonProperty("blipQueueId") String blipQueueId,
        @JsonProperty("itsmUserId") String itsmUserId,
        @JsonProperty("discordWebhookUrl") String discordWebhookUrl,
        @JsonProperty("externalWaLink") String externalWaLink,
        @JsonProperty("isExternal") Boolean isExternal,
        @JsonProperty("ignoreAutoSchedule") Boolean ignoreAutoSchedule) {
    }
}


