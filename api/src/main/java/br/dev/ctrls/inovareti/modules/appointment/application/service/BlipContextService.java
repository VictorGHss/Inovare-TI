package br.dev.ctrls.inovareti.modules.appointment.application.service;

import java.util.Map;
import java.util.UUID;


import br.dev.ctrls.inovareti.modules.appointment.application.dto.AppointmentPayload;
import br.dev.ctrls.inovareti.modules.appointment.application.dto.BlipContactUpdateCommand;
import br.dev.ctrls.inovareti.modules.appointment.application.dto.BlipTextMessage;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.adapter.output.client.BlipLIMEClient;
import br.dev.ctrls.inovareti.modules.appointment.infrastructure.config.BlipProperties;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@org.springframework.stereotype.Service
public class BlipContextService {

    private final BlipLIMEClient limeClient;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final BlipProperties blipProperties;

    @org.springframework.beans.factory.annotation.Value("${APP_BLIP_APPOINTMENT_ID:}")
    private String blipAppointmentId;

    public BlipContextService(BlipLIMEClient limeClient, com.fasterxml.jackson.databind.ObjectMapper objectMapper, BlipProperties blipProperties) {
        this.limeClient = limeClient;
        this.objectMapper = objectMapper;
        this.blipProperties = blipProperties;
    }

    public void setUserContextForUser(String userIdentity, String key, String value) {
        if (userIdentity == null || userIdentity.isBlank()) return;
        String normalizedIdentity = limeClient.normalizeUserIdentity(userIdentity);
        setUserContext(normalizedIdentity, key, value);
    }

    public String getUserContext(String userIdentity, String key) {
        if (userIdentity == null || userIdentity.isBlank() || key == null || key.isBlank()) return null;
        String normalizedIdentity = limeClient.normalizeUserIdentity(userIdentity);

        Map<String, Object> command = Map.of(
            "id", UUID.randomUUID().toString(),
            "to", BlipLIMEClient.MASTER_STATE_COMMAND_TO,
            "method", "get",
            "uri", "/contexts/" + normalizedIdentity + "/" + key
        );

        try {
            Map<String, Object> response = limeClient.executeCommand(command, BlipLIMEClient.AuthorizationScope.ROUTER);
            Map<String, Object> body = response;
            if (body == null) return null;
            Object resource = body.get("resource");
            if (resource == null) return null;

            String value;
            if (resource instanceof Map<?, ?> map) {
                Object rawValue = map.get("value");
                value = rawValue != null ? String.valueOf(rawValue) : null;
            } else {
                value = String.valueOf(resource);
            }

            if (value == null) return null;
            String normalizedValue = value.trim();
            if (normalizedValue.isBlank() || "null".equalsIgnoreCase(normalizedValue)) return null;
            return normalizedValue;
        } catch (org.springframework.web.client.RestClientException ex) {
            log.warn("Falha ao consultar contexto. identity={}, key={}", normalizedIdentity, key, ex);
            return null;
        }
    }

    public void setUserContext(String normalizedIdentity, String key, String value) {
        if (normalizedIdentity == null || normalizedIdentity.isBlank() || value == null || value.isBlank()) return;

        Map<String, Object> command = Map.of(
            "id", UUID.randomUUID().toString(),
            "to", "postmaster@msging.net",
            "method", "set",
            "uri", "/contexts/" + normalizedIdentity + "/" + key,
            "type", "text/plain",
            "metadata", Map.of("expiration", "86400"),
            "resource", value
        );

        try {
            limeClient.executeCommand(command, BlipLIMEClient.AuthorizationScope.ROUTER);
            log.info("Contexto configurado. identity={}, key={}", normalizedIdentity, key);
        } catch (org.springframework.web.client.RestClientException ex) {
            log.warn("Falha ao configurar contexto. identity={}, key={}", normalizedIdentity, key, ex);
        }
    }

