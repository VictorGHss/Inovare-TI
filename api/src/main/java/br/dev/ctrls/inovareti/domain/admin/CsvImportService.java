package br.dev.ctrls.inovareti.domain.admin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import br.dev.ctrls.inovareti.domain.asset.Asset;
import br.dev.ctrls.inovareti.domain.asset.AssetCategory;
import br.dev.ctrls.inovareti.domain.asset.AssetCategoryRepository;
import br.dev.ctrls.inovareti.domain.asset.AssetMaintenance;
import br.dev.ctrls.inovareti.domain.asset.AssetMaintenanceRepository;
import br.dev.ctrls.inovareti.domain.asset.AssetRepository;
import br.dev.ctrls.inovareti.domain.user.Sector;
import br.dev.ctrls.inovareti.domain.user.SectorRepository;
import br.dev.ctrls.inovareti.domain.user.User;
import br.dev.ctrls.inovareti.domain.user.UserRepository;
import br.dev.ctrls.inovareti.domain.user.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço responsável pela funcionalidade de importação em massa via CSV.
 * Processa o arquivo CSV para criar usuários, setores, categorias de equipamentos e equipamentos com as devidas associações.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CsvImportService {

    private final UserRepository userRepository;
    private final SectorRepository sectorRepository;
    private final AssetRepository assetRepository;
    private final AssetCategoryRepository assetCategoryRepository;
    private final AssetMaintenanceRepository assetMaintenanceRepository;
    private final PasswordEncoder passwordEncoder;

    private static final String DEFAULT_PASSWORD = "Mudar@123";
    private static final String DEFAULT_LOCATION = "Matriz";

    /**
     * Importa dados de um arquivo CSV.
     * Formato CSV: UserName;UserEmail;UserRole;SectorName;AssetName;AssetCategory;PatrimonyCode;AssetSpecs
     * (Separador: ponto-e-vírgula para compatibilidade com Excel em PT-BR)
     *
     * @param file arquivo CSV enviado
     * @return ImportResultDTO com as estatísticas da importação
     */
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
                
                // Pula a linha de cabeçalho
                if (lineNumber == 1) {
                    continue;
                }
                
                // Pula linhas vazias
                if (line.trim().isEmpty()) {
                    continue;
                }
                
                try {
                    parsedRows.add(parseLine(line, lineNumber));
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

    private CsvImportRow parseLine(String line, int lineNumber) {
        // Faz o parse da linha CSV usando ponto-e-vírgula como separador (formato Excel PT-BR)
        String[] columns = line.split(";", -1);
        
        if (columns.length < 4) {
            throw new IllegalArgumentException("Formato de linha inválido. São esperadas no mínimo 4 colunas.");
        }
        
        // Extrai e normaliza todos os campos de forma robusta
        String userName = extractAndTrim(columns, 0);
        String userEmail = extractAndTrim(columns, 1);
        String userRoleStr = extractAndTrim(columns, 2);
        String sectorName = extractAndTrim(columns, 3);
        String assetName = extractAndTrim(columns, 4);
        String assetCategoryName = extractAndTrim(columns, 5);
        String patrimonyCode = extractAndTrim(columns, 6);
        String assetSpecs = extractAndTrim(columns, 7);
        
        // Para agrupamento seguro, o único campo obrigatório em toda linha é o e-mail.
        if (userEmail.isEmpty()) {
            throw new IllegalArgumentException("UserEmail é obrigatório");
        }

        return new CsvImportRow(
                lineNumber,
                userName,
                userEmail,
                userRoleStr,
                sectorName,
                assetName,
                assetCategoryName,
                patrimonyCode,
                assetSpecs
        );
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
            if (referenceRow.isEmpty()) {
                continue;
            }

            try {
                User user = findOrCreateUser(referenceRow.get(), userRows, result);

                for (CsvImportRow row : userRows) {
                    try {
                        processAssetRow(row, user, result);
                    } catch (Exception e) {
                        String assetError = String.format(
                                "Erro na linha %d (ativo do usuário %s): %s",
                                row.lineNumber(),
                                userEmail,
                                e.getMessage()
                        );
                        log.error(assetError, e);
                        errors.add(assetError);
                    }
                }
            } catch (Exception e) {
                String userError = String.format(
                        "Erro ao processar usuário %s (linha %d): %s",
                        userEmail,
                        referenceRow.get().lineNumber(),
                        e.getMessage()
                );
                log.error(userError, e);
                errors.add(userError);
            }
        }
    }

    private User findOrCreateUser(CsvImportRow referenceRow, List<CsvImportRow> userRows, ImportResultDTO result) {
        User existingUser = userRepository.findByEmail(referenceRow.userEmail()).orElse(null);
        if (existingUser != null) {
            return existingUser;
        }

        String userName = firstNonBlank(userRows, CsvImportRow::userName)
                .orElseThrow(() -> new IllegalArgumentException("UserName é obrigatório para criar novo usuário"));
        String sectorName = firstNonBlank(userRows, CsvImportRow::sectorName)
                .orElseThrow(() -> new IllegalArgumentException("SectorName é obrigatório para criar novo usuário"));
        String userRoleStr = firstNonBlank(userRows, CsvImportRow::userRoleStr).orElse("USER");

        // Busca ou cria setor apenas quando realmente precisarmos criar usuário.
        Sector sector = sectorRepository.findByName(sectorName)
                .orElseGet(() -> {
                    Sector newSector = Sector.builder()
                            .name(sectorName)
                            .build();
                    Sector savedSector = sectorRepository.save(newSector);
                    result.incrementSectorsCreated();
                    log.debug("Created new sector: {}", sectorName);
                    return savedSector;
                });

        UserRole role = parseUserRole(userRoleStr);

        User newUser = User.builder()
                .name(userName)
                .email(referenceRow.userEmail())
                .passwordHash(passwordEncoder.encode(DEFAULT_PASSWORD))
                .mustChangePassword(true)
                .role(role)
                .sector(sector)
                .location(DEFAULT_LOCATION)
                .build();

        try {
            User savedUser = userRepository.save(newUser);
            result.incrementUsersCreated();
            log.debug("Created new user: {}", referenceRow.userEmail());
            return savedUser;
        } catch (DataIntegrityViolationException ex) {
            // Blindagem para concorrência: se outro processo criou o usuário ao mesmo tempo,
            // reutilizamos o registro recém-criado.
            return userRepository.findByEmail(referenceRow.userEmail())
                    .orElseThrow(() -> ex);
        }
    }

    private void processAssetRow(CsvImportRow row, User user, ImportResultDTO result) {
        if (row.assetName().isEmpty() || row.patrimonyCode().isEmpty()) {
            return;
        }

        processAsset(
                row.assetName(),
                row.assetCategoryName(),
                row.patrimonyCode(),
                row.assetSpecs(),
                user,
                result
        );
    }
        
    private void processAsset(String assetName, String assetCategoryName, String patrimonyCode, 
                               String assetSpecs, User user, ImportResultDTO result) {
        // Verifica se o equipamento já existe pelo código de patrimônio
        if (assetRepository.existsByPatrimonyCode(patrimonyCode)) {
            log.debug("Asset with patrimony code {} already exists, skipping", patrimonyCode);
            return;
        }
        
        // Busca ou cria a Categoria do Equipamento
        AssetCategory category = null;
        if (!assetCategoryName.isEmpty()) {
            category = assetCategoryRepository.findByName(assetCategoryName)
                    .orElseGet(() -> {
                        AssetCategory newCategory = AssetCategory.builder()
                                .name(assetCategoryName)
                                .build();
                        AssetCategory saved = assetCategoryRepository.save(newCategory);
                        result.incrementCategoriesCreated();
                        log.debug("Created new asset category: {}", assetCategoryName);
                        return saved;
                    });
        }
        
        // Cria o Equipamento
        Asset asset = Asset.builder()
                .userId(user.getId())
                .name(assetName)
                .patrimonyCode(patrimonyCode)
                .category(category)
                .specifications(assetSpecs.isEmpty() ? null : assetSpecs)
                .build();
        Asset savedAsset;
        try {
            savedAsset = assetRepository.save(asset);
        } catch (DataIntegrityViolationException ex) {
            // Blindagem para colisões de patrimônio durante processamento concorrente.
            log.warn("Asset with patrimony code {} was created concurrently. Skipping.", patrimonyCode);
            return;
        }
        result.incrementAssetsCreated();
        log.debug("Created new asset: {} ({})", assetName, patrimonyCode);
        
        // Cria registro de auditoria (AssetMaintenance)
        AssetMaintenance maintenance = AssetMaintenance.builder()
                .asset(savedAsset)
                .maintenanceDate(LocalDate.now())
                .type(AssetMaintenance.MaintenanceType.TRANSFER)
                .description("Carga inicial de dados via importação CSV. Atribuído a " + user.getName())
                .cost(BigDecimal.ZERO)
                .technician(user)
                .build();
        assetMaintenanceRepository.save(maintenance);
        log.debug("Created audit record for asset: {}", patrimonyCode);
    }

    private UserRole parseUserRole(String roleStr) {
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
     * Extrai um valor do array de colunas CSV e aplica normalização de espaços.
     * Método robusto contra problemas de whitespace.
     *
     * @param columns o array de colunas obtido após o split do CSV
     * @param index o índice da coluna a ser extraída
     * @return o valor normalizado, ou string vazia se o índice estiver fora dos limites
     */
    private String extractAndTrim(String[] columns, int index) {
        // Trata índice fora dos limites com segurança
        if (index < 0 || index >= columns.length) {
            return "";
        }
        
        String value = columns[index];
        
        // Trata valor nulo
        if (value == null) {
            return "";
        }
        
        // Aplica trim() para remover espaços no início e no fim
        // Usa strip() também para tratar caracteres de whitespace Unicode
        return value.trim().strip();
    }

    private Optional<String> firstNonBlank(List<CsvImportRow> rows, java.util.function.Function<CsvImportRow, String> extractor) {
        return rows.stream()
                .map(extractor)
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }

    private record CsvImportRow(
            int lineNumber,
            String userName,
            String userEmail,
            String userRoleStr,
            String sectorName,
            String assetName,
            String assetCategoryName,
            String patrimonyCode,
            String assetSpecs
    ) {
    }
}
