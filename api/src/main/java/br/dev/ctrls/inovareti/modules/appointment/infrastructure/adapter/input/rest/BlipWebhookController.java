package br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.input.rest;

import io.micrometer.observation.annotation.Observed;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.dev.ctrls.inovareti.config.security.WebhookSignatureValidator;
import br.dev.ctrls.inovareti.modules.appointment.application.service.BlipWebhookInboundService;
import br.dev.ctrls.inovareti.modules.appointment.application.usecase.HandleBlipWebhookUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Controller que gerencia a recepção de webhooks de mensagens e notificações da Blip.
 * Implementa validação criptográfica de integridade HMAC-SHA256 e prevenção de duplicidade.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Webhooks - Blip", description = "Endpoints de integração de webhooks para o motor de agendamentos e controle da Blip")
@Observed
public class BlipWebhookController {

    private final HandleBlipWebhookUseCase handleBlipWebhookUseCase;
    private final BlipWebhookInboundService blipWebhookInboundService;
    private final ObjectMapper objectMapper;
    private final WebhookSignatureValidator webhookSignatureValidator;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final Environment env;

    @Value("${blip.webhook.secret}")
    private String blipWebhookSecret;

    @Value("${blip.webhook.token:}")
    private String blipWebhookToken;

    // Cache local em memória concorrente com expiração para garantir resiliência caso o Redis esteja indisponível
    private final Map<String, Long> processedEventsCache = new java.util.concurrent.ConcurrentHashMap<>();

