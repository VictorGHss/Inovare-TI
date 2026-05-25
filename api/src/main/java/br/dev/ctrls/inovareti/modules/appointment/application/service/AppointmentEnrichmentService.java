package br.dev.ctrls.inovareti.modules.appointment.application.service;

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

import br.dev.ctrls.inovareti.domain.user.User;
import br.dev.ctrls.inovareti.domain.user.UserRepository;
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
public class AppointmentEnrichmentService {

    private final AppointmentDoctorMappingRepositoryPort appointmentDoctorMappingRepository;
    private final ProfessionalExternalPort professionalExternalPort;
    private final UserRepository userRepository;

    /**
     * Lista todos os mapeamentos de médicos, tentando enriquecer o nome do profissional
     * a partir da API Feegow caso esteja em falta no banco de dados.
     */
    public List<AppointmentDoctorMapping> getMappings() {
        List<AppointmentDoctorMapping> mappings = appointmentDoctorMappingRepository.findAll();

        try {
            List<FeegowProfessional> pros = professionalExternalPort.listProfessionals();
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

        return mappings;
    }

    /**
     * Obtém a lista de profissionais da Feegow formatada como pares de chave/valor (id e name).
     */
    public List<Map<String, String>> getProfessionals() {
        try {
            List<FeegowProfessional> list = professionalExternalPort.listProfessionals();
            return list.stream()
                    .map(p -> Map.of("id", p.id(), "name", p.name()))
                    .collect(Collectors.toList());
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode() != null ? ex.getStatusCode().value() : 500;
            log.error("Erro ao consultar Feegow (professionals). status={}, headers={}, body={}", status, ex.getResponseHeaders(), ex.getResponseBodyAsString());
            throw ex;
        } catch (Exception ex) {
            log.error("Erro inesperado ao listar profissionais da Feegow: {}", ex.getMessage());
            throw ex;
        }
    }

    /**
     * Sincroniza em lote os mapeamentos de médicos, adicionando novos, atualizando existentes
     * e deletando mapeamentos que não foram informados no payload de sincronização.
     */
    @Transactional
    public Map<String, Object> syncMappings(List<SyncMappingRequest> payload) {
        log.info("syncMappings chamado com payload: {}", payload);

        if (CollectionUtils.isEmpty(payload)) {
            throw new IllegalArgumentException("empty-body");
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
                    .ignoreAutoSchedule(Boolean.TRUE.equals(item.ignoreAutoSchedule()))
                        .build();

                // Nome de exibição opcional fornecido no payload
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

                // Tenta enriquecer o nome do profissional a partir do Feegow
                try {
                    String profNome = professionalExternalPort.getProfessionalName(profissionalId);
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

            // Permite atualizar o nome, webhook do discord e flag de externo através do payload
            if (StringUtils.hasText(item.profissionalNome())) {
                mapping.setProfissionalNome(formatProperName(item.profissionalNome()));
            }

            if (StringUtils.hasText(item.discordWebhookUrl())) {
                mapping.setDiscordWebhookUrl(item.discordWebhookUrl());
            }

            if (item.isExternal() != null) {
                mapping.setExternal(item.isExternal());
            }

            if (item.ignoreAutoSchedule() != null) {
                mapping.setIgnoreAutoSchedule(item.ignoreAutoSchedule());
            }

            // Enriquece o nome apenas se já não houver um definido
            if (!StringUtils.hasText(mapping.getProfissionalNome())) {
                try {
                    String profNome = professionalExternalPort.getProfessionalName(profissionalId);
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

        return Map.of(
                "status", "success",
                "received", payload.size(),
                "created", created,
                "updated", updated,
                "deleted", deleted,
                "skipped", skipped,
                "skippedItems", skippedItems);
    }

    /**
     * Cria ou atualiza de forma transacional um único mapeamento de médico/profissional.
     */
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
                    // Fornece ID de fila padrão para evitar nulos em novas linhas
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

        if (payload.ignoreAutoSchedule() != null) {
            mapping.setIgnoreAutoSchedule(payload.ignoreAutoSchedule());
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

        return Map.of("status", "success", "profissionalId", profissionalId);
    }

    /**
     * Remove um mapeamento do banco de dados a partir do ID UUID de forma transacional.
     */
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
