package br.dev.ctrls.inovareti.domain.appointment;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
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
import com.fasterxml.jackson.databind.JsonNode;

import br.dev.ctrls.inovareti.domain.appointment.usecase.HandleBlipWebhookUseCase;
import br.dev.ctrls.inovareti.domain.appointment.usecase.IngestAppointmentsUseCase;
import br.dev.ctrls.inovareti.domain.user.User;
import br.dev.ctrls.inovareti.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/v1/appointments")
@RequiredArgsConstructor
public class AppointmentMotorController {


    private final AppointmentMotorProperties appointmentMotorProperties;
    private final IngestAppointmentsUseCase ingestAppointmentsUseCase;
    private final HandleBlipWebhookUseCase handleBlipWebhookUseCase;
    private final AppointmentDoctorMappingRepository appointmentDoctorMappingRepository;
    private final BlipClient blipClient;
    private final FeegowClient feegowClient;
    private final UserRepository userRepository;

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
            List<AppointmentDoctorMapping> mappings = appointmentDoctorMappingRepository.findAll();

            // Attempt to enrich profissionalNome from Feegow where missing
            try {
                List<FeegowClient.FeegowProfessional> pros = feegowClient.listProfessionals();
                if (pros != null && !pros.isEmpty()) {
                    Map<String, String> idToName = pros.stream()
                            .filter(p -> p.id() != null)
                            .collect(Collectors.toMap(p -> String.valueOf(p.id()), p -> p.name(), (a, b) -> a));

                    for (AppointmentDoctorMapping m : mappings) {
                        if (!StringUtils.hasText(m.getProfissionalNome())) {
                            String candidate = idToName.get(m.getProfissionalId());
                            if (StringUtils.hasText(candidate)) {
                                m.setProfissionalNome(formatProperName(candidate));
                            }
                        }
                    }
                }
            } catch (RestClientResponseException ex) {
                int status = ex.getStatusCode() != null ? ex.getStatusCode().value() : 500;
                log.warn("Falha ao consultar Feegow para enriquecimento de nomes: status={}, body={}", status, ex.getResponseBodyAsString());
            } catch (Exception ex) {
                log.warn("Erro inesperado ao enriquecer nomes de profissionais: {}", ex.getMessage());
            }

