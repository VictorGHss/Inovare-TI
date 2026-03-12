package br.dev.ctrls.inovareti.domain.admin;

/**
 * Representa uma linha do arquivo CSV já parseada e validada.
 * Objeto de valor imutável (record) — sem dependências externas.
 */
public record CsvImportRow(
        int lineNumber,
        String userName,
        String userEmail,
        String userRoleStr,
        String sectorName,
        String assetName,
        String assetCategoryName,
        String patrimonyCode,
        String assetSpecs
) {}
