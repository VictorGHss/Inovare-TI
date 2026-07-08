package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.input.rest;

import io.micrometer.observation.annotation.Observed;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
import com.fasterxml.jackson.databind.ObjectMapper;
import br.dev.ctrls.inovareti.config.security.WebhookSignatureValidator;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import java.nio.charset.StandardCharsets;

/**
 * Controlador de entrada REST responsável por expor as rotas HTTP do Motor de Agendamentos.
 * Todas as lógicas de enriquecimento e cruzamento de dados foram delegadas ao {@link AppointmentEnrichmentService}.
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
    private final WebhookSignatureValidator webhookSignatureValidator;
    private final Environment env;
    private final ObjectMapper objectMapper;

    @Value("${blip.webhook.secret}")
    private String blipWebhookSecret;

    @Value("${blip.webhook.token:}")
    private String blipWebhookToken;

    @GetMapping("/motor-config")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> motorConfig() {
        String mode = appointmentMotorProperties.isTestMode() ? "TEST" : "PROD";
        return ResponseEntity.ok(Map.of(
                "enabled", appointmentMotorProperties.isEnabled(),
                "testMode", appointmentMotorProperties.isTestMode(),
                "testDoctorId", appointmentMotorProperties.getTestDoctorId() != null ? appointmentMotorProperties.getTestDoctorId() : "",
                "testDoctorIds", appointmentMotorProperties.getTestDoctorIds() != null ? appointmentMotorProperties.getTestDoctorIds() : java.util.List.of(),
                "activeDoctorIds", appointmentMotorProperties.getActiveDoctorIds() != null ? appointmentMotorProperties.getActiveDoctorIds() : java.util.List.of(),
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

    @PostMapping(value = "/blip/webhook")
    public ResponseEntity<Map<String, Object>> blipWebhook(
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-Inovare-Token", required = false) String inovareToken,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-Blip-Signature", required = false) String blipSignature,
            jakarta.servlet.http.HttpServletRequest request,
            @RequestBody(required = false) String rawJson) {
        log.debug("RECEIVED IN WEBHOOK rawJson: {}", rawJson);

        // 1. VALIDAÇÃO DE ASSINATURA CRIPTOGRÁFICA (HMAC-SHA256) E TOKENS DE SEGURANÇA IMEDIATA
        byte[] bodyBytes = null;
        if (request instanceof org.springframework.web.util.ContentCachingRequestWrapper wrappedRequest) {
            bodyBytes = wrappedRequest.getContentAsByteArray();
        }
        if (bodyBytes == null || bodyBytes.length == 0) {
            bodyBytes = (rawJson != null) ? rawJson.getBytes(StandardCharsets.UTF_8) : new byte[0];
        }

        boolean isSignatureValid = webhookSignatureValidator.isValid(bodyBytes, blipSignature, blipWebhookSecret);

        String expectedToken = StringUtils.hasText(blipWebhookToken)
            ? blipWebhookToken
            : System.getenv("APP_BLIP_SECURITY_WEBHOOK_TOKEN");

        boolean hasTokenMatch = StringUtils.hasText(inovareToken)
            && StringUtils.hasText(expectedToken)
            && secureCompare(expectedToken, inovareToken);

        boolean isBypassProfile = env.acceptsProfiles(Profiles.of("local", "default"));
        boolean isBypassEnabled = hasTokenMatch
            || (isBypassProfile && StringUtils.hasText(inovareToken));

        if (!isSignatureValid && !isBypassEnabled) {
            log.warn("[ACESSO NEGADO] Assinatura do webhook inválida ou ausente em /v1/appointments/blip/webhook. Bypass inativo.");
            return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).build();
        }

        if (rawJson == null || rawJson.isBlank()) {
            log.warn("Webhook Blip recebido sem body em /v1/appointments/blip/webhook.");
            return ResponseEntity.ok(Map.of(
                    "status", "ignored",
                    "reason", "body-empty"));
        }

        Map<String, Object> payload;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(rawJson, Map.class);
            payload = map != null ? map : Map.of();
        } catch (com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException e) {
            log.error("Erro ao realizar o parse do JSON do webhook da Blip em /v1/appointments/blip/webhook", e);
            return ResponseEntity.badRequest().body(Map.of(
                "status", "ignored",
                "reason", "invalid-json"
            ));
        }

        BlipWebhookInboundService.ParsedInbound parsed = blipWebhookInboundService.parse(payload);

        if (parsed.isOutbound()) {
            return ResponseEntity.ok(Map.of("status", "ignored", "reason", "outbound-message"));
        }

        Map<String, Object> metadata = new java.util.HashMap<>(extractMetadata(payload));
        if (parsed.rawFrom() != null) {
            metadata.put("rawFrom", parsed.rawFrom());
        }

        handleBlipWebhookUseCase.execute(new HandleBlipWebhookUseCase.BlipWebhookPayload(
                parsed.messageId(),
                parsed.appointmentId(),
                parsed.action(),
                parsed.from(),
                inovareToken,
                parsed.content(),
                metadata,
                parsed.bsuid(),
                parsed.type()), true); // skipTokenValidation is true because we already verified it above

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

    private boolean secureCompare(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return java.security.MessageDigest.isEqual(
            a.getBytes(java.nio.charset.StandardCharsets.UTF_8),
            b.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
    }
}