            return ResponseEntity.ok(mappings);
        } catch (Exception ex) {
            log.error("Erro inesperado ao listar mappings: {}", ex.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/admin/debug-queues")
    public ResponseEntity<Map<String, Object>> debugQueues() {
        return ResponseEntity.ok(blipClient.getBlipQueues());
    }

    @GetMapping("/professionals")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Map<String, String>>> professionals() {
        try {
            List<FeegowClient.FeegowProfessional> list = feegowClient.listProfessionals();
            List<Map<String, String>> out = list.stream()
                    .map(p -> Map.of("id", p.id(), "name", p.name()))
                    .collect(Collectors.toList());
            return ResponseEntity.ok(out);
        } catch (RestClientResponseException ex) {
                int status = ex.getStatusCode() != null ? ex.getStatusCode().value() : 500;
                log.error("Erro ao consultar Feegow (professionals). status={}, headers={}, body={}", status, ex.getResponseHeaders(), ex.getResponseBodyAsString());
                // Propaga o mesmo status retornado pela Feegow para o frontend
                return ResponseEntity.status(ex.getStatusCode()).build();
        } catch (Exception ex) {
            log.error("Erro inesperado ao listar profissionais da Feegow: {}", ex.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/blip/queues")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Map<String, String>>> blipQueues() {
        List<BlipClient.BlipQueue> queues = blipClient.listBlipQueues();
        List<Map<String, String>> out = queues.stream()
                .map(q -> Map.of("id", q.id(), "name", q.name()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(out);
    }

    @PatchMapping("/admin/sync-mappings")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<Map<String, Object>> syncMappings(
            @RequestBody(required = false) List<SyncMappingRequest> payload) {
        log.info("syncMappings called with payload: {}", payload);

        if (CollectionUtils.isEmpty(payload)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "reason", "empty-body"));
        }

        List<String> validProfissionalIds = new ArrayList<>();
        for (SyncMappingRequest item : payload) {
            if (item != null) {
                String pId = normalizeValue(item.profissionalId());
                if (pId != null && !pId.isBlank()) {
                    validProfissionalIds.add(pId);
                }
            }
        }

        List<AppointmentDoctorMapping> allMappings = appointmentDoctorMappingRepository.findAll();
        List<AppointmentDoctorMapping> mappingsToDelete = allMappings.stream()
                .filter(m -> !validProfissionalIds.contains(m.getProfissionalId()))
                .toList();

        appointmentDoctorMappingRepository.deleteAll(mappingsToDelete);
        int deleted = mappingsToDelete.size();

        int created = 0;
        int updated = 0;
        int skipped = 0;
        List<Map<String, Object>> skippedItems = new ArrayList<>();
        
        Set<String> processedProfissionalIds = new HashSet<>();

        for (SyncMappingRequest item : payload) {
            if (item == null) {
                skipped++;
                skippedItems.add(buildSkippedItem(null, "null-item"));
                continue;
            }

            String profissionalId = normalizeValue(item.profissionalId());
            if (profissionalId == null || profissionalId.isBlank()) {
                skipped++;
                skippedItems.add(buildSkippedItem(item, "missing-profissional-id"));
                continue;
            }
            
            // Previne falha de "duplicate key value violates unique constraint" no banco de dados.
            if (!processedProfissionalIds.add(profissionalId)) {
                skipped++;
                skippedItems.add(buildSkippedItem(item, "duplicate-in-payload"));
                continue;
            }

            String blipQueueId = normalizeValue(item.blipQueueId());
            String itsmUserId = normalizeValue(item.itsmUserId());
            String externalLink = normalizeValue(firstNonBlank(item.externalWaLink(), item.externalLink()));

            AppointmentDoctorMapping mapping = appointmentDoctorMappingRepository.findByProfissionalId(profissionalId)
                    .orElse(null);

            if (mapping == null) {
                if (!StringUtils.hasText(blipQueueId)) {
                    skipped++;
                    skippedItems.add(buildSkippedItem(item, "missing-blipQueueId-for-create"));
                    continue;
                }

                AppointmentDoctorMapping createdMapping = AppointmentDoctorMapping.builder()
                        .profissionalId(profissionalId)
                        .blipQueueId(blipQueueId)
                        .itsmUserId(itsmUserId)
                        .externalWaLink(externalLink)
                        .external(StringUtils.hasText(externalLink))
                        .build();

                // Optional provided display name
                if (StringUtils.hasText(item.profissionalNome())) {
                    createdMapping.setProfissionalNome(formatProperName(item.profissionalNome()));
                }

                if (StringUtils.hasText(item.discordWebhookUrl())) {
                    createdMapping.setDiscordWebhookUrl(item.discordWebhookUrl());
                }

                if (item.isExternal() != null) {
                    createdMapping.setExternal(item.isExternal());
                }

                if (StringUtils.hasText(itsmUserId)) {
                    try {
                        User user = userRepository.findById(UUID.fromString(itsmUserId)).orElse(null);
                        if (user != null) {
                            createdMapping.setSecretaryNames(user.getName());
                        }
                    } catch (IllegalArgumentException e) {
                        log.warn("itsmUserId inválido para busca de usuário: {}", itsmUserId);
                    }
                }

                // Attempt to enrich profissional_nome from Feegow
                try {
                    String profNome = feegowClient.getProfessionalName(profissionalId);
                        if (StringUtils.hasText(profNome)) {
                            createdMapping.setProfissionalNome(formatProperName(profNome));
                        }
                } catch (Exception e) {
                    log.warn("Falha ao recuperar nome do profissional da Feegow para id {}: {}", profissionalId, e.getMessage());
                }

                appointmentDoctorMappingRepository.save(createdMapping);
                created++;
                continue;
            }

            if (StringUtils.hasText(blipQueueId)) {
                mapping.setBlipQueueId(blipQueueId);
            }

            if (StringUtils.hasText(itsmUserId)) {
                mapping.setItsmUserId(itsmUserId);
                try {
                    User user = userRepository.findById(UUID.fromString(itsmUserId)).orElse(null);
                    if (user != null) {
                        mapping.setSecretaryNames(user.getName());
                    }
                } catch (IllegalArgumentException e) {
                    log.warn("itsmUserId inválido para busca de usuário: {}", itsmUserId);
                }
            }

            if (StringUtils.hasText(externalLink)) {
                mapping.setExternalWaLink(externalLink);
                mapping.setExternal(true);
            }

            // Allow setting profissional_nome, discord webhook and external flag via payload
            if (StringUtils.hasText(item.profissionalNome())) {
                mapping.setProfissionalNome(formatProperName(item.profissionalNome()));
            }

            if (StringUtils.hasText(item.discordWebhookUrl())) {
                mapping.setDiscordWebhookUrl(item.discordWebhookUrl());
            }

            if (item.isExternal() != null) {
                mapping.setExternal(item.isExternal());
            }

            // Enrich profissional_nome on update only if not already provided
            if (!StringUtils.hasText(mapping.getProfissionalNome())) {
                try {
                    String profNome = feegowClient.getProfessionalName(profissionalId);
                    if (StringUtils.hasText(profNome)) {
                        mapping.setProfissionalNome(formatProperName(profNome));
                    }
                } catch (Exception e) {
                    log.warn("Falha ao recuperar nome do profissional da Feegow para id {} durante update: {}", profissionalId, e.getMessage());
                }
            }

            appointmentDoctorMappingRepository.save(mapping);
            updated++;
        }

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "received", payload.size(),
                "created", created,
                "updated", updated,
                "deleted", deleted,
                "skipped", skipped,
                "skippedItems", skippedItems));
    }

    @PatchMapping("/admin/doctor-mapping")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<Map<String, Object>> upsertDoctorMapping(@RequestBody DoctorMappingUpsert payload) {
        log.info("upsertDoctorMapping called with payload: {}", payload);

        if (payload == null || normalizeValue(payload.profissionalId()) == null) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "reason", "missing-profissionalId"));
        }

        String profissionalId = normalizeValue(payload.profissionalId());

        AppointmentDoctorMapping mapping = appointmentDoctorMappingRepository.findByProfissionalId(profissionalId)
                .orElseGet(() -> {
                    AppointmentDoctorMapping m = new AppointmentDoctorMapping();
                    m.setProfissionalId(profissionalId);
                    // provide a default queue id so column is not null for new rows
                    m.setBlipQueueId(payload.blipQueueId() == null ? "" : payload.blipQueueId());
                    return m;
                });

        if (StringUtils.hasText(payload.blipQueueId())) {
            mapping.setBlipQueueId(payload.blipQueueId().trim());
        }

        if (payload.profissionalNome() != null) {
            mapping.setProfissionalNome(formatProperName(payload.profissionalNome()));
        }

        if (StringUtils.hasText(payload.externalWaLink())) {
            mapping.setExternalWaLink(payload.externalWaLink().trim());
            mapping.setExternal(true);
        }

        if (StringUtils.hasText(payload.discordWebhookUrl())) {
            mapping.setDiscordWebhookUrl(payload.discordWebhookUrl().trim());
        }

        if (payload.isExternal() != null) {
            mapping.setExternal(payload.isExternal());
        }

        if (StringUtils.hasText(payload.itsmUserId())) {
            mapping.setItsmUserId(payload.itsmUserId().trim());
            try {
                User user = userRepository.findById(UUID.fromString(payload.itsmUserId().trim())).orElse(null);
                if (user != null) {
                    mapping.setSecretaryNames(user.getName());
                }
            } catch (IllegalArgumentException e) {
                log.warn("itsmUserId inválido para busca de usuário: {}", payload.itsmUserId());
            }
        }

        appointmentDoctorMappingRepository.save(mapping);

        return ResponseEntity.ok(Map.of("status", "success", "profissionalId", profissionalId));
    }

    @DeleteMapping("/mapping/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<Void> deleteMapping(@PathVariable("id") String id) {
        try {
            UUID uuid = UUID.fromString(id);
            if (!appointmentDoctorMappingRepository.existsById(uuid)) {
                return ResponseEntity.notFound().build();
            }
            appointmentDoctorMappingRepository.deleteById(uuid);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        } catch (Exception ex) {
            log.error("Erro ao deletar mapeamento {}: {}", id, ex.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/blip/webhook")
    public ResponseEntity<Map<String, Object>> blipWebhook(@RequestBody(required = false) JsonNode payload) {
        log.error("RECEIVED IN WEBHOOK: " + payload);

        if (payload == null || payload.isNull()) {
            log.warn("Webhook Blip recebido sem body em /v1/appointments/blip/webhook.");
            return ResponseEntity.accepted().body(Map.of(
                    "status", "ignored",
                    "reason", "body-empty"));
        }

        String from = extractFrom(payload);
        String action = extractActionText(payload);
        String messageId = extractMessageId(payload);
        String appointmentId = extractAppointmentId(payload);

        handleBlipWebhookUseCase.execute(new HandleBlipWebhookUseCase.BlipWebhookPayload(
                messageId,
                appointmentId,
                action,
                from
        ));

        return ResponseEntity.accepted().body(Map.of(
                "status", "processed"));
    }

    private String extractFrom(JsonNode payload) {
        return firstNonBlank(
                payload.path("from").asText(null),
                payload.path("resource").path("from").asText(null),
                payload.path("message").path("from").asText(null));
    }

    private String extractActionText(JsonNode payload) {
        return firstNonBlank(
                payload.path("action").asText(null),
                payload.path("content").asText(null),
                payload.path("content").path("text").asText(null),
                payload.path("content").path("title").asText(null),
                payload.path("resource").path("action").asText(null),
                payload.path("resource").path("content").asText(null),
                payload.path("resource").path("content").path("text").asText(null),
                payload.path("resource").path("content").path("title").asText(null));
    }

    private String extractMessageId(JsonNode payload) {
        return firstNonBlank(
                payload.path("id").asText(null),
                payload.path("message").path("id").asText(null),
                UUID.randomUUID().toString()
        );
    }

    private String extractAppointmentId(JsonNode payload) {
        return firstNonBlank(
                payload.path("appointmentId").asText(null),
                payload.path("metadata").path("appointmentId").asText(null),
                payload.path("envelope").path("metadata").path("appointmentId").asText(null),
                payload.path("resource").path("appointmentId").asText(null),
                payload.path("resource").path("metadata").path("appointmentId").asText(null),
                payload.path("resource").path("content").path("appointmentId").asText(null));
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }

        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }

        return null;
    }

    private String normalizeValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        return value.trim();
    }

        public record SyncMappingRequest(
            @JsonProperty("profissionalId") String profissionalId,
            @JsonProperty("blipQueueId") String blipQueueId,
            @JsonProperty("itsmUserId") String itsmUserId,
            @JsonProperty("externalWaLink") String externalWaLink,
            @JsonProperty("externalLink") String externalLink,
            @JsonProperty("profissionalNome") String profissionalNome,
            @JsonProperty("discordWebhookUrl") String discordWebhookUrl,
            @JsonProperty("isExternal") Boolean isExternal) {
        }

        public record DoctorMappingUpsert(
            @JsonProperty("profissionalId") String profissionalId,
            @JsonProperty("profissionalNome") String profissionalNome,
            @JsonProperty("blipQueueId") String blipQueueId,
            @JsonProperty("itsmUserId") String itsmUserId,
            @JsonProperty("discordWebhookUrl") String discordWebhookUrl,
            @JsonProperty("externalWaLink") String externalWaLink,
            @JsonProperty("isExternal") Boolean isExternal) {
        }

    private Map<String, Object> buildSkippedItem(SyncMappingRequest item, String reason) {
        Map<String, Object> skippedItem = new LinkedHashMap<>();
        skippedItem.put("reason", reason);

        if (item == null) {
            skippedItem.put("profissionalId", null);
            return skippedItem;
        }

        skippedItem.put("profissionalId", normalizeValue(item.profissionalId()));
        skippedItem.put("blipQueueId", normalizeValue(item.blipQueueId()));
        return skippedItem;
    }

    private String formatProperName(String raw) {
        if (!StringUtils.hasText(raw)) return null;
        String normalized = raw.trim().toLowerCase();
        String[] parts = normalized.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            sb.append(Character.toUpperCase(p.charAt(0)));
            if (p.length() > 1) sb.append(p.substring(1));
            sb.append(' ');
        }
        return sb.toString().trim();
    }
}
