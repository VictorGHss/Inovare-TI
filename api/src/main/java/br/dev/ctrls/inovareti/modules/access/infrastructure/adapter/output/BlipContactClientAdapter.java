package br.dev.ctrls.inovareti.modules.access.infrastructure.adapter.output;

import br.dev.ctrls.inovareti.modules.access.domain.port.output.BlipContactClientPort;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.AppointmentMotorProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.util.Map;
import java.util.UUID;

/**
 * Adaptador de infraestrutura BlipContactClientAdapter.
 * Sincroniza ativamente as informações do contato do paciente via API Rest do Blip.
 * Comentários mantidos em PT-BR pelas Regras de Ouro.
 */
@Slf4j
@Component
public class BlipContactClientAdapter implements BlipContactClientPort {

    private final AppointmentMotorProperties properties;
    private final br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.BlipProperties blipProperties;
    private final br.dev.ctrls.inovareti.modules.appointment.domain.port.output.BlipUserIdentityReconciliationRepositoryPort reconciliationRepository;
    private final br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentSessionRepositoryPort appointmentSessionRepository;
    private final br.dev.ctrls.inovareti.modules.appointment.domain.port.output.PatientExternalPort patientExternalPort;

    private RestClient restClient;

    private final java.util.concurrent.ConcurrentHashMap<String, Long> contactSyncCache = new java.util.concurrent.ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    public BlipContactClientAdapter(
            AppointmentMotorProperties properties,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.BlipProperties blipProperties,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            br.dev.ctrls.inovareti.modules.appointment.domain.port.output.BlipUserIdentityReconciliationRepositoryPort reconciliationRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            br.dev.ctrls.inovareti.modules.appointment.domain.port.output.AppointmentSessionRepositoryPort appointmentSessionRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            br.dev.ctrls.inovareti.modules.appointment.domain.port.output.PatientExternalPort patientExternalPort
    ) {
        this.properties = properties;
        this.blipProperties = blipProperties;
        this.reconciliationRepository = reconciliationRepository;
        this.appointmentSessionRepository = appointmentSessionRepository;
        this.patientExternalPort = patientExternalPort;
    }

    public BlipContactClientAdapter(AppointmentMotorProperties properties) {
        this(properties, null, null, null, null);
    }

    @PostConstruct
    public void init() {
        String baseUrl = properties.getBlipBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://inovaremed.http.msging.net";
        }
        log.info("[BlipContact-Adapter] Inicializando RestClient para Blip. BaseURL: {}", baseUrl);

