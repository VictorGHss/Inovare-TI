package br.dev.ctrls.inovareti.modules.access.domain.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Representa o payload de resposta JSON retornado pela API da GerAcesso.
 * Mapeia os campos retornados em português ou inglês para atributos em inglês.
 * Comentários mantidos em PT-BR.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GerAcessoResponse(
    @JsonProperty("status") @JsonAlias({"status", "statusCode", "code"}) String status,
    @JsonProperty("mensagem") @JsonAlias({"mensagem", "message", "msg"}) String message,
    @JsonProperty("agendamento") @JsonAlias({"agendamento", "appointment"}) Long appointment,
    @JsonProperty("tipo") @JsonAlias({"tipo", "type"}) String type,
    @JsonProperty("pessoa") @JsonAlias({"pessoa", "person"}) Long person,
    @JsonProperty("localizador") @JsonAlias({"localizador", "locator", "loc"}) String locator,
    @JsonProperty("credencial") @JsonAlias({"credencial", "credential", "qrcode", "codigo", "token"}) String credential
) {}
