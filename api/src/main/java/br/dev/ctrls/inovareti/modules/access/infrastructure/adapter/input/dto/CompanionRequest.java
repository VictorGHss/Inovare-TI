package br.dev.ctrls.inovareti.modules.access.infrastructure.adapter.input.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Representa os dados cadastrais de um acompanhante recebidos no payload da requisição.
 * Comentários mantidos em PT-BR.
 */
public record CompanionRequest(
    @NotBlank(message = "Nome do acompanhante é obrigatório")
    String name,
    
    String cpf,
    
    String phone,
    
    String email
) {}