        java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(10))
                .build();

        org.springframework.http.client.JdkClientHttpRequestFactory factory =
                new org.springframework.http.client.JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(java.time.Duration.ofSeconds(10));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    public static boolean isInvalidName(String name) {
        if (name == null || name.isBlank() || "null".equalsIgnoreCase(name.trim())) {
            return true;
        }
        String trimmed = name.trim();
        if (trimmed.contains("@") || trimmed.contains("msging.net")) {
            return true;
        }
        if (trimmed.matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")) {
            return true;
        }
        return false;
    }

    @Override
    public boolean syncContact(String phoneNumber, String name, String cpf, String queueName, String doctorId) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            log.warn("[BlipContact-Adapter] Telefone do contato nulo ou vazio. Cancelando sincronização.");
            return false;
        }

        String normalizedIdentity = normalizeIdentity(phoneNumber);
        if (properties.isTestMode(doctorId)) {
            log.info("[BlipContact-Adapter] [TEST-MODE] Simulando sincronização com sucesso para {}: Nome={}, CPF={}, Fila={}, DoctorId={}",
                    normalizedIdentity, name, cpf, queueName, doctorId);
            return true;
        }

        final String cleanName = resolveCleanName(phoneNumber, normalizedIdentity, name);

        final String cleanCpf = (cpf != null && !cpf.isBlank() && !cpf.equalsIgnoreCase("null"))
            ? cpf.replaceAll("\\D", "")
            : "";

        final String cleanQueue = (queueName != null && !queueName.isBlank() && !queueName.equalsIgnoreCase("null"))
            ? queueName.trim()
            : "";

        // Cache de Idempotência de Sync de Contato (120s / 2 min)
        String cacheKey = normalizedIdentity + ":" + cleanName + ":" + cleanQueue;
        long now = System.currentTimeMillis();
        Long lastSync = contactSyncCache.get(cacheKey);
        if (lastSync != null && (now - lastSync) < 120000L) {
            log.info("[SYNC-CACHE-HIT] Contato {} já sincronizado recentemente. Pulando chamadas REST redundantes.", normalizedIdentity);
            return true;
        }

        final String rawPhone = normalizedIdentity.contains("@") 
            ? normalizedIdentity.substring(0, normalizedIdentity.indexOf('@')) 
            : normalizedIdentity;
        final String digitsOnly = rawPhone.replaceAll("\\D", "");
        final String formattedPhone = digitsOnly.startsWith("55") ? "+" + digitsOnly : "+55" + digitsOnly;
        final String plainPhone = (digitsOnly.startsWith("55") && digitsOnly.length() > 11) ? digitsOnly.substring(2) : digitsOnly;

        java.util.List<String> targetIdentities = new java.util.ArrayList<>();
        targetIdentities.add(normalizedIdentity);

        // Resolve túnel determinístico (ex: 5542999999999.fluxov1@tunnel.msging.net)
        try {
            String subbotId = blipProperties != null ? blipProperties.getSubbotId() : null;
            if (subbotId != null && !subbotId.isBlank()) {
                String subbotLocalPart = subbotId.trim();
                if (subbotLocalPart.contains("@")) {
                    subbotLocalPart = subbotLocalPart.substring(0, subbotLocalPart.indexOf('@'));
                }
                if (!digitsOnly.isBlank() && subbotLocalPart != null && !subbotLocalPart.isBlank()) {
                    String deterministicTunnel = digitsOnly + "." + subbotLocalPart + "@tunnel.msging.net";
                    if (!targetIdentities.contains(deterministicTunnel)) {
                        targetIdentities.add(deterministicTunnel);
                    }
                }
            }
        } catch (Exception ex) {
            log.debug("[BlipContact-Adapter] Falha ao resolver túnel determinístico: {}", ex.getMessage());
        }

        // Resolve túneis reconciliados do banco de dados (ex: GUID@tunnel.msging.net)
        try {
            if (!digitsOnly.isBlank()) {
                String searchPhone = digitsOnly.startsWith("55") ? digitsOnly : "55" + digitsOnly;
                String altPhone = searchPhone.startsWith("55") && searchPhone.length() > 2 ? searchPhone.substring(2) : searchPhone;
                
                if (reconciliationRepository != null) {
                    var reconciliations = new java.util.ArrayList<br.dev.ctrls.inovareti.modules.appointment.domain.model.BlipUserIdentityReconciliation>();
                    reconciliations.addAll(reconciliationRepository.findByPhoneNumber(searchPhone));
                    reconciliations.addAll(reconciliationRepository.findByPhoneNumber(altPhone));
                    for (var rec : reconciliations) {
                        if (rec.getBlipGuid() != null && !rec.getBlipGuid().isBlank()) {
                            String tunnelId = rec.getBlipGuid().trim() + "@tunnel.msging.net";
                            if (!targetIdentities.contains(tunnelId)) {
                                targetIdentities.add(tunnelId);
                            }
                        }
                    }
                }

                if (appointmentSessionRepository != null) {
                    var activeSessions = appointmentSessionRepository.findActiveByPhoneNumber(searchPhone);
                    if (activeSessions != null) {
                        for (var s : activeSessions) {
                            String guid = s.getBlipGuid();
                            if (guid == null || guid.isBlank()) guid = s.getBsuid();
                            if (guid != null && !guid.isBlank()) {
                                String cleanGuid = guid.contains("@") ? guid.substring(0, guid.indexOf('@')) : guid.trim();
                                if (cleanGuid.matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")) {
                                    String tunnelId = cleanGuid + "@tunnel.msging.net";
                                    if (!targetIdentities.contains(tunnelId)) {
                                        targetIdentities.add(tunnelId);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ex) {
            log.debug("[BlipContact-Adapter] Falha ao resolver túneis reconciliados: {}", ex.getMessage());
        }

        if (phoneNumber.contains("@tunnel.msging.net") && !targetIdentities.contains(phoneNumber.trim())) {
            targetIdentities.add(phoneNumber.trim());
        }

        log.info("[BlipContact-Adapter] Sincronizando contato em escopo dual (Master + Túneis) para {}. Qtd identidades={}. Nome={}, CPF={}, Fila={}",
                normalizedIdentity, targetIdentities.size(), cleanName, cleanCpf, cleanQueue);

        java.util.List<java.util.concurrent.CompletableFuture<Boolean>> futures = targetIdentities.stream()
                .map(targetId -> java.util.concurrent.CompletableFuture.supplyAsync(
                        () -> sendContactCommand(targetId, cleanName, formattedPhone, plainPhone, cleanCpf, cleanQueue, digitsOnly)))
                .toList();

        boolean overallSuccess = false;
        for (var future : futures) {
            try {
                if (future.join()) {
                    overallSuccess = true;
                }
            } catch (Exception ex) {
                log.warn("[BlipContact-Adapter] Falha em tarefa paralela de sincronização: {}", ex.getMessage());
            }
        }

        if (overallSuccess) {
            contactSyncCache.put(cacheKey, now);
        }

        if (contactSyncCache.size() > 5000) {
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                long currentTime = System.currentTimeMillis();
                contactSyncCache.entrySet().removeIf(entry -> (currentTime - entry.getValue()) > 120000L);
            });
        }

        return overallSuccess;
    }

    private String resolveCleanName(String phoneNumber, String normalizedIdentity, String name) {
        if (!isInvalidName(name)) {
            return name.trim();
        }

        try {
            String digitsOnly = phoneNumber.replaceAll("\\D", "");
            if (!digitsOnly.isBlank() && appointmentSessionRepository != null && patientExternalPort != null) {
                String searchPhone = digitsOnly.startsWith("55") ? digitsOnly : "55" + digitsOnly;
                var activeSessions = appointmentSessionRepository.findActiveByPhoneNumber(searchPhone);
                if (activeSessions != null && !activeSessions.isEmpty()) {
                    for (var session : activeSessions) {
                        if (session.getPatientId() != null && !session.getPatientId().isBlank()) {
                            var patient = patientExternalPort.patientInfo(session.getPatientId());
                            if (patient != null && patient.name() != null && !isInvalidName(patient.name())) {
                                log.info("[BlipContact-Adapter] Nome do paciente ('{}') recuperado com sucesso via Feegow/Session para {}", patient.name(), normalizedIdentity);
                                return patient.name().trim();
                            }
                        }
                    }
                }
            }
        } catch (Exception ex) {
            log.debug("[BlipContact-Adapter] Erro defensivo ao tentar buscar nome do paciente no Feegow: {}", ex.getMessage());
        }

        if (name != null && !name.isBlank()) {
            log.warn("[BlipContact-Adapter] Nome fornecido ('{}') é inválido (GUID/identidade de túnel). Usando fallback 'Paciente Não Identificado' para forçar sobrescrita no Blip.", name);
        }

        return "Paciente Não Identificado";
    }

    private boolean sendContactCommand(String identity, String name, String formattedPhone, String plainPhone, String cleanCpf, String cleanQueue, String digitsOnly) {
        String authKey = resolveAuthorizationKey();
        if (!authKey.startsWith("Key ")) {
            authKey = "Key " + authKey;
        }

        Map<String, Object> contactResource = new java.util.LinkedHashMap<>();
        contactResource.put("identity", identity);
        contactResource.put("name", name);
        if (!digitsOnly.isBlank()) {
            contactResource.put("phoneNumber", formattedPhone);
            contactResource.put("cellPhoneNumber", formattedPhone);
        }
        contactResource.put("extras", Map.of(
            "cpf", cleanCpf,
            "fila", cleanQueue,
            "deskFila", cleanQueue,
            "phoneNumber", plainPhone,
            "telefone", plainPhone
        ));

        Map<String, Object> command = Map.of(
            "id", "sync-contact-" + UUID.randomUUID().toString(),
            "to", "postmaster@msging.net",
            "method", "set",
            "uri", "/contacts",
            "type", "application/vnd.lime.contact+json",
            "resource", contactResource
        );

        try {
            String path = properties.getBlipSetContextPath();
            if (path == null || path.isBlank()) {
                path = "/commands";
            }

            try {
                @SuppressWarnings("rawtypes")
                ResponseEntity<Map> response = restClient.post()
                        .uri(path)
                        .header("Authorization", authKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(command)
                        .retrieve()
                        .toEntity(Map.class);

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    Object status = response.getBody().get("status");
                    if ("success".equalsIgnoreCase(String.valueOf(status))) {
                        log.info("[BlipContact-Adapter] Sincronização concluída com sucesso no Blip para {}", identity);
                        return true;
                    } else {
                        log.warn("[BlipContact-Adapter] Blip retornou status de falha no comando: {}. Body={}", status, response.getBody());
                    }
                } else {
                    log.warn("[BlipContact-Adapter] Blip respondeu com HTTP status: {}", response.getStatusCode());
                }
            } catch (org.springframework.web.client.HttpClientErrorException.TooManyRequests ex) {
                log.warn("[BlipContact-Adapter] [HTTP 429] Rate limit (Cloudflare Error 1015) atingido no Blip para {}. Abortando envio.", identity);
            } catch (Exception ex) {
                String msg = ex.getMessage() != null ? ex.getMessage() : "";
                if (msg.contains("429") || msg.contains("1015") || msg.toLowerCase().contains("too many requests") || msg.toLowerCase().contains("cloudflare")) {
                    log.warn("[BlipContact-Adapter] [HTTP 429] Rate limit (Cloudflare Error 1015) atingido no Blip para {}. Abortando envio.", identity);
                } else {
                    log.error("[BlipContact-Adapter] Falha de comunicação com o Blip para a identidade {}: {}", identity, ex.getMessage());
                }
            }
        } catch (Exception ex) {
            log.error("[BlipContact-Adapter] Erro ao preparar sincronização com o Blip para a identidade {}: {}",
                    identity, ex.getMessage());
        }

        return false;
    }

    private String normalizeIdentity(String phone) {
        if (phone == null) {
            return "";
        }
        String sanitized = phone.trim();
        if (sanitized.contains("@desk.msging.net")) {
            sanitized = sanitized.replace("@desk.msging.net", "");
        }
        if (sanitized.contains("%40")) {
            sanitized = sanitized.replace("%40", "@");
        }
        if (sanitized.contains("@")) {
            return sanitized;
        }
        String digits = sanitized.replaceAll("\\D", "");
        return digits + "@wa.gw.msging.net";
    }

    private String resolveAuthorizationKey() {
        String env = System.getenv("APP_APPOINTMENT_BLIP_ROUTER_KEY");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        String key = properties.getBot().getBlipRouterKey();
        return key != null ? key.trim() : "";
    }
}