    /**
     * Envia um contexto JSON ao Blip via comando LIME.
     * O resource é passado como Object (Map, record, etc.) e serializado para string JSON,
     * sendo enviado como type: "text/plain".
     * Isso evita o erro Code 21 (quando enviado como application/json com string) AND
     * evita o erro de [object Object] / Redirecionamento incorreto no Javascript do Blip
     * (já que o Blip receberá e armazenará uma string JSON pura que o script consegue parsear).
     */
    public void setJsonContext(String normalizedIdentity, String key, Object resourceObject) {
        if (normalizedIdentity == null || normalizedIdentity.isBlank() || resourceObject == null) return;

        try {
            String jsonString = objectMapper.writeValueAsString(resourceObject);

            java.util.LinkedHashMap<String, Object> command = new java.util.LinkedHashMap<>();
            command.put("id", UUID.randomUUID().toString());
            command.put("to", "postmaster@msging.net");
            command.put("method", "set");
            command.put("uri", "/contexts/" + normalizedIdentity + "/" + key);
            command.put("type", "text/plain");
            command.put("metadata", Map.of("expiration", "86400"));
            command.put("resource", jsonString);

            limeClient.executeCommand(command, BlipLIMEClient.AuthorizationScope.ROUTER);
            log.info("[LIME] Contexto JSON configurado como text/plain. identity={}, key={}", normalizedIdentity, key);
        } catch (com.fasterxml.jackson.core.JsonProcessingException | org.springframework.web.client.RestClientException ex) {
            log.warn("[LIME] Falha ao configurar contexto JSON como text/plain. identity={}, key={}", normalizedIdentity, key, ex);
        }
    }

    public void setMasterState(String userIdentity, String botIdentity, String operation) {
        String targetBot = botIdentity != null && !botIdentity.isBlank() ? botIdentity : blipAppointmentId;
        String normalizedIdentity = limeClient.normalizeUserIdentity(userIdentity);

        String stateId = operation != null && !operation.isBlank() ? operation : "stateid";
        String flowId = targetBot.contains("@") ? targetBot.substring(0, targetBot.indexOf('@')) : targetBot;
        String combined = stateId + "@" + flowId;
        String encodedState = java.net.URLEncoder.encode(combined, java.nio.charset.StandardCharsets.UTF_8);

        Map<String, Object> command = Map.of(
            "id", UUID.randomUUID().toString(),
            "to", BlipLIMEClient.MASTER_STATE_COMMAND_TO,
            "method", "set",
            "uri", "/contexts/" + normalizedIdentity + "/" + encodedState,
            "type", "text/plain",
            "metadata", Map.of("expiration", "86400"),
            "resource", targetBot
        );

        try {
            limeClient.executeCommand(command, BlipLIMEClient.AuthorizationScope.ROUTER);
            log.info("Master-State atualizado. operation={}, targetBot={}", operation, targetBot);
        } catch (org.springframework.web.client.RestClientException ex) {
            log.error("Erro ao atualizar Master-State. operation={}", operation, ex);
        }
    }

    public void setUserState(String userIdentity, String stateName) {
        String normalizedIdentity = limeClient.normalizeUserIdentity(userIdentity);

        Map<String, Object> command = Map.of(
                "id", UUID.randomUUID().toString(),
                "to", BlipLIMEClient.MASTER_STATE_COMMAND_TO,
                "method", "set",
                "uri", "/contexts/" + normalizedIdentity + "/state",
                "type", "text/plain",
                "resource", stateName
        );

        try {
            limeClient.executeCommand(command, BlipLIMEClient.AuthorizationScope.ROUTER);
            log.info("User State atualizado. stateName={}", stateName);
        } catch (org.springframework.web.client.RestClientException ex) {
            log.error("Erro ao atualizar User State. stateName={}", stateName, ex);
        }
    }



    private void sendToBlipCommandsApi(Object commandPayload) {
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> commandMap = objectMapper.convertValue(commandPayload, java.util.Map.class);
        limeClient.executeCommand(commandMap, BlipLIMEClient.AuthorizationScope.ROUTER);
    }

    private void sendToBlipMessagesApi(Object messagePayload) {
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> messageMap = objectMapper.convertValue(messagePayload, java.util.Map.class);
        limeClient.executeMessage(messageMap, BlipLIMEClient.AuthorizationScope.ROUTER);
    }

