package br.dev.ctrls.inovareti.modules.admin.application.service;

import br.dev.ctrls.inovareti.modules.admin.application.dto.ImportResultDTO;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import br.dev.ctrls.inovareti.modules.user.domain.model.User;
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

    /**
     * Importa usuários e ativos a partir de um arquivo CSV.
     *
     * O método lê o arquivo linha a linha (pulando o cabeçalho), delega o parsing
     * para {@link CsvRowParser} e coordena a persistência via
     * {@link CsvPersistenceService}. Em caso de erros por linha, eles são
     * acumulados no {@link ImportResultDTO} e a importação continua para as
     * demais linhas.
     *
     * Esta operação é transacional; a persistência parcial é controlada pelo
     * {@link CsvPersistenceService} e pelo objeto {@code ImportResultDTO}.
     *
     * @param file arquivo CSV enviado pelo cliente
     * @return {@link ImportResultDTO} contendo contadores e lista de erros
     */
        log.info("Starting CSV import for file: {}", file.getOriginalFilename());

        ImportResultDTO result = new ImportResultDTO();
        List<String> errors = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(file.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
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
                    String errorMsg = String.format("Error on line %d: %s", lineNumber, e.getMessage());
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
            throw new RuntimeException("Error processing CSV file: " + e.getMessage(), e);
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
                                "Error on line %d (user asset %s): %s",
                                row.lineNumber(), userEmail, e.getMessage());
                        log.error(assetError, e);
                        errors.add(assetError);
                    }
                }
            } catch (Exception e) {
                String userError = String.format(
                    "Error processing user %s (line %d): %s",
                    userEmail, referenceRow.get().lineNumber(), e.getMessage());
                log.error(userError, e);
                errors.add(userError);
            }
        }
    }
}

