package br.dev.ctrls.inovareti.modules.access.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Representa o payload de resposta JSON retornado pela API da GerAcesso.
 * Mapeia os campos retornados em português para atributos em inglês.
 * Comentários mantidos em PT-BR.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GerAcessoResponse(
    @JsonProperty("status") String status,
    @JsonProperty("mensagem") String message,
    @JsonProperty("agendamento") Long appointment,
    @JsonProperty("tipo") String type,
    @JsonProperty("pessoa") Long person,
    @JsonProperty("localizador") String locator,
    @JsonProperty("credencial") String credential
) {}