    public void processAppointmentPush(String userPhone, String action, AppointmentPayload payload) {
        try {
            String normalizedPhone = limeClient.normalizeUserIdentity(userPhone);
            String userIdentity = normalizedPhone; // A identidade já possui o domínio correto pós-normalização

            // PASSO 1: Atualiza os dados do Contato no Roteador (Garante consistência imediata)
            BlipContactUpdateCommand contactCommand = new BlipContactUpdateCommand();
            BlipContactUpdateCommand.ContactResource contactResource = new BlipContactUpdateCommand.ContactResource();
            contactResource.setIdentity(userIdentity);
            contactResource.setName(payload.getPatientName());
            contactResource.setTaxDocument(payload.getPatientCPF());

            java.util.Map<String, String> extras = new java.util.HashMap<>();
            extras.put("Medico", payload.getDoctorName());
            extras.put("fila", payload.getQueue());
            extras.put("nascimento", payload.getPatientBirthdate());
            extras.put("data_nascimento", payload.getPatientBirthdate());
            contactResource.setExtras(extras);
            contactCommand.setResource(contactResource);

            // POST para /commands
            log.info("[LIME PUSH] Passo 1: Atualizando contato para identity={}: Medico={}, fila={}", userIdentity, payload.getDoctorName(), payload.getQueue());
            sendToBlipCommandsApi(contactCommand);

            // PASSO 2: Envia ativamente a mensagem de texto (Confirmação ou Alteração) via /messages
            if ("confirm".equalsIgnoreCase(action)) {
                String fullMessage = blipProperties.getTexts().getConfirmSuccess();
                fullMessage = fullMessage.replace("\\n", "\n");

                // MENSAGEM 1: Envio do Mapa Nativo LIME (application/vnd.lime.location+json)
                java.util.Map<String, Object> locationMessage = new java.util.HashMap<>();
                locationMessage.put("id", "msg-" + java.util.UUID.randomUUID().toString());
                locationMessage.put("to", userIdentity);
                locationMessage.put("type", "application/vnd.lime.location+json");
                
                java.util.Map<String, Object> locationContent = new java.util.HashMap<>();
                locationContent.put("latitude", -25.1027718);
                locationContent.put("longitude", -50.1595712);
                locationContent.put("text", "Clínica Inovare - Edifício Inovare");
                locationContent.put("address", "R. Carlos Osternack, 111 - Estrela, Ponta Grossa - PR, 84040-120");
                locationMessage.put("content", locationContent);

                log.info("[LIME PUSH] Passo 2 (Mapa Nativo): Enviando mapa para identity={}", userIdentity);
                sendToBlipMessagesApi(locationMessage);

                // Delay seguro de 800ms
                try {
                    Thread.sleep(800);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("Delay do push interrompido para {}", userIdentity, ie);
                }

                // MENSAGEM 2 (Texto de Avisos): Logo em seguida, envia o restante contendo "🚨 AVISO IMPORTANTE..."
                String warningText = fullMessage;
                int warningIdx = fullMessage.indexOf("🚨 AVISO IMPORTANTE");
                if (warningIdx == -1) {
                    warningIdx = fullMessage.indexOf("AVISO IMPORTANTE");
                }
                if (warningIdx != -1) {
                    warningText = fullMessage.substring(warningIdx);
                }

                BlipTextMessage textMessage = new BlipTextMessage();
                textMessage.setTo(userIdentity);
                textMessage.setContent(warningText);

                log.info("[LIME PUSH] Passo 2 (Avisos): Enviando restante do texto para identity={}", userIdentity);
                sendToBlipMessagesApi(textMessage);
            } else {
                String messageText = blipProperties.getTexts().getAlterRequest()
                  .replace("{patientName}", payload.getPatientName())
                  .replace("{doctorName}", payload.getDoctorName());
                messageText = messageText.replace("\\n", "\n");

                BlipTextMessage textMessage = new BlipTextMessage();
                textMessage.setTo(userIdentity);
                textMessage.setContent(messageText);

                log.info("[LIME PUSH] Passo 2: Enviando mensagem ativa de alteração para identity={}", userIdentity);
                sendToBlipMessagesApi(textMessage);
            }

            // Roteamento delegado ao payload nativo do Blip Builder.
            // Os Passos 3 e 4 (Master-State e stateid) foram removidos intencionalmente:
            // o próprio Blip detecta 'confirm_' ou 'alter_' no payload do botão e faz o desvio
            // de fluxo de forma nativa, eliminando a disputa de estado com o Roteador principal.
            log.info("[MENSAGERIA] Registro processado. Delegando roteamento ao payload nativo do Blip Builder para a identidade: {}", userIdentity);
        } catch (Exception e) {
            throw new RuntimeException("Falha ao executar orquestração de push no Blip", e);
        }
    }

