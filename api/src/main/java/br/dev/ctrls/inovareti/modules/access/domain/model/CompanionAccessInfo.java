package br.dev.ctrls.inovareti.modules.access.domain.model;

/**
 * Representa os dados de acesso de um acompanhante no domínio.
 * Mantido na camada de domínio para respeitar as regras de arquitetura hexagonal do ArchUnit.
 * Comentários mantidos em PT-BR.
 */
public record CompanionAccessInfo(
    String name,
    String cpf,
    String phone,
    String email
) {}
