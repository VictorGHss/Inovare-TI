package br.dev.ctrls.inovareti.modules.access.domain.model;

import lombok.Builder;

/**
 * DTO que representa a requisição JSON esperada pela API física da catraca GerAcesso.
 * Campos mapeados para português conforme especificado pela API externa.
 */
@Builder
public record GerAcessoVisitorRequest(
    String cpf,
    int status,
    String nome,
    String telefone,
    String email,
    int tipovisista,
    String matricula_visitado,
    String cpf_visitado,
    String inicio_visita,
    String fim_visita
) {}