    /**
     * Envia uma mensagem de texto ativa ao paciente solicitando que ele digíte a palavra-chave
     * de gatilho para acionar o atendimento humano no Blip Desk.
     *
     * <p>O Blip Desk exige que a última mensagem do thread seja de autoria do próprio paciente;
     * por isso, ao invés de abrir o ticket diretamente via API, enviamos esta mensagem e aguardamos
     * que o paciente responda — o Builder do Blip então abre o ticket com o histórico textual garantido.
     *
     * @param userIdentity identidade do usuário (telefone normalizado ou raw)
     * @param tipoAcao     tipo da ação confirmada: {@code "confirm"} ou {@code "alter"}
     */
    public void enviarMensagemGatilhoAtendimento(String userIdentity, String tipoAcao) {
        if (userIdentity == null || userIdentity.isBlank()) return;

        String normalizedIdentity = limeClient.normalizeUserIdentity(userIdentity);

        String texto;
        if ("confirm".equalsIgnoreCase(tipoAcao)) {
            texto = "Perfeito! Seu agendamento foi confirmado com sucesso na Feegow. 🤝 "
                  + "Para prosseguir e abrir a sua ficha com a nossa equipe de atendimento, "
                  + "responda esta mensagem digitando apenas a palavra: *ATENDIMENTO*";
        } else {
            texto = "Entendido! Registramos sua solicitação de alteração. 🗓️ "
                  + "Para falar diretamente com uma de nossas secretárias e consultar novos horários, "
                  + "responda esta mensagem digitando apenas a palavra: *ATENDIMENTO*";
        }

        java.util.Map<String, Object> mensagem = new java.util.LinkedHashMap<>();
        mensagem.put("id",   "msg-" + UUID.randomUUID());
        mensagem.put("to",   normalizedIdentity);
        mensagem.put("type", "text/plain");
        mensagem.put("content", texto);

        try {
            limeClient.executeMessage(mensagem, BlipLIMEClient.AuthorizationScope.ROUTER);
            log.info("[GATILHO DESK] Mensagem de gatilho de atendimento enviada com sucesso. "
                    + "identity={}, tipoAcao={}", normalizedIdentity, tipoAcao);
        } catch (Exception ex) {
            log.error("[GATILHO DESK] Falha ao enviar mensagem de gatilho de atendimento. "
                    + "identity={}, tipoAcao={}", normalizedIdentity, tipoAcao, ex);
        }
    }

    public boolean setQueueRedirect(String userIdentity, String queueName) {
        String normalizedIdentity = limeClient.normalizeUserIdentity(userIdentity);

        String safeQueueName = cleanQueueName(queueName);
        if (safeQueueName.isBlank()) {
            safeQueueName = "Recepção Central / Suporte";
            log.warn("[QUEUE] Nome de fila inválido ('{}') substituído por fallback: '{}'", queueName, safeQueueName);
        }

        if (!safeQueueName.isBlank()
                && !"Recepção Central / Suporte".equalsIgnoreCase(safeQueueName)
                && !"Recepção".equalsIgnoreCase(safeQueueName)
                && !safeQueueName.contains(" - ")) {
            log.warn("[QUEUE WARNING] Nome da fila pode estar incompleto para o Desk. fila='{}'", safeQueueName);
        }

        Map<String, Object> deleteCommand = Map.of(
            "id", UUID.randomUUID().toString(),
            "to", BlipLIMEClient.MASTER_STATE_COMMAND_TO,
            "method", "delete",
            "uri", "/contexts/" + normalizedIdentity + "/attendanceQueueToRedirect"
        );

        try {
            limeClient.executeCommand(deleteCommand, BlipLIMEClient.AuthorizationScope.ROUTER);
            log.debug("[QUEUE] Contexto anterior removido. identity={}", normalizedIdentity);
        } catch (org.springframework.web.client.RestClientException ex) {
            log.warn("[QUEUE] Falha ao limpar contexto anterior. identity={}", normalizedIdentity, ex);
        }

        Map<String, Object> command = Map.of(
            "id", UUID.randomUUID().toString(),
            "to", BlipLIMEClient.MASTER_STATE_COMMAND_TO,
            "method", "set",
            "uri", "/contexts/" + normalizedIdentity + "/attendanceQueueToRedirect",
            "type", "text/plain",
            "resource", safeQueueName
        );

        try {
            limeClient.executeCommand(command, BlipLIMEClient.AuthorizationScope.ROUTER);
            log.info("Fila de redirecionamento configurada no contexto. identity={}, fila={}", normalizedIdentity, safeQueueName);
            return true;
        } catch (org.springframework.web.client.RestClientException ex) {
            log.warn("Falha ao configurar fila no contexto. identity={}, fila={}", normalizedIdentity, safeQueueName, ex);
            return false;
        }
    }

    public String cleanQueueName(String queueName) {
        if (queueName == null) return "";
        String cleaned = queueName.replace("\u200E", "");
        cleaned = cleaned.replaceAll("(?i)null", "");
        cleaned = cleaned.replaceAll("\\s+", " ");
        cleaned = cleaned.trim();
        return cleaned;
    }
}