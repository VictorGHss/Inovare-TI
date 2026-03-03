package br.dev.ctrls.inovareti.domain.knowledge.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO simples para resposta de upload de anexo genérico.
 * Retorna apenas a URL do arquivo armazenado.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttachmentResponseDTO {

    private String url;
}
