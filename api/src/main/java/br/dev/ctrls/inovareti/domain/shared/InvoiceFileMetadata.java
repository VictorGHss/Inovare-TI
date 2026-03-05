package br.dev.ctrls.inovareti.domain.shared;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Metadados de um arquivo de nota fiscal armazenado.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceFileMetadata {
    /** Nome do arquivo (ex: asset-{uuid}-{timestamp}.pdf). */
    private String fileName;

    /** Tipo MIME do arquivo (ex: application/pdf, image/png). */
    private String contentType;

    /** Caminho completo do arquivo no disco (ex: uploads/invoices/asset-{uuid}-{timestamp}.pdf). */
    private String filePath;
}
