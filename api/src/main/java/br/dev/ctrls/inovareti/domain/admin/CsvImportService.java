package br.dev.ctrls.inovareti.domain.admin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import br.dev.ctrls.inovareti.domain.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Orquestrador da importação CSV.
 * Responsabilidade única: ler o arquivo, delegar o parsing para {@link CsvRowParser}
 * e coordenar a persistência via {@link CsvPersistenceService}.
 *
 * Formato CSV: UserName;UserEmail;UserRole;SectorName;AssetName;AssetCategory;PatrimonyCode;AssetSpecs
 * (Separador: ponto-e-vírgula para compatibilidade com Excel em PT-BR)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CsvImportService {

    private final CsvRowParser csvRowParser;
    private final CsvPersistenceService csvPersistenceService;

    @Transactional
    public ImportResultDTO importCsv(MultipartFile file) {
        log.info("Starting CSV import from file: {}", file.getOriginalFilename());

        ImportResultDTO result = new ImportResultDTO();
        List<String> errors = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String line;
            int lineNumber = 0;
            List<CsvImportRow> parsedRows = new ArrayList<>();

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (lineNumber == 1) continue; // pula cabeçalho
                if (line.trim().isEmpty()) continue;

                try {
                    parsedRows.add(csvRowParser.parseLine(line, lineNumber));
                } catch (Exception e) {
                    String errorMsg = String.format("Erro na linha %d: %s", lineNumber, e.getMessage());
                    log.error(errorMsg, e);
                    errors.add(errorMsg);
                }
            }

            Map<String, List<CsvImportRow>> rowsByEmail = groupRowsByEmail(parsedRows);
            processGroupedRows(rowsByEmail, result, errors);

            result.setErrors(errors);
            result.setSuccess(errors.isEmpty());

            log.info("CSV import completed. Users: {}, Assets: {}, Errors: {}",
                    result.getUsersCreated(), result.getAssetsCreated(), errors.size());

        } catch (Exception e) {
            log.error("Fatal error during CSV import", e);
            throw new RuntimeException("Erro ao processar arquivo CSV: " + e.getMessage(), e);
        }

        return result;
    }

    private Map<String, List<CsvImportRow>> groupRowsByEmail(List<CsvImportRow> parsedRows) {
        Map<String, List<CsvImportRow>> rowsByEmail = new LinkedHashMap<>();
        for (CsvImportRow row : parsedRows) {
            rowsByEmail.computeIfAbsent(row.userEmail(), key -> new ArrayList<>()).add(row);
        }
        return rowsByEmail;
    }

    private void processGroupedRows(
            Map<String, List<CsvImportRow>> rowsByEmail,
            ImportResultDTO result,
            List<String> errors
    ) {
        for (Map.Entry<String, List<CsvImportRow>> entry : rowsByEmail.entrySet()) {
            String userEmail = entry.getKey();
            List<CsvImportRow> userRows = entry.getValue();

            Optional<CsvImportRow> referenceRow = userRows.stream().findFirst();
            if (referenceRow.isEmpty()) continue;

            try {
                User user = csvPersistenceService.findOrCreateUser(referenceRow.get(), userRows, result);

                for (CsvImportRow row : userRows) {
                    try {
                        csvPersistenceService.processAssetRow(row, user, result);
                    } catch (Exception e) {
                        String assetError = String.format(
                                "Erro na linha %d (ativo do usuário %s): %s",
                                row.lineNumber(), userEmail, e.getMessage());
                        log.error(assetError, e);
                        errors.add(assetError);
                    }
                }
            } catch (Exception e) {
                String userError = String.format(
                        "Erro ao processar usuário %s (linha %d): %s",
                        userEmail, referenceRow.get().lineNumber(), e.getMessage());
                log.error(userError, e);
                errors.add(userError);
            }
        }
    }
}

