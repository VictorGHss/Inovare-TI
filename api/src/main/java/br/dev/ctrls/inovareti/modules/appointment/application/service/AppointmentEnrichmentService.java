package br.dev.ctrls.inovareti.modules.appointment.application.service;

import io.micrometer.observation.annotation.Observed;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientResponseException;

import br.dev.ctrls.inovareti.modules.user.domain.port.output.UserRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.model.AppointmentDoctorMapping;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentDoctorMappingRepositoryPort;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.FeegowProfessional;
import br.dev.ctrls.inovareti.modules.appointment.domain.port.output.ProfessionalExternalPort;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.input.rest.AppointmentMotorController.DoctorMappingUpsert;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.input.rest.AppointmentMotorController.SyncMappingRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço de aplicação responsável pelo processamento de mapeamentos,
 * enriquecimento com dados da API Feegow e sincronização do motor de agendamentos.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Observed
public class AppointmentEnrichmentService {

    private final AppointmentDoctorMappingRepositoryPort appointmentDoctorMappingRepository;
    private final ProfessionalExternalPort professionalExternalPort;
    private final UserRepositoryPort userRepository;

    private record SyncItemResult(boolean success, SyncMappingRequest details, String reason) {}

    public List<AppointmentDoctorMapping> getMappings() {
        List<AppointmentDoctorMapping> mappings = appointmentDoctorMappingRepository.findAll();
        try {
            List<FeegowProfessional> pros = professionalExternalPort.listProfessionals();
            if (pros != null && !pros.isEmpty()) {
                Map<String, String> idToName = pros.stream()
                        .filter(p -> p.id() != null)
                        .collect(Collectors.toMap(p -> String.valueOf(p.id()), p -> p.name(), (a, b) -> a));

                for (AppointmentDoctorMapping m : mappings) {
                    enrichMappingName(m, idToName);
                }
            }
        } catch (RestClientResponseException ex) {
            log.warn("Falha ao consultar Feegow para enriquecimento de nomes: status={}", ex.getStatusCode());
        } catch (Exception ex) {
            log.warn("Erro inesperado ao enriquecer nomes de profissionais: {}", ex.getMessage());
        }
        return mappings;
    }

    private void enrichMappingName(AppointmentDoctorMapping m, Map<String, String> idToName) {
        if (!StringUtils.hasText(m.getProfissionalNome())) {
            String candidate = idToName.get(m.getProfissionalId());
            if (StringUtils.hasText(candidate)) {
                m.setProfissionalNome(formatProperName(candidate));
            }
        }
    }

    public List<Map<String, String>> getProfessionals() {
        try {
            List<FeegowProfessional> list = professionalExternalPort.listProfessionals();
            return list.stream()
                    .map(p -> Map.of("id", p.id(), "name", p.name()))
                    .collect(Collectors.toList());
        } catch (RestClientResponseException ex) {
            log.error("Erro ao consultar Feegow (professionals). status={}", ex.getStatusCode());
            throw ex;
        } catch (Exception ex) {
            log.error("Erro inesperado ao listar profissionais da Feegow: {}", ex.getMessage());
            throw ex;
        }
    }

    @Transactional
    public Map<String, Object> syncMappings(List<SyncMappingRequest> payload) {
        log.info("syncMappings chamado com payload: {}", payload);
        if (CollectionUtils.isEmpty(payload)) {
            throw new IllegalArgumentException("empty-body");
        }

        List<String> validProfissionalIds = payload.stream()
                .filter(java.util.Objects::nonNull)
                .map(item -> normalizeValue(item.profissionalId()))
                .filter(pId -> pId != null && !pId.isBlank())
                .toList();

        List<AppointmentDoctorMapping> allMappings = appointmentDoctorMappingRepository.findAll();
        List<AppointmentDoctorMapping> mappingsToDelete = allMappings.stream()
                .filter(m -> !validProfissionalIds.contains(m.getProfissionalId()))
                .toList();

        appointmentDoctorMappingRepository.deleteAll(mappingsToDelete);
        
        return executeSyncLoop(payload, mappingsToDelete.size());
    }

    private Map<String, Object> executeSyncLoop(List<SyncMappingRequest> payload, int deleted) {
        int created = 0;
        int updated = 0;
        int skipped = 0;
        List<Map<String, Object>> skippedItems = new ArrayList<>();
        Set<String> processedProfissionalIds = new HashSet<>();

        for (SyncMappingRequest item : payload) {
            SyncItemResult res = processSyncItem(item, processedProfissionalIds);
            if (!res.success()) {
                skipped++;
                skippedItems.add(buildSkippedItem(res.details(), res.reason()));
            } else if ("created".equals(res.reason())) {
                created++;
            } else {
                updated++;
            }
        }

        return Map.of("status", "success", "received", payload.size(), "created", created,
                "updated", updated, "deleted", deleted, "skipped", skipped, "skippedItems", skippedItems);
    }

    private SyncItemResult processSyncItem(SyncMappingRequest item, Set<String> processedProfissionalIds) {
        if (item == null) return new SyncItemResult(false, null, "null-item");

        String profissionalId = normalizeValue(item.profissionalId());
        if (profissionalId == null || profissionalId.isBlank()) {
            return new SyncItemResult(false, null, "missing-profissional-id");
        }
        
        if (!processedProfissionalIds.add(profissionalId)) {
            return new SyncItemResult(false, item, "duplicate-in-payload");
        }

        String blipQueueId = normalizeValue(item.blipQueueId());
        String itsmUserId = normalizeValue(item.itsmUserId());
        String externalLink = normalizeValue(firstNonBlank(item.externalWaLink(), item.externalLink()));

        AppointmentDoctorMapping mapping = appointmentDoctorMappingRepository.findByProfissionalId(profissionalId).orElse(null);
        if (mapping == null) {
            if (!StringUtils.hasText(blipQueueId)) {
                return new SyncItemResult(false, item, "missing-blipQueueId-for-create");
            }
            createMapping(item, profissionalId, blipQueueId, itsmUserId, externalLink);
            return new SyncItemResult(true, null, "created");
        }

        updateMapping(mapping, item, blipQueueId, itsmUserId, externalLink);
        return new SyncItemResult(true, null, "updated");
    }

