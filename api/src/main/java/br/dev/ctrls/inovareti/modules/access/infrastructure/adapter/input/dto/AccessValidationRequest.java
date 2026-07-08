package br.dev.ctrls.inovareti.modules.access.infrastructure.adapter.input.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * Representa o payload JSON recebido para validação e liberação de acesso físico.
 * Comentários mantidos em PT-BR.
 */
public record AccessValidationRequest(
    @NotBlank(message = "O ID do agendamento é obrigatório")
    String appointmentId,
    
    String cpf,
    
    List<CompanionRequest> companions
) {}
