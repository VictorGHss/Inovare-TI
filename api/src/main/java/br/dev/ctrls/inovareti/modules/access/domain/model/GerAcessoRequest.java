package br.dev.ctrls.inovareti.modules.access.domain.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

/**
 * Representa o payload de requisição JSON enviado para a API GerAcesso.
 * Campos serializados em português para compatibilidade com o servidor da GerAcesso,
 * mantendo a nomeação dos atributos e métodos da classe em inglês.
 * Comentários mantidos em PT-BR.
 */
@Builder
public record GerAcessoRequest(
    @JsonProperty("cpf") String cpf,
    @JsonProperty("status") Integer status,
    @JsonProperty("nome") String name,
    @JsonProperty("telefone") String phone,
    @JsonProperty("email") String email,
    @JsonProperty("tipovisita") Integer visitType,
    @JsonProperty("inicio_visita") String startVisit,
    @JsonProperty("fim_visita") String endVisit
) {}