    private void createMapping(SyncMappingRequest item, String profissionalId, String blipQueueId, String itsmUserId, String externalLink) {
        AppointmentDoctorMapping createdMapping = AppointmentDoctorMapping.builder()
                .profissionalId(profissionalId)
                .blipQueueId(blipQueueId)
                .itsmUserId(itsmUserId)
                .externalWaLink(externalLink)
                .external(StringUtils.hasText(externalLink))
                .ignoreAutoSchedule(Boolean.TRUE.equals(item.ignoreAutoSchedule()))
                .build();

        applyMappingDetails(createdMapping, item.profissionalNome(), item.discordWebhookUrl(), item.isExternal(), item.ignoreAutoSchedule(), itsmUserId);
        enrichDoctorName(createdMapping, profissionalId);
        appointmentDoctorMappingRepository.save(createdMapping);
    }

    private void updateMapping(AppointmentDoctorMapping mapping, SyncMappingRequest item, String blipQueueId, String itsmUserId, String externalLink) {
        if (StringUtils.hasText(blipQueueId)) {
            mapping.setBlipQueueId(blipQueueId);
        }
        if (StringUtils.hasText(externalLink)) {
            mapping.setExternalWaLink(externalLink);
            mapping.setExternal(true);
        }

        applyMappingDetails(mapping, item.profissionalNome(), item.discordWebhookUrl(), item.isExternal(), item.ignoreAutoSchedule(), itsmUserId);

        if (!StringUtils.hasText(mapping.getProfissionalNome())) {
            enrichDoctorName(mapping, mapping.getProfissionalId());
        }
        appointmentDoctorMappingRepository.save(mapping);
    }

    private void applyMappingDetails(AppointmentDoctorMapping mapping, String name, String discordUrl, Boolean isExt, Boolean ignoreAuto, String itsmUserId) {
        if (StringUtils.hasText(name)) {
            mapping.setProfissionalNome(formatProperName(name));
        }
        if (StringUtils.hasText(discordUrl)) {
            mapping.setDiscordWebhookUrl(discordUrl);
        }
        if (isExt != null) {
            mapping.setExternal(isExt);
        }
        if (ignoreAuto != null) {
            mapping.setIgnoreAutoSchedule(ignoreAuto);
        }
        if (StringUtils.hasText(itsmUserId)) {
            mapping.setItsmUserId(itsmUserId);
            try {
                userRepository.findById(UUID.fromString(itsmUserId)).ifPresent(u -> mapping.setSecretaryNames(u.getName()));
            } catch (IllegalArgumentException e) {
                log.warn("itsmUserId inválido: {}", itsmUserId);
            }
        }
    }

    private void enrichDoctorName(AppointmentDoctorMapping mapping, String id) {
        try {
            String profNome = professionalExternalPort.getProfessionalName(id);
            if (StringUtils.hasText(profNome)) {
                mapping.setProfissionalNome(formatProperName(profNome));
            }
        } catch (Exception e) {
            log.warn("Falha ao recuperar nome Feegow para id {}: {}", id, e.getMessage());
        }
    }

    @Transactional
    public Map<String, Object> upsertDoctorMapping(DoctorMappingUpsert payload) {
        log.info("upsertDoctorMapping chamado com payload: {}", payload);
        if (payload == null || normalizeValue(payload.profissionalId()) == null) {
            throw new IllegalArgumentException("missing-profissionalId");
        }

        String profissionalId = normalizeValue(payload.profissionalId());
        AppointmentDoctorMapping mapping = appointmentDoctorMappingRepository.findByProfissionalId(profissionalId)
                .orElseGet(() -> {
                    AppointmentDoctorMapping m = new AppointmentDoctorMapping();
                    m.setProfissionalId(profissionalId);
                    m.setBlipQueueId(payload.blipQueueId() == null ? "" : payload.blipQueueId());
                    return m;
                });

        if (StringUtils.hasText(payload.blipQueueId())) {
            mapping.setBlipQueueId(payload.blipQueueId().trim());
        }

        applyMappingDetails(mapping, payload.profissionalNome(), payload.discordWebhookUrl(), payload.isExternal(), payload.ignoreAutoSchedule(), payload.itsmUserId());
        if (StringUtils.hasText(payload.externalWaLink())) {
            mapping.setExternalWaLink(payload.externalWaLink().trim());
            mapping.setExternal(true);
        }

        appointmentDoctorMappingRepository.save(mapping);
        return Map.of("status", "success", "profissionalId", profissionalId);
    }

    @Transactional
    public boolean deleteMapping(String id) {
        UUID uuid = UUID.fromString(id);
        if (!appointmentDoctorMappingRepository.existsById(uuid)) {
            return false;
        }
        appointmentDoctorMappingRepository.deleteById(uuid);
        return true;
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (StringUtils.hasText(value)) return value.trim();
        }
        return null;
    }

    private String normalizeValue(String value) {
        if (!StringUtils.hasText(value)) return null;
        return value.trim();
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


