package br.dev.ctrls.inovareti.domain.admin;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.springframework.stereotype.Component;

import br.dev.ctrls.inovareti.domain.user.UserRole;
import lombok.extern.slf4j.Slf4j;

/**
 * Componente responsável exclusivamente pelo parsing e validação de linhas CSV.
 * Não possui dependências de banco de dados — toda a lógica é stateless e pura.
 *
 * Formato esperado: UserName;UserEmail;UserRole;SectorName;AssetName;AssetCategory;PatrimonyCode;AssetSpecs
 * (Separador: ponto-e-vírgula para compatibilidade com Excel em PT-BR)
 */
@Component
@Slf4j
public class CsvRowParser {

    /**
     * Faz o parse de uma linha CSV e retorna um {@link CsvImportRow} validado.
     */
    public CsvImportRow parseLine(String line, int lineNumber) {
        String[] columns = line.split(";", -1);

        if (columns.length < 4) {
            throw new IllegalArgumentException("Formato de linha inválido. São esperadas no mínimo 4 colunas.");
        }

        String userName          = extractAndTrim(columns, 0);
        String userEmail         = extractAndTrim(columns, 1);
        String userRoleStr       = extractAndTrim(columns, 2);
        String sectorName        = extractAndTrim(columns, 3);
        String assetName         = extractAndTrim(columns, 4);
        String assetCategoryName = extractAndTrim(columns, 5);
        String patrimonyCode     = extractAndTrim(columns, 6);
        String assetSpecs        = extractAndTrim(columns, 7);

        if (userEmail.isEmpty()) {
            throw new IllegalArgumentException("UserEmail é obrigatório");
        }

        return new CsvImportRow(lineNumber, userName, userEmail, userRoleStr,
                sectorName, assetName, assetCategoryName, patrimonyCode, assetSpecs);
    }

    /**
     * Converte a string de role para o enum {@link UserRole}, com fallback para USER.
     */
    public UserRole parseUserRole(String roleStr) {
        if (roleStr == null || roleStr.isEmpty()) {
            return UserRole.USER;
        }
        try {
            return UserRole.valueOf(roleStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid role '{}', defaulting to USER", roleStr);
            return UserRole.USER;
        }
    }

    /**
     * Retorna o primeiro valor não-blank extraído de uma lista de linhas através do extractor fornecido.
     */
    public Optional<String> firstNonBlank(List<CsvImportRow> rows, Function<CsvImportRow, String> extractor) {
        return rows.stream()
                .map(extractor)
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }

    /**
     * Extrai um valor do array de colunas CSV e aplica normalização de espaços.
     * Retorna string vazia se o índice estiver fora dos limites ou o valor for nulo.
     */
    private String extractAndTrim(String[] columns, int index) {
        if (index < 0 || index >= columns.length) {
            return "";
        }
        String value = columns[index];
        return value == null ? "" : value.strip();
    }
}