    @Operation(
        summary = "Recebe e processa webhooks enviados pelo Blip",
        description = "Este endpoint recebe as mensagens enviadas pela plataforma Blip, executa a validação de assinatura criptográfica HMAC-SHA256 (X-Blip-Signature) para atestar a autenticidade e aplica controle de idempotência de eventos."
    )
    @PostMapping(value = {"/v1/webhook/blip", "/webhooks/blip"},
        consumes = {
            MediaType.APPLICATION_JSON_VALUE,
            "application/vnd.lime.select+json",
            "application/vnd.lime.reply+json"
        })
    public ResponseEntity<?> blipWebhook(
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-Inovare-Token", required = false) String inovareToken,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-Blip-Signature", required = false) String blipSignature,
            jakarta.servlet.http.HttpServletRequest request,
            @RequestBody(required = false) String rawJson) {

        log.info("Recebido POST em /api/v1/webhook/blip. Payload bruto: {}", rawJson);
        log.debug("[ALERTA REDE] Requisição bruta da Take Blip ACABOU de tocar o Tomcat na porta 8085!");

        if (rawJson == null || rawJson.isBlank()) {
            log.warn("Blip webhook recebido sem corpo no payload em /v1/webhook/blip.");
            return ResponseEntity.ok(Map.of(
                    "status", "ignored",
                    "reason", "body-empty"));
        }

        // 1. VALIDAí‡íO DE ASSINATURA CRIPTOGRí FICA (HMAC-SHA256)
        byte[] bodyBytes = null;
        if (request instanceof org.springframework.web.util.ContentCachingRequestWrapper wrappedRequest) {
            bodyBytes = wrappedRequest.getContentAsByteArray();
        }
        if (bodyBytes == null || bodyBytes.length == 0) {
            bodyBytes = rawJson.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }

        boolean isSignatureValid = webhookSignatureValidator.isValid(bodyBytes, blipSignature, blipWebhookSecret);

        String expectedToken = StringUtils.hasText(blipWebhookToken)
            ? blipWebhookToken
            : System.getenv("APP_BLIP_SECURITY_WEBHOOK_TOKEN");

        boolean hasTokenMatch = StringUtils.hasText(inovareToken)
            && StringUtils.hasText(expectedToken)
            && expectedToken.equals(inovareToken);

        // O bypass de assinatura por token é aceito quando o token confiável confere,
        // mesmo em produção. Em local/default, aceita qualquer token não vazio.
        boolean isBypassProfile = env.acceptsProfiles(Profiles.of("local", "default"));
        boolean isBypassEnabled = hasTokenMatch
            || (isBypassProfile && StringUtils.hasText(inovareToken));

        if (!isSignatureValid && !isBypassEnabled) {
            log.warn("[ACESSO NEGADO] Assinatura do webhook inválida ou ausente. Bypass por token inativo no perfil de produção.");
            return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).build();
        }

        if (!isSignatureValid && isBypassEnabled) {
            log.info("[BYPASS] Assinatura ausente ou inválida, mas acesso liberado por token confiável configurado.");
        }

        // 2. FAST-FAIL GUARD (Early Return): Validação estrutural genérica por padrões
        String rawLower = rawJson.toLowerCase();

        // Compila uma Regex genérica para capturar QUALQUER UUID presente no JSON bruto
        boolean containsAnyUuid = rawLower.matches(".*[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}.*");

        boolean hasActionKeyword = containsAnyUuid
                || rawLower.contains("confirm_")
                || rawLower.contains("alter_")
                || rawLower.contains("cancel_")
                || rawLower.contains("confirmar")
                || rawLower.contains("alterar")
                || rawLower.contains("cancelar")
                || rawLower.contains("ver agendamentos")
                || rawLower.contains("ver_agendamentos")
                || rawLower.contains("group_view_fallback")
                || rawLower.contains("preparar_atendimento")
                || rawLower.contains("exibir_agenda");

        if (!hasActionKeyword) {
            return ResponseEntity.ok().build(); // Ignora spams irrelevantes de forma segura e veloz
        }

        Map<String, Object> payload;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(rawJson, Map.class);
            payload = map != null ? map : Map.of();
        } catch (com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException e) {
            log.error("Erro ao realizar o parse do JSON do webhook da Blip", e);
            return ResponseEntity.badRequest().body(Map.of(
                "status", "ignored",
                "reason", "invalid-json"
            ));
        }

        BlipWebhookInboundService.ParsedInbound parsed = blipWebhookInboundService.parse(payload);

        String from = parsed.from();
        String action = parsed.action();
        String messageId = parsed.messageId();
        String appointmentId = parsed.appointmentId();
        Object content = parsed.content();

        // --- SAFETY-GUARD DE SEGURANí‡A ESTRUTURAL (REJEITA PAYLOADS MALFORMADOS COM 400 BAD REQUEST) ---
        if (!StringUtils.hasText(from) || !StringUtils.hasText(messageId)) {
            log.warn("[SAFETY-GUARD] Payload Blip/LIME estruturalmente inválido. from={}, messageId={}", from, messageId);
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "reason", "bad-request",
                "message", "Envelope LIME estruturalmente invalido: 'from' e 'id' sao obrigatorios."
            ));
        }
        // -----------------------------------------------------------------------------------------------

        // 3. IDEMPOTíŠNCIA (Prevenção de Duplicidade): Early Return 200 se for duplicado
        if (!isFirstTimeProcessing(messageId)) {
            log.info("[IDEMPOTíŠNCIA] Evento duplicado ignorado. messageId='{}'", messageId);
            return ResponseEntity.ok(Map.of(
                    "status", "processed",
                    "reason", "duplicate-ignored"
            ));
        }

        Map<String, Object> metadata = extractMetadata(payload);

        log.info("[WEBHOOK BLIP] Resposta recebida do paciente. Telefone: {}, Ação: {}", from, action);

        HandleBlipWebhookUseCase.WebhookResult result = handleBlipWebhookUseCase.execute(new HandleBlipWebhookUseCase.BlipWebhookPayload(
                messageId,
                appointmentId,
                action,
                from,
                inovareToken,
                content,
                metadata,
                parsed.bsuid()));

        if (result == null) {
            return ResponseEntity.ok(Map.of("status", "processed", "queue", ""));
        }

        return ResponseEntity.ok(new WebhookResponse(
            Objects.requireNonNullElse(result.queue(), ""),
            Objects.requireNonNullElse(result.patientName(), ""),
            Objects.requireNonNullElse(result.patientCPF(), ""),
            Objects.requireNonNullElse(result.patientBirthdate(), ""),
            Objects.requireNonNullElse(result.action(), ""),
            Objects.requireNonNullElse(result.doctorName(), "")
        ));
    }

    @Operation(
        summary = "Dispara manualmente fluxos de agendamento por API",
        description = "Permite simular ou forçar requisições de webhooks Blip de forma controlada via painel administrativo para depuração e testes."
    )
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(value = "/webhooks/blip/manual-trigger", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> manualTrigger(
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-Inovare-Token", required = false) String inovareToken,
            @RequestBody ManualTriggerRequest body) {
        if (body == null
                || !StringUtils.hasText(body.identity())
                || !StringUtils.hasText(body.appointmentId())
                || !StringUtils.hasText(body.action())) {
            return ResponseEntity.badRequest().body(Map.of(
                "status", "ignored",
                "reason", "missing-fields"));
        }

        String normalizedAction = body.action().trim().toLowerCase();
        String actionPrefix = switch (normalizedAction) {
            case "confirm" -> "confirm";
            case "alter" -> "alter";
            default -> null;
        };

        if (actionPrefix == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "status", "ignored",
                "reason", "invalid-action"));
        }

        String appointmentId = body.appointmentId().trim();
        String action = actionPrefix + "_" + appointmentId;

        log.info("[MANUAL TRIGGER] identity='{}' | action='{}' | appointmentId='{}'",
            body.identity(), action, appointmentId);

        handleBlipWebhookUseCase.execute(new HandleBlipWebhookUseCase.BlipWebhookPayload(
                UUID.randomUUID().toString(),
                appointmentId,
                action,
                body.identity().trim(),
                inovareToken,
                null,
                Map.of()), true);

        return ResponseEntity.ok(Map.of());
    }

    /**
     * Verifica e registra o processamento do evento para garantir idempotência de forma resiliente.
     *
     * @param messageId ID único do evento/mensagem
     * @return true se for o primeiro processamento (deve processar), false se for duplicado (deve ignorar)
     */
    private boolean isFirstTimeProcessing(String messageId) {
        if (!StringUtils.hasText(messageId)) {
            return true; // Fail-open para mensagens sem identificação
        }

        String cacheKey = "webhook:idempotency:blip:" + messageId.trim();
        StringRedisTemplate redis = redisTemplateProvider.getIfAvailable();

        if (redis != null) {
            try {
                // Tenta registrar no Redis de forma atômica com expiração de 24 horas
                Boolean success = redis.opsForValue().setIfAbsent(cacheKey, "1", java.time.Duration.ofHours(24));
                if (success != null) {
                    return success;
                }
            } catch (Exception ex) {
                log.warn("Redis indisponível para registro de idempotência. Ativando fallback em memória local: {}", ex.getMessage());
            }
        }

        // Fallback em memória
        long now = System.currentTimeMillis();

        // Evita vazamento de memória do cache local se o mapa crescer além do limite de 10.000 entradas
        if (processedEventsCache.size() > 10000) {
            processedEventsCache.entrySet().removeIf(entry -> entry.getValue() < now);
        }

        // Verifica se a chave existe no cache local e se ela ainda é válida
        Long expiration = processedEventsCache.get(messageId);
        if (expiration != null && expiration > now) {
            return false;
        }

        // Registra o ID no cache em memória por 1 hora
        processedEventsCache.put(messageId, now + 3600_000L);
        return true;
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

    public record ManualTriggerRequest(
            @JsonProperty("identity") String identity,
            @JsonAlias({"appointment_id", "appointmentId"}) @JsonProperty("appointment_id") String appointmentId,
            @JsonProperty("action") String action) {
    }

    public record WebhookResponse(
            @JsonProperty("queue") String queue,
            @JsonProperty("patientName") String patientName,
            @JsonProperty("patientCPF") String patientCPF,
            @JsonProperty("patientBirthdate") String patientBirthdate,
            @JsonProperty("action") String action,
            @JsonProperty("doctorName") String doctorName) {
    }
}


