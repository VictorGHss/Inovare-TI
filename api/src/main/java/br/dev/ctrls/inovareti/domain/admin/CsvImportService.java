package br.dev.ctrls.inovareti.domain.admin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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
 * Service responsible for bulk CSV import functionality.
 * Processes CSV file to create users, sectors, asset categories and assets with proper associations.
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
     * Imports data from CSV file.
     * CSV format: UserName,UserEmail,UserRole,SectorName,AssetName,AssetCategory,PatrimonyCode,AssetSpecs
     * 
     * @param file CSV file uploaded
     * @return ImportResultDTO with statistics
     */
    @Transactional
    public ImportResultDTO importCsv(MultipartFile file) {
        log.info("Starting CSV import from file: {}", file.getOriginalFilename());
        
        ImportResultDTO result = new ImportResultDTO();
        List<String> errors = new ArrayList<>();
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String line;
            int lineNumber = 0;
            
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                
                // Skip header line
                if (lineNumber == 1) {
                    continue;
                }
                
                // Skip empty lines
                if (line.trim().isEmpty()) {
                    continue;
                }
                
                try {
                    processLine(line, result);
                } catch (Exception e) {
                    String errorMsg = String.format("Erro na linha %d: %s", lineNumber, e.getMessage());
                    log.error(errorMsg, e);
                    errors.add(errorMsg);
                }
            }
            
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

    private void processLine(String line, ImportResultDTO result) {
        // Parse CSV line (handle commas inside quotes if needed)
        String[] columns = line.split(",", -1);
        
        if (columns.length < 4) {
            throw new IllegalArgumentException("Linha com formato inválido. Mínimo 4 colunas esperadas.");
        }
        
        String userName = columns[0].trim();
        String userEmail = columns[1].trim();
        String userRoleStr = columns.length > 2 ? columns[2].trim() : "";
        String sectorName = columns.length > 3 ? columns[3].trim() : "";
        String assetName = columns.length > 4 ? columns[4].trim() : "";
        String assetCategoryName = columns.length > 5 ? columns[5].trim() : "";
        String patrimonyCode = columns.length > 6 ? columns[6].trim() : "";
        String assetSpecs = columns.length > 7 ? columns[7].trim() : "";
        
        // Validate required fields
        if (userName.isEmpty() || userEmail.isEmpty() || sectorName.isEmpty()) {
            throw new IllegalArgumentException("UserName, UserEmail e SectorName são obrigatórios");
        }
        
        // 1. Find or create Sector
        Sector sector = sectorRepository.findByName(sectorName)
                .orElseGet(() -> {
                    Sector newSector = Sector.builder()
                            .name(sectorName)
                            .build();
                    Sector saved = sectorRepository.save(newSector);
                    result.incrementSectorsCreated();
                    log.debug("Created new sector: {}", sectorName);
                    return saved;
                });
        
        // 2. Find or create User
        User user = userRepository.findByEmail(userEmail)
                .orElseGet(() -> {
                    UserRole role = parseUserRole(userRoleStr);
                    User newUser = User.builder()
                            .name(userName)
                            .email(userEmail)
                            .passwordHash(passwordEncoder.encode(DEFAULT_PASSWORD))
                            .role(role)
                            .sector(sector)
                            .location(DEFAULT_LOCATION)
                            .build();
                    User saved = userRepository.save(newUser);
                    result.incrementUsersCreated();
                    log.debug("Created new user: {}", userEmail);
                    return saved;
                });
        
        // 3. Process Asset if AssetName is provided
        if (!assetName.isEmpty() && !patrimonyCode.isEmpty()) {
            processAsset(assetName, assetCategoryName, patrimonyCode, assetSpecs, user, result);
        }
    }

    private void processAsset(String assetName, String assetCategoryName, String patrimonyCode, 
                               String assetSpecs, User user, ImportResultDTO result) {
        // Check if asset already exists
        if (assetRepository.existsByPatrimonyCode(patrimonyCode)) {
            log.debug("Asset with patrimony code {} already exists, skipping", patrimonyCode);
            return;
        }
        
        // Find or create AssetCategory
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
        
        // Create Asset
        Asset asset = Asset.builder()
                .userId(user.getId())
                .name(assetName)
                .patrimonyCode(patrimonyCode)
                .category(category)
                .specifications(assetSpecs.isEmpty() ? null : assetSpecs)
                .build();
        Asset savedAsset = assetRepository.save(asset);
        result.incrementAssetsCreated();
        log.debug("Created new asset: {} ({})", assetName, patrimonyCode);
        
        // Create audit record (AssetMaintenance)
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
}
