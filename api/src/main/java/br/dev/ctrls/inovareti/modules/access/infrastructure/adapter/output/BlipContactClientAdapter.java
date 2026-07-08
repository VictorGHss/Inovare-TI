package br.dev.ctrls.inovareti.modules.access.infrastructure.adapter.output;

import br.dev.ctrls.inovareti.modules.access.domain.port.output.BlipContactClientPort;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.AppointmentMotorProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class BlipContactClientAdapter implements BlipContactClientPort {

    private final AppointmentMotorProperties properties;
    private RestClient restClient;

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

        log.info("[BlipContact-Adapter] Sincronizando contato ativamente no Blip. Identity={}, Nome={}, CPF={}, Fila={}",
                normalizedIdentity, name, cpf, queueName);

        String authKey = resolveAuthorizationKey();
        if (!authKey.startsWith("Key ")) {
            authKey = "Key " + authKey;
        }

        // Montagem do payload de comando da API LIME do Blip
        Map<String, Object> command = Map.of(
            "id", "sync-contact-" + UUID.randomUUID().toString(),
            "to", "postmaster@msging.net",
            "method", "set",
            "uri", "/contacts",
            "type", "application/vnd.lime.contact+json",
            "resource", Map.of(
                "identity", normalizedIdentity,
                "name", name != null ? name.trim() : "",
                "extras", Map.of(
                    "cpf", cpf != null ? cpf.replaceAll("\\D", "") : "",
                    "fila", queueName != null ? queueName.trim() : "",
                    "deskFila", queueName != null ? queueName.trim() : ""
                )
            )
        );

        try {
            String path = properties.getBlipSetContextPath();
            if (path == null || path.isBlank()) {
                path = "/commands";
            }

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
                    log.info("[BlipContact-Adapter] Sincronização concluída com sucesso no Blip para {}", normalizedIdentity);
                    return true;
                } else {
                    log.warn("[BlipContact-Adapter] Blip retornou status de falha no comando: {}. Body={}", status, response.getBody());
                }
            } else {
                log.warn("[BlipContact-Adapter] Blip respondeu com HTTP status: {}", response.getStatusCode());
            }

        } catch (Exception ex) {
            log.error("[BlipContact-Adapter] Falha de comunicação/rede com o Blip para a identidade {}. Erro: {}",
                    normalizedIdentity, ex.getMessage(), ex);
        }

        return false;
    }

    private String normalizeIdentity(String phone) {
        String sanitized = phone.trim();
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
