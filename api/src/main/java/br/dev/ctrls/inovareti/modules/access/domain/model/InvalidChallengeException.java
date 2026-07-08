package br.dev.ctrls.inovareti.modules.access.domain.model;

/**
 * Exceção de negócio lançada quando o desafio de validação de telefone falha.
 * Comentários em PT-BR pelas Regras de Ouro.
 */
public class InvalidChallengeException extends RuntimeException {
    
    public InvalidChallengeException(String message) {
        super(message);
    }
}
