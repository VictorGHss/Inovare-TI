package br.dev.ctrls.inovareti.modules.access.domain.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

/**
 * DTO que representa a requisição JSON esperada pela API física da catraca GerAcesso.
 * Mapeia os campos para português com a grafia exata exigida pela API externa.
 */
@Builder
public record GerAcessoVisitorRequest(
    @JsonProperty("cpf") String cpf,
    @JsonProperty("status") int status,
    @JsonProperty("nome") String nome,
    @JsonProperty("telefone") String telefone,
    @JsonProperty("email") String email,
    @JsonProperty("tipovisista") int tipovisista,
    @JsonProperty("matricula_visitado") String matricula_visitado,
    @JsonProperty("cpf_visitado") String cpf_visitado,
    @JsonProperty("inicio_visita") String inicio_visita,
    @JsonProperty("fim_visita") String fim_visita
) {}
