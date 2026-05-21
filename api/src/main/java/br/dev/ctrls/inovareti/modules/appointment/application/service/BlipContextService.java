package br.dev.ctrls.inovareti.modules.appointment.application.service;

import java.util.Map;
import java.util.UUID;


import br.dev.ctrls.inovareti.modules.appointment.application.dto.AppointmentPayload;
import br.dev.ctrls.inovareti.modules.appointment.application.dto.BlipContactUpdateCommand;
import br.dev.ctrls.inovareti.modules.appointment.application.dto.BlipMasterStateCommand;
import br.dev.ctrls.inovareti.modules.appointment.application.dto.BlipStateChangeCommand;
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

    public void setUserState(String userIdentity, String flowId, String blockId) {
        String normalizedIdentity = limeClient.normalizeUserIdentity(userIdentity);
        String uri = "/contexts/" + normalizedIdentity + "/stateid@" + flowId;

        Map<String, Object> command = Map.of(
                "id", UUID.randomUUID().toString(),
                "to", BlipLIMEClient.MASTER_STATE_COMMAND_TO,
                "method", "set",
                "uri", uri,
                "type", "text/plain",
                "resource", blockId
        );

        try {
            limeClient.executeCommand(command, BlipLIMEClient.AuthorizationScope.ROUTER);
            log.info("User State (Teletransporte Interno) atualizado. flowId={}, blockId={}", flowId, blockId);
        } catch (org.springframework.web.client.RestClientException ex) {
            log.error("Erro ao atualizar User State (Teletransporte Interno). flowId={}", flowId, ex);
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

            // PASSO 3: Define o Master-State (Informa ao Roteador que o usuário pertence ao fluxo v1)
            BlipMasterStateCommand masterStateCommand = new BlipMasterStateCommand();
            masterStateCommand.setUri("/contexts/" + userIdentity + "/Master-State");
            masterStateCommand.setResource(blipProperties.getSubbotId());

            // POST para /commands
            log.info("[LIME PUSH] Passo 3: Definindo Master-State para identity={}", userIdentity);
            sendToBlipCommandsApi(masterStateCommand);

            // PASSO 4: Teleporta o Usuário direto para o bloco Preparar_Atendimento
            BlipStateChangeCommand stateCommand = new BlipStateChangeCommand();
            stateCommand.setUri("/contexts/" + userIdentity + "/stateid@" + blipProperties.getFlowId());
            
            String targetBlockId = blipProperties.getBlocks().getPrepararAtendimento();
            if (targetBlockId == null || targetBlockId.isBlank()) {
                targetBlockId = "a0776d9c-6486-42f3-8a4f-2706f0185908";
            } else {
                targetBlockId = targetBlockId.trim();
            }
            if ("confirm".equalsIgnoreCase(action)) {
                targetBlockId = "a0776d9c-6486-42f3-8a4f-2706f0185908";
            }
            stateCommand.setResource(targetBlockId);

            // POST para /commands
            log.info("[LIME PUSH] Passo 4: Teleportando usuário identity={} para o bloco Preparar_Atendimento ({})", userIdentity, targetBlockId);
            sendToBlipCommandsApi(stateCommand);

            // PASSO 5: Injeção de variável de transição para acionar o Desk via input textual do paciente.
            // O Blip Desk exige que a última mensagem do thread seja digitada pelo próprio usuário (código 67).
            // Por isso, em vez de abrir o ticket diretamente, injetamos uma variável de contexto e aguardamos
            // que o paciente digite uma mensagem no nó de confirmação do Builder para então acionar o Desk.
            if ("confirm".equalsIgnoreCase(action)) {
                final String identityParaDesk = userIdentity;
                final String nomeParaDesk     = payload.getPatientName();

                java.util.concurrent.CompletableFuture.runAsync(() -> {
                    try {
                        // Aguarda processamento dos passos síncronos anteriores pelo Builder
                        Thread.sleep(2000);

                        // Passo 5a: Upsert defensivo de contato no índice do Desk (preparação prévia)
                        log.info("[DESK] Passo 5a (Assíncrono): Executando upsert defensivo de contato no Desk para identity={}",
                                identityParaDesk);

                        java.util.LinkedHashMap<String, Object> contatoDesk = new java.util.LinkedHashMap<>();
                        contatoDesk.put("id",     UUID.randomUUID().toString());
                        contatoDesk.put("to",     "postmaster@desk.msging.net");
                        contatoDesk.put("method", "merge");
                        contatoDesk.put("uri",    "/contacts");
                        contatoDesk.put("type",   "application/vnd.lime.contact+json");

                        java.util.Map<String, Object> contatoResource = new java.util.HashMap<>();
                        contatoResource.put("identity", identityParaDesk);
                        contatoResource.put("name",
                                nomeParaDesk != null && !nomeParaDesk.isBlank() ? nomeParaDesk : identityParaDesk);
                        contatoDesk.put("resource", contatoResource);

                        Map<String, Object> respostaContato = limeClient.executeCommand(
                                contatoDesk, BlipLIMEClient.AuthorizationScope.DESK);

                        Object statusContatoRaw = respostaContato != null ? respostaContato.get("status") : null;
                        String statusContato = statusContatoRaw != null ? String.valueOf(statusContatoRaw) : "nulo";

                        if ("failure".equalsIgnoreCase(statusContato)) {
                            Object reason = respostaContato.get("reason");
                            String codigo  = "N/A";
                            String detalhe = "N/A";
                            if (reason instanceof Map<?, ?> reasonMap) {
                                Object c = reasonMap.get("code");
                                Object d = reasonMap.get("description");
                                codigo  = c != null ? String.valueOf(c) : "N/A";
                                detalhe = d != null ? String.valueOf(d) : "N/A";
                            }
                            log.warn("[DESK AVISO] Upsert de contato retornou falha. A variável de transição será injetada mesmo assim. " +
                                    "identity={}, Código: {}, Detalhes: {}", identityParaDesk, codigo, detalhe);
                        } else {
                            log.info("[DESK] Upsert de contato concluído com status='{}'. identity={}",
                                    statusContato, identityParaDesk);
                        }

                        // Passo 5b: Injeta variável de transição no contexto do Roteador.
                        // O webhook interceptará este valor quando o paciente digitar o texto de confirmação.
                        log.info("[DESK] Passo 5b (Assíncrono): Injetando variável de transição 'status_acao_inovare' para identity={}",
                                identityParaDesk);

                        Map<String, Object> varTransicao = new java.util.LinkedHashMap<>();
                        varTransicao.put("id",       UUID.randomUUID().toString());
                        varTransicao.put("to",       BlipLIMEClient.MASTER_STATE_COMMAND_TO);
                        varTransicao.put("method",   "set");
                        varTransicao.put("uri",      "/contexts/" + identityParaDesk + "/status_acao_inovare");
                        varTransicao.put("type",     "text/plain");
                        varTransicao.put("metadata", Map.of("expiration", "86400"));
                        varTransicao.put("resource", "AGUARDANDO_CONFIRMACAO_DESK");

                        limeClient.executeCommand(varTransicao, BlipLIMEClient.AuthorizationScope.ROUTER);
                        log.info("[DESK] Variável de transição injetada com sucesso. O Desk será acionado quando o paciente digitar o texto de validação. identity={}",
                                identityParaDesk);

                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.warn("[DESK] Processo assíncrono de injeção de variável interrompido para identity={}",
                                identityParaDesk, ie);
                    } catch (Exception e) {
                        log.error("[DESK ERROR] Erro inesperado na preparação assíncrona do transbordo para identity={}",
                                identityParaDesk, e);
                    }
                });
            }

            log.info("Push síncrono finalizado com sucesso. Contato atualizado, mensagens ativas enviadas, usuário teleportado e variável de transição agendada.");
        } catch (Exception e) {
            throw new RuntimeException("Falha ao executar orquestração de push no Blip", e);
        }
    }

    /**
     * Abre um ticket no Blip Desk para o usuário informado.
     *
     * <p>Este método é chamado pelo interceptor do webhook ({@code HandleBlipWebhookUseCase}) quando
     * o paciente envia o texto de validação após a confirmação de agendamento. O histórico de mensagem
     * digitada pelo próprio usuário já está garantido neste momento, eliminando o erro 67 do Desk.
     *
     * <p>Fluxo interno:
     * <ol>
     *   <li>Envia comando {@code set /tickets} para {@code postmaster@desk.msging.net}</li>
     *   <li>Loga sucesso ou falha detalhada com código e descrição do Blip</li>
     *   <li>Limpa a variável de transição {@code status_acao_inovare} do contexto</li>
     * </ol>
     *
     * @param userIdentity identidade normalizada do usuário (ex: {@code 5511999887766@wa.gw.msging.net})
     */
    public void abrirTicketDesk(String userIdentity) {
        if (userIdentity == null || userIdentity.isBlank()) return;

        String normalizedIdentity = limeClient.normalizeUserIdentity(userIdentity);

        log.info("[DESK] Abrindo ticket no Blip Desk após confirmação textual do paciente. identity={}",
                normalizedIdentity);

        // Monta o payload do ticket
        java.util.Map<String, Object> customerInput = new java.util.HashMap<>();
        customerInput.put("type",  "text/plain");
        customerInput.put("value", "Confirmação de atendimento recebida. Paciente aguardando atendimento humano.");

        java.util.Map<String, Object> ticketResource = new java.util.HashMap<>();
        ticketResource.put("customerIdentity", normalizedIdentity);
        ticketResource.put("customerInput",    customerInput);

        java.util.Map<String, Object> ticketCommand = new java.util.HashMap<>();
        ticketCommand.put("id",       UUID.randomUUID().toString());
        ticketCommand.put("to",       "postmaster@desk.msging.net");
        ticketCommand.put("method",   "set");
        ticketCommand.put("uri",      "/tickets");
        ticketCommand.put("type",     "application/vnd.iris.ticket+json");
        ticketCommand.put("resource", ticketResource);

        try {
            Map<String, Object> respostaTicket = limeClient.executeCommand(
                    ticketCommand, BlipLIMEClient.AuthorizationScope.DESK);

            Object statusRaw = respostaTicket != null ? respostaTicket.get("status") : null;
            String statusTicket = statusRaw != null ? String.valueOf(statusRaw) : "nulo";

            if ("failure".equalsIgnoreCase(statusTicket)) {
                Object reason  = respostaTicket.get("reason");
                String codigo  = "N/A";
                String detalhe = "N/A";
                if (reason instanceof Map<?, ?> reasonMap) {
                    Object c = reasonMap.get("code");
                    Object d = reasonMap.get("description");
                    codigo  = c != null ? String.valueOf(c) : "N/A";
                    detalhe = d != null ? String.valueOf(d) : "N/A";
                }
                log.error("[DESK ERROR] Falha crítica ao abrir ticket no Blip Desk. Código: {}, Detalhes: {}",
                        codigo, detalhe);
            } else {
                log.info("[DESK] Transbordo para atendimento humano concluído com sucesso. " +
                        "Ticket aberto para identity: {}", normalizedIdentity);
            }
        } catch (Exception ex) {
            log.error("[DESK ERROR] Exceção ao tentar abrir ticket no Blip Desk para identity={}",
                    normalizedIdentity, ex);
        } finally {
            // Limpa a variável de transição independentemente do resultado
            limparVariavelTransicaoDesk(normalizedIdentity);
        }
    }

    /**
     * Remove a variável de transição {@code status_acao_inovare} do contexto do Roteador,
     * evitando que um segundo texto digitado pelo paciente dispare um novo ticket.
     *
     * @param normalizedIdentity identidade já normalizada do usuário
     */
    public void limparVariavelTransicaoDesk(String normalizedIdentity) {
        try {
            Map<String, Object> deleteCmd = Map.of(
                "id",     UUID.randomUUID().toString(),
                "to",     BlipLIMEClient.MASTER_STATE_COMMAND_TO,
                "method", "delete",
                "uri",    "/contexts/" + normalizedIdentity + "/status_acao_inovare"
            );
            limeClient.executeCommand(deleteCmd, BlipLIMEClient.AuthorizationScope.ROUTER);
            log.debug("[DESK] Variável de transição 'status_acao_inovare' removida do contexto. identity={}",
                    normalizedIdentity);
        } catch (Exception ex) {
            log.warn("[DESK] Falha ao remover variável de transição do contexto. identity={}", normalizedIdentity, ex);
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